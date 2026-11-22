package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.core.common.Money
import com.tradinginaja.domain.model.MarketQuote
import com.tradinginaja.domain.model.OrderPreview
import com.tradinginaja.domain.model.OrderRequest
import com.tradinginaja.domain.model.OrderSide
import com.tradinginaja.domain.model.OrderType
import com.tradinginaja.domain.model.PortfolioState
import com.tradinginaja.domain.model.Position
import kotlin.math.abs

internal object OrderPreviewCalculator {

    private const val PERCENT_MULTIPLIER = 100.0

    fun preview(
        portfolio: PortfolioState,
        request: OrderRequest,
        quote: MarketQuote,
        quotesBySymbol: Map<String, MarketQuote>
    ): OrderPreview {
        val safeQty = request.quantity.coerceAtLeast(0L)
        val safeLeverage = request.leverage.coerceAtLeast(1)
        val existingPos = portfolio.positions[request.symbol]
        val isClosing = OrderValidation.isClosingOrder(existingPos, request.side)
        val isShortOpening = !isClosing && (request.side == OrderSide.SELL || existingPos?.isShort == true)

        val refPrice = if (request.side == OrderSide.BUY) quote.askPrice else quote.bidPrice
        val rejectReason = OrderFormulas.validateRequest(portfolio, request, quote)
        val estSlippageBps = previewSlippageBps(request.type, safeQty, quote)
        val estExecPrice = estimateExecutionPrice(request, refPrice, estSlippageBps)
        val estSlippageCost = if (request.type == OrderType.MARKET) {
            (estExecPrice - refPrice).abs() * safeQty
        } else {
            Money.ZERO
        }
        val estNotional = estExecPrice * safeQty
        val estCommission = OrderFormulas.calculateCommission(estNotional)
        val requiredMargin = if (isClosing) {
            Money.ZERO
        } else {
            RiskEngine.calculateRequiredMargin(estNotional, safeLeverage)
        }
        val dailyBorrowEstimate = if (isClosing) {
            Money.ZERO
        } else {
            RiskEngine.calculateDailyBorrowFeeEstimate(estNotional, safeLeverage, isShortOpening)
        }
        val estRealizedPnl = estimateCloseRealizedPnl(portfolio, request, estNotional, estCommission)
        val netImpact = if (isClosing) {
            estimateReleasedMargin(existingPos, safeQty) + (estRealizedPnl ?: -estCommission)
        } else {
            -(requiredMargin + estCommission)
        }
        val concentrationPct = computeProjectedConcentrationPct(
            portfolio = portfolio,
            request = request,
            quote = quote,
            quotesBySymbol = quotesBySymbol,
            estCommission = estCommission
        )
        val isOpeningLeveraged = !isClosing && safeQty > 0L && (isShortOpening || safeLeverage > 1)
        val estLiqPrice = previewLiquidationPrice(
            isOpeningLeveraged = isOpeningLeveraged,
            portfolio = portfolio,
            quotesBySymbol = quotesBySymbol,
            estExecPrice = estExecPrice,
            safeQty = safeQty,
            isShortOpening = isShortOpening,
            safeLeverage = safeLeverage,
            estCommission = estCommission,
            requiredMargin = requiredMargin
        )

        return OrderPreview(
            symbol = request.symbol,
            side = request.side,
            type = request.type,
            quantity = safeQty,
            leverage = safeLeverage,
            isShortOpening = isShortOpening,
            referencePrice = refPrice,
            estimatedExecutionPrice = estExecPrice,
            spreadBps = quote.spreadBps,
            estimatedSlippageBps = estSlippageBps,
            estimatedSlippageCost = estSlippageCost,
            estimatedNotional = estNotional,
            requiredMargin = requiredMargin,
            estimatedBorrowFeePerDay = dailyBorrowEstimate,
            estimatedLiquidationPrice = estLiqPrice,
            estimatedCommission = estCommission,
            estimatedNetCashImpact = netImpact,
            projectedCashAfterOrder = portfolio.availableCash + netImpact,
            projectedAssetConcentrationPct = concentrationPct,
            isHighConcentrationWarning = concentrationPct > GameBalance.POSITION_CONCENTRATION_WARNING_PCT,
            isHighSlippageWarning = estSlippageBps >= GameBalance.HIGH_SLIPPAGE_WARNING_BPS,
            isMarginLiquidationRiskWarning = isOpeningLeveraged,
            estimatedRealizedPnl = estRealizedPnl,
            rejectReason = rejectReason
        )
    }

