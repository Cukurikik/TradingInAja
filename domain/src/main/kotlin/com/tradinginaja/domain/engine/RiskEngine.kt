package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.core.common.Money
import com.tradinginaja.domain.model.LiquidationEvent
import com.tradinginaja.domain.model.MarginHealthSnapshot
import com.tradinginaja.domain.model.MarginStatus
import com.tradinginaja.domain.model.MarketQuote
import com.tradinginaja.domain.model.OrderSide
import com.tradinginaja.domain.model.OrderStatus
import com.tradinginaja.domain.model.PortfolioRiskMetrics
import com.tradinginaja.domain.model.PortfolioState
import com.tradinginaja.domain.model.Position
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Mesin manajemen risiko pure Kotlin (tanpa import Android):
 * - Margin awal & leverage berbasis level karier
 * - Biaya pinjam per tick untuk posisi short & leveraged
 * - Evaluasi Margin Call (<= 110%) & Likuidasi Paksa (<= 100%)
 * - Metrik risiko portofolio (drawdown, win rate, risk/reward, exposure, Sharpe sederhana)
 */
object RiskEngine {

    private const val PERCENT_MULTIPLIER = 100.0
    private val TICKS_PER_YEAR_DECIMAL = BigDecimal.valueOf(GameBalance.TICKS_PER_TRADING_YEAR)
    private val ANNUAL_BORROW_RATE_DIVISOR =
        BigDecimal.valueOf(GameBalance.BASIS_POINTS_DIVISOR).multiply(TICKS_PER_YEAR_DECIMAL)

    fun calculateRequiredMargin(notional: Money, leverage: Int): Money {
        val safeLeverage = leverage.coerceAtLeast(1)
        if (notional <= Money.ZERO) return Money.ZERO
        val required = BigDecimal.valueOf(notional.minorUnits)
            .divide(BigDecimal.valueOf(safeLeverage.toLong()), 0, RoundingMode.UP)
            .longValueExact()
        return Money(required)
    }

    fun calculateDailyBorrowFeeEstimate(notional: Money, leverage: Int, isShort: Boolean): Money {
        val borrowedNotional = when {
            notional <= Money.ZERO -> Money.ZERO
            isShort -> notional
            else -> (notional - calculateRequiredMargin(notional, leverage)).coerceAtLeast(Money.ZERO)
        }
        return if (borrowedNotional <= Money.ZERO) {
            Money.ZERO
        } else {
            val dailyFactor = BigDecimal.valueOf(GameBalance.ANNUAL_BORROW_FEE_BPS)
                .multiply(BigDecimal.valueOf(GameBalance.TICKS_PER_TRADING_DAY.toLong()))
            val fee = BigDecimal.valueOf(borrowedNotional.minorUnits)
                .multiply(dailyFactor)
                .divide(ANNUAL_BORROW_RATE_DIVISOR, 0, RoundingMode.HALF_UP)
                .longValueExact()
            Money(fee)
        }
    }

    fun calculateTickBorrowFee(position: Position, markPrice: Money): Money {
        val liveNotional = position.marketValue(markPrice)
        val borrowedNotional = if (position.isShort) {
            liveNotional
        } else if (position.leverage > 1) {
            (liveNotional - position.marginLocked).coerceAtLeast(Money.ZERO)
        } else {
            Money.ZERO
        }
        if (borrowedNotional <= Money.ZERO) return Money.ZERO
        val feeUnits = BigDecimal.valueOf(borrowedNotional.minorUnits)
            .multiply(BigDecimal.valueOf(GameBalance.ANNUAL_BORROW_FEE_BPS.toLong()))
            .divide(ANNUAL_BORROW_RATE_DIVISOR, 0, RoundingMode.HALF_UP)
            .longValueExact()
        return Money(feeUnits)
    }

