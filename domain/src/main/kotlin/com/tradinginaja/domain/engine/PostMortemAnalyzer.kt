package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.domain.model.Order
import com.tradinginaja.domain.model.OrderSide
import com.tradinginaja.domain.model.OrderStatus
import com.tradinginaja.domain.model.OrderType
import com.tradinginaja.domain.model.PortfolioState
import com.tradinginaja.domain.model.PostMortemCause
import com.tradinginaja.domain.model.PostMortemSeverity
import com.tradinginaja.domain.model.PostMortemSummary
import com.tradinginaja.domain.model.TradeFill
import com.tradinginaja.domain.model.TradePostMortem
import kotlin.math.abs
import kotlin.math.sign

/**
 * Mesin analisis post-mortem otomatis pure Kotlin untuk setiap trade rugi.
 * Mengevaluasi proteksi stop-loss, leverage, ukuran risiko (>2%), konsentrasi, dan slippage.
 */
object PostMortemAnalyzer {

    private const val PERCENT_MULTIPLIER = 100.0

    fun analyzePortfolio(portfolio: PortfolioState): List<TradePostMortem> {
        if (portfolio.fills.isEmpty()) return emptyList()
        val ordersById = portfolio.orders.associateBy { it.orderId }
        val sortedFills = portfolio.fills.sortedWith(
            compareBy<TradeFill> { it.tickIndex }.thenBy { it.executedAtEpochMillis }
        )
        val tracker = PositionTrackingState()
        val results = mutableListOf<TradePostMortem>()

        for (fill in sortedFills) {
            processFill(
                fill = fill,
                portfolio = portfolio,
                ordersById = ordersById,
                tracker = tracker,
                results = results
            )
        }

        return results.sortedWith(
            compareByDescending<TradePostMortem> { it.tickIndex }.thenByDescending { it.lossAmount.minorUnits }
        )
    }

    private fun processFill(
        fill: TradeFill,
        portfolio: PortfolioState,
        ordersById: Map<String, Order>,
        tracker: PositionTrackingState,
        results: MutableList<TradePostMortem>
    ) {
        val prevQty = tracker.runningQty[fill.symbol] ?: 0L
        val delta = if (fill.side == OrderSide.BUY) fill.quantity else -fill.quantity
        val nextQty = prevQty + delta
        val order = ordersById[fill.orderId]
        val orderLev = (order?.leverage ?: 1).coerceAtLeast(1)

        if (isClosingFill(prevQty, fill.side)) {
            val positionLeverage = maxOf(tracker.activeMaxLeverage[fill.symbol] ?: 1, orderLev)
            if (fill.realizedPnl.isNegative) {
                results.add(buildPostMortem(fill, portfolio, order, positionLeverage))
            }
            if (didPositionCloseOrFlip(prevQty, nextQty)) {
                tracker.activeMaxLeverage.remove(fill.symbol)
            }
        } else {
            tracker.activeMaxLeverage[fill.symbol] = maxOf(tracker.activeMaxLeverage[fill.symbol] ?: 1, orderLev)
        }
        tracker.runningQty[fill.symbol] = nextQty
    }

    private fun isClosingFill(prevQty: Long, side: OrderSide): Boolean =
        (prevQty > 0L && side == OrderSide.SELL) || (prevQty < 0L && side == OrderSide.BUY)

    private fun didPositionCloseOrFlip(prevQty: Long, nextQty: Long): Boolean =
        nextQty == 0L || prevQty.sign != nextQty.sign

    private class PositionTrackingState(
        val runningQty: MutableMap<String, Long> = mutableMapOf(),
        val activeMaxLeverage: MutableMap<String, Int> = mutableMapOf()
    )

    fun buildSummary(postMortems: List<TradePostMortem>): PostMortemSummary = PostMortemSummary(
        totalLosingTrades = postMortems.size,
        unhedgedLossCount = postMortems.count { !it.hadStopLossProtection },
        overLeveragedLossCount = postMortems.count { it.leverageUsed > 1 },
        disciplinedStopLossCount = postMortems.count { it.primaryCause == PostMortemCause.CONTROLLED_STOP_EXIT },
        latestPostMortem = postMortems.firstOrNull()
    )