    private fun previewSlippageBps(type: OrderType, safeQty: Long, quote: MarketQuote): Long =
        if (safeQty > 0L && (type == OrderType.MARKET || type == OrderType.STOP_MARKET)) {
            OrderFormulas.calculateSlippageBps(safeQty, quote)
        } else {
            0L
        }

    private fun previewLiquidationPrice(
        isOpeningLeveraged: Boolean,
        portfolio: PortfolioState,
        quotesBySymbol: Map<String, MarketQuote>,
        estExecPrice: Money,
        safeQty: Long,
        isShortOpening: Boolean,
        safeLeverage: Int,
        estCommission: Money,
        requiredMargin: Money
    ): Money? {
        if (!isOpeningLeveraged) return null
        val health = RiskEngine.evaluateMarginHealth(portfolio, quotesBySymbol)
        return RiskEngine.estimateLiquidationPrice(
            entryPrice = estExecPrice,
            quantity = safeQty,
            isShort = isShortOpening,
            leverage = safeLeverage,
            accountEquityAfterFee = health.accountEquity - estCommission,
            newTotalLeveragedMargin = health.usedLeveragedMargin + requiredMargin
        )
    }

    private fun estimateExecutionPrice(request: OrderRequest, refPrice: Money, slippageBps: Long): Money =
        when (request.type) {
            OrderType.MARKET -> OrderFormulas.applySlippage(refPrice, request.side, slippageBps)
            OrderType.LIMIT -> request.limitPrice ?: refPrice
            OrderType.STOP_MARKET ->
                OrderFormulas.applySlippage(request.triggerPrice ?: refPrice, request.side, slippageBps)
            OrderType.STOP_LIMIT -> request.limitPrice ?: request.triggerPrice ?: refPrice
        }

    private fun estimateReleasedMargin(position: Position?, closeQty: Long): Money {
        if (position == null || closeQty <= 0L) return Money.ZERO
        val clamped = closeQty.coerceAtMost(position.absoluteQuantity)
        return if (clamped == position.absoluteQuantity) {
            position.marginLocked
        } else {
            position.marginLocked.allocate(listOf(clamped, position.absoluteQuantity - clamped)).first()
        }
    }

    private fun computeProjectedConcentrationPct(
        portfolio: PortfolioState,
        request: OrderRequest,
        quote: MarketQuote,
        quotesBySymbol: Map<String, MarketQuote>,
        estCommission: Money
    ): Double {
        if (request.quantity <= 0L) return 0.0
        val currentSigned = portfolio.positions[request.symbol]?.signedQuantity ?: 0L
        val delta = if (request.side == OrderSide.BUY) request.quantity else -request.quantity
        val projectedAssetNotional = quote.midPrice * abs(currentSigned + delta)

        var otherEquityContribution = Money.ZERO
        for ((sym, pos) in portfolio.positions) {
            if (sym != request.symbol) {
                val otherMid = quotesBySymbol[sym]?.midPrice ?: pos.averageEntryPrice
                otherEquityContribution += pos.netEquityContribution(otherMid)
            }
        }
        val currentPosContribution =
            portfolio.positions[request.symbol]?.netEquityContribution(quote.midPrice) ?: Money.ZERO
        val projectedEquity = (
            portfolio.cashBalance + currentPosContribution + otherEquityContribution - estCommission
            ).minorUnits
        return if (projectedEquity > 0L) {
            (projectedAssetNotional.minorUnits.toDouble() / projectedEquity.toDouble()) * PERCENT_MULTIPLIER
        } else {
            0.0
        }
    }

    private fun estimateCloseRealizedPnl(
        portfolio: PortfolioState,
        request: OrderRequest,
        estNotional: Money,
        estCommission: Money
    ): Money? {
        val position = portfolio.positions[request.symbol]
        val isClosing = OrderValidation.isClosingOrder(position, request.side)
        val validQty = position != null && request.quantity in 1L..position.absoluteQuantity
        return if (!isClosing || !validQty) {
            null
        } else {
            val safePos = requireNotNull(position)
            val closedCost = if (request.quantity == safePos.absoluteQuantity) {
                safePos.costBasis
            } else {
                safePos.costBasis.allocate(
                    listOf(request.quantity, safePos.absoluteQuantity - request.quantity)
                ).first()
            }
            val grossPnl = if (safePos.isLong) estNotional - closedCost else closedCost - estNotional
            grossPnl - estCommission
        }
    }
}