    fun applyTickBorrowFees(state: PortfolioState, quotesBySymbol: Map<String, MarketQuote>): PortfolioState {
        if (state.positions.isEmpty()) return state
        var nextCash = state.cashBalance
        var totalBorrowFees = state.totalBorrowFeesPaid
        var insolvency = state.insolvencyCoverage
        val updatedPositions = state.positions.toMutableMap()

        for ((symbol, pos) in state.positions) {
            val markPrice = quotesBySymbol[symbol]?.midPrice ?: pos.averageEntryPrice
            val tickFee = calculateTickBorrowFee(pos, markPrice)
            if (tickFee.isPositive) {
                val deduction = deductBorrowFee(nextCash, pos.marginLocked, tickFee)
                nextCash = deduction.nextCash
                insolvency += deduction.extraInsolvency
                updatedPositions[symbol] = pos.copy(
                    marginLocked = deduction.nextMargin,
                    accumulatedBorrowFee = pos.accumulatedBorrowFee + tickFee
                )
                totalBorrowFees += tickFee
            }
        }
        return state.copy(
            cashBalance = nextCash,
            positions = updatedPositions,
            totalBorrowFeesPaid = totalBorrowFees,
            insolvencyCoverage = insolvency
        )
    }

    private fun deductBorrowFee(cash: Money, margin: Money, fee: Money): BorrowDeduction {
        val cashPaid = minOf(cash, fee)
        val afterCash = cash - cashPaid
        val leftAfterCash = fee - cashPaid
        val marginPaid = minOf(margin, leftAfterCash)
        val afterMargin = margin - marginPaid
        val uncovered = leftAfterCash - marginPaid
        return BorrowDeduction(afterCash, afterMargin, uncovered)
    }

    fun evaluateMarginHealth(state: PortfolioState, quotesBySymbol: Map<String, MarketQuote>): MarginHealthSnapshot {
        val positionsContribution = OrderEngine.computePositionsNetEquity(state, quotesBySymbol)
        val accountEquity = state.cashBalance + positionsContribution
        val totalMarginLocked = state.positions.values.fold(Money.ZERO) { acc, pos -> acc + pos.marginLocked }
        val usedLeveragedMargin = state.positions.values
            .filter { it.isShort || it.leverage > 1 }
            .fold(Money.ZERO) { acc, pos -> acc + pos.marginLocked }

        val marginLevelPct = if (usedLeveragedMargin.isPositive) {
            (accountEquity.minorUnits.toDouble() / usedLeveragedMargin.minorUnits.toDouble()) * PERCENT_MULTIPLIER
        } else {
            null
        }
        val status = when {
            marginLevelPct == null -> MarginStatus.HEALTHY
            marginLevelPct <= GameBalance.LIQUIDATION_MARGIN_LEVEL_PCT -> MarginStatus.LIQUIDATION
            marginLevelPct <= GameBalance.MARGIN_CALL_LEVEL_PCT -> MarginStatus.MARGIN_CALL
            else -> MarginStatus.HEALTHY
        }
        return MarginHealthSnapshot(
            accountEquity = accountEquity,
            usedLeveragedMargin = usedLeveragedMargin,
            totalMarginLocked = totalMarginLocked,
            marginLevelPct = marginLevelPct,
            status = status
        )
    }

    fun estimateLiquidationPrice(
        entryPrice: Money,
        quantity: Long,
        isShort: Boolean,
        leverage: Int,
        accountEquityAfterFee: Money,
        newTotalLeveragedMargin: Money
    ): Money? {
        if (quantity <= 0L || (!isShort && leverage <= 1)) return null
        val buffer = (accountEquityAfterFee - newTotalLeveragedMargin).coerceAtLeast(Money.ZERO)
        val bufferPerUnit = buffer.minorUnits / quantity
        val rawPriceUnits = if (isShort) {
            entryPrice.minorUnits + bufferPerUnit
        } else {
            (entryPrice.minorUnits - bufferPerUnit).coerceAtLeast(GameBalance.MIN_ASSET_PRICE_MINOR)
        }
        return Money(rawPriceUnits)
    }