    private fun buildPostMortem(
        fill: TradeFill,
        portfolio: PortfolioState,
        closingOrder: Order?,
        positionLeverage: Int
    ): TradePostMortem {
        val lossAmount = fill.realizedPnl.abs()
        val initialMinor = portfolio.initialCash.minorUnits.coerceAtLeast(1L)
        val lossPct = (lossAmount.minorUnits.toDouble() / initialMinor.toDouble()) * PERCENT_MULTIPLIER
        val concentrationPct = (fill.grossNotional.minorUnits.toDouble() / initialMinor.toDouble()) * PERCENT_MULTIPLIER
        val hadStopProtection = hasStopLossProtectionForFill(fill, closingOrder, portfolio.orders)

        val causes = collectCauses(
            fill = fill,
            positionLeverage = positionLeverage,
            hadStopProtection = hadStopProtection,
            lossPct = lossPct,
            concentrationPct = concentrationPct
        )
        val primary = causes.first()
        val severity = when (primary) {
            PostMortemCause.FORCED_LIQUIDATION,
            PostMortemCause.OVER_LEVERAGE -> PostMortemSeverity.CRITICAL
            PostMortemCause.CONTROLLED_STOP_EXIT -> PostMortemSeverity.CONSTRUCTIVE
            else -> PostMortemSeverity.WARNING
        }

        return TradePostMortem(
            fillId = fill.fillId,
            symbol = fill.symbol,
            tickIndex = fill.tickIndex,
            lossAmount = lossAmount,
            lossPctOfInitialCapital = abs(lossPct),
            leverageUsed = positionLeverage,
            slippageBps = fill.slippageBps,
            hadStopLossProtection = hadStopProtection,
            primaryCause = primary,
            contributingCauses = causes,
            severity = severity
        )
    }

    private fun collectCauses(
        fill: TradeFill,
        positionLeverage: Int,
        hadStopProtection: Boolean,
        lossPct: Double,
        concentrationPct: Double
    ): List<PostMortemCause> {
        val causes = mutableListOf<PostMortemCause>()
        if (fill.isLiquidation) {
            causes.add(PostMortemCause.FORCED_LIQUIDATION)
        }
        if (positionLeverage > 1) {
            causes.add(PostMortemCause.OVER_LEVERAGE)
        }
        if (!hadStopProtection && !fill.isLiquidation) {
            causes.add(PostMortemCause.NO_STOP_LOSS)
        }
        if (lossPct > GameBalance.MAX_DISCIPLINED_RISK_PER_TRADE_PCT) {
            causes.add(PostMortemCause.OVERSIZED_POSITION_RISK)
        }
        if (concentrationPct > GameBalance.POSITION_CONCENTRATION_WARNING_PCT) {
            causes.add(PostMortemCause.HIGH_CONCENTRATION)
        }
        if (fill.slippageBps >= GameBalance.HIGH_SLIPPAGE_WARNING_BPS) {
            causes.add(PostMortemCause.HIGH_SLIPPAGE)
        }
        if (causes.isEmpty()) {
            causes.add(PostMortemCause.CONTROLLED_STOP_EXIT)
        }
        return causes
    }

    private fun hasStopLossProtectionForFill(fill: TradeFill, closingOrder: Order?, allOrders: List<Order>): Boolean {
        val closedByStop = fill.orderType == OrderType.STOP_MARKET ||
            fill.orderType == OrderType.STOP_LIMIT ||
            closingOrder?.orderId?.endsWith("_sl") == true
        val hasRelatedStopOrder = allOrders.any { order ->
            order.symbol == fill.symbol &&
                order.status != OrderStatus.REJECTED &&
                order.createdAtTick <= fill.tickIndex &&
                (
                    order.stopLossPrice != null ||
                        order.type == OrderType.STOP_MARKET ||
                        order.type == OrderType.STOP_LIMIT
                    )
        }
        return closedByStop || hasRelatedStopOrder
    }
}