    fun liquidateIfNeeded(
        state: PortfolioState,
        quotesBySymbol: Map<String, MarketQuote>,
        tickIndex: Long,
        executedAtEpochMillis: Long
    ): PortfolioState {
        var current = state
        var health = evaluateMarginHealth(current, quotesBySymbol)
        if (health.status != MarginStatus.LIQUIDATION) return current

        // Batalkan order BUY pembuka tertunda untuk melepas cadangan kas sebelum melikuidasi posisi.
        val cleanedOrders = current.orders.map { order ->
            val holdingShort = current.positions[order.symbol]?.isShort == true
            val isClosingShort = order.side == OrderSide.BUY && holdingShort
            if (order.status == OrderStatus.PENDING && order.reservedCash.isPositive && !isClosingShort) {
                order.copy(status = OrderStatus.CANCELLED, reservedCash = Money.ZERO, updatedAtTick = tickIndex)
            } else {
                order
            }
        }
        current = current.copy(orders = cleanedOrders)
        health = evaluateMarginHealth(current, quotesBySymbol)

        var stepCounter = 0
        var keepLiquidating = health.status == MarginStatus.LIQUIDATION
        while (keepLiquidating) {
            val next = liquidateNextTarget(
                current = current,
                quotesBySymbol = quotesBySymbol,
                marginBefore = health.marginLevelPct ?: 0.0,
                stepCounter = stepCounter,
                tickIndex = tickIndex,
                executedAtEpochMillis = executedAtEpochMillis
            )
            if (next == null) {
                keepLiquidating = false
            } else {
                current = next
                health = evaluateMarginHealth(current, quotesBySymbol)
                keepLiquidating = health.status == MarginStatus.LIQUIDATION
                stepCounter++
            }
        }
        return current
    }

    private fun liquidateNextTarget(
        current: PortfolioState,
        quotesBySymbol: Map<String, MarketQuote>,
        marginBefore: Double,
        stepCounter: Int,
        tickIndex: Long,
        executedAtEpochMillis: Long
    ): PortfolioState? {
        val targetPos = selectLiquidationTarget(current, quotesBySymbol)
        val quote = targetPos?.let { quotesBySymbol[it.symbol] }
        if (targetPos == null || quote == null) return null

        val closeSide = if (targetPos.isShort) OrderSide.BUY else OrderSide.SELL
        val refPrice = if (closeSide == OrderSide.BUY) quote.askPrice else quote.bidPrice
        val slippageBps = (
            OrderFormulas.calculateSlippageBps(targetPos.absoluteQuantity, quote) +
                GameBalance.LIQUIDATION_EXTRA_SLIPPAGE_BPS
            ).coerceAtMost(GameBalance.MAX_SLIPPAGE_BPS)
        val execPrice = OrderFormulas.applySlippage(refPrice, closeSide, slippageBps)
        val liqOrderId = "liq_${tickIndex}_${targetPos.symbol}_$stepCounter"

        val (afterLiq, fill) = OrderAccounting.executeForcedLiquidation(
            state = current,
            position = targetPos,
            orderId = liqOrderId,
            closeSide = closeSide,
            executionPrice = execPrice,
            slippageBps = slippageBps,
            tickIndex = tickIndex,
            executedAtEpochMillis = executedAtEpochMillis
        )
        val liqEvent = LiquidationEvent(
            eventId = "${liqOrderId}_evt",
            symbol = targetPos.symbol,
            closedSide = closeSide,
            closedQuantity = targetPos.absoluteQuantity,
            liquidationPrice = execPrice,
            realizedPnl = fill.realizedPnl,
            marginLevelBeforePct = marginBefore,
            tickIndex = tickIndex,
            occurredAtEpochMillis = executedAtEpochMillis
        )
        return afterLiq.copy(liquidationEvents = afterLiq.liquidationEvents + liqEvent)
    }

    fun computePortfolioMetrics(state: PortfolioState, quotesBySymbol: Map<String, MarketQuote>): PortfolioRiskMetrics {
        val marginHealth = evaluateMarginHealth(state, quotesBySymbol)
        return RiskMetricsCalculator.computeMetrics(
            state = state,
            quotesBySymbol = quotesBySymbol,
            marginHealth = marginHealth
        ) { symbol ->
            val pos = state.positions[symbol]
            if (pos != null) {
                estimateLiquidationPrice(
                    entryPrice = pos.averageEntryPrice,
                    quantity = pos.absoluteQuantity,
                    isShort = pos.isShort,
                    leverage = pos.leverage,
                    accountEquityAfterFee = marginHealth.accountEquity,
                    newTotalLeveragedMargin = marginHealth.usedLeveragedMargin
                )
            } else {
                null
            }
        }
    }

    private fun selectLiquidationTarget(state: PortfolioState, quotesBySymbol: Map<String, MarketQuote>): Position? =
        state.positions.values
            .filter { it.isShort || it.leverage > 1 }
            .minByOrNull { pos ->
                val mark = quotesBySymbol[pos.symbol]?.midPrice ?: pos.averageEntryPrice
                pos.unrealizedPnl(mark).minorUnits
            }

    private data class BorrowDeduction(val nextCash: Money, val nextMargin: Money, val extraInsolvency: Money)
}
