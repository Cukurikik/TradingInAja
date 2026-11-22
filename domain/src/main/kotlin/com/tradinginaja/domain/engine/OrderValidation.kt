package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.core.common.Money
import com.tradinginaja.domain.model.MarketQuote
import com.tradinginaja.domain.model.OrderRejectReason
import com.tradinginaja.domain.model.OrderRequest
import com.tradinginaja.domain.model.OrderSide
import com.tradinginaja.domain.model.OrderType
import com.tradinginaja.domain.model.PortfolioState
import com.tradinginaja.domain.model.Position

internal object OrderValidation {

    fun validateRequest(portfolio: PortfolioState, request: OrderRequest, quote: MarketQuote): OrderRejectReason? =
        validateBasicFields(request) ?: validatePositionOrMarginOrder(portfolio, request, quote)

    fun isClosingOrder(position: Position?, side: OrderSide): Boolean = when {
        position == null -> false
        position.isLong && side == OrderSide.SELL -> true
        position.isShort && side == OrderSide.BUY -> true
        else -> false
    }

    private fun validateBasicFields(request: OrderRequest): OrderRejectReason? = when {
        request.quantity <= 0L -> OrderRejectReason.INVALID_QUANTITY
        request.leverage < 1 || request.leverage > GameBalance.MAX_LEVERAGE_FUND ->
            OrderRejectReason.INVALID_LEVERAGE
        request.type == OrderType.LIMIT && !isValidMoneyPrice(request.limitPrice) ->
            OrderRejectReason.INVALID_LIMIT_PRICE
        request.type == OrderType.STOP_MARKET && !isValidMoneyPrice(request.triggerPrice) ->
            OrderRejectReason.INVALID_TRIGGER_PRICE
        request.type == OrderType.STOP_LIMIT && !isValidMoneyPrice(request.triggerPrice) ->
            OrderRejectReason.INVALID_TRIGGER_PRICE
        request.type == OrderType.STOP_LIMIT && !isValidMoneyPrice(request.limitPrice) ->
            OrderRejectReason.INVALID_LIMIT_PRICE
        else -> null
    }

    private fun validatePositionOrMarginOrder(
        portfolio: PortfolioState,
        request: OrderRequest,
        quote: MarketQuote
    ): OrderRejectReason? {
        val existingPos = portfolio.positions[request.symbol]
        return if (isClosingOrder(existingPos, request.side)) {
            validateClosingOrder(portfolio, requireNotNull(existingPos), request)
        } else {
            validateOpeningOrAddingOrder(portfolio, existingPos, request, quote)
        }
    }

    private fun validateClosingOrder(
        portfolio: PortfolioState,
        position: Position,
        request: OrderRequest
    ): OrderRejectReason? {
        val reservedClose = portfolio.reservedClosingQuantity(request.symbol, request.side)
        val closable = position.absoluteQuantity - reservedClose
        val hasBracket = request.takeProfitPrice != null || request.stopLossPrice != null
        return when {
            request.quantity > closable -> OrderRejectReason.INSUFFICIENT_POSITION
            hasBracket -> OrderRejectReason.INVALID_OCO_BRACKET
            else -> null
        }
    }

    private fun validateOpeningOrAddingOrder(
        portfolio: PortfolioState,
        existingPos: Position?,
        request: OrderRequest,
        quote: MarketQuote
    ): OrderRejectReason? {
        val isShortSide = request.side == OrderSide.SELL || existingPos?.isShort == true
        val careerLevel = portfolio.careerLevel
        val permissionError = when {
            isShortSide && !careerLevel.allowsShortSelling ->
                if (request.leverage > 1) {
                    OrderRejectReason.LEVERAGE_EXCEEDS_CAREER_LIMIT
                } else {
                    OrderRejectReason.SHORT_NOT_ALLOWED_FOR_LEVEL
                }
            request.leverage > careerLevel.maxLeverage -> OrderRejectReason.LEVERAGE_EXCEEDS_CAREER_LIMIT
            else -> validateOcoBracketOnEntry(request, quote)
        }
        return permissionError ?: validateCashCoverage(portfolio, request, quote)
    }

    private fun validateOcoBracketOnEntry(request: OrderRequest, quote: MarketQuote): OrderRejectReason? {
        val tp = request.takeProfitPrice
        val sl = request.stopLossPrice
        val hasBracket = tp != null || sl != null
        val entrySupported = request.type == OrderType.MARKET || request.type == OrderType.LIMIT
        val refEntry = referenceEntryPrice(request, quote)
        return when {
            !hasBracket -> null
            !entrySupported -> OrderRejectReason.INVALID_OCO_BRACKET
            tp != null && !isValidBracketPrice(tp, refEntry, mustBeAbove = request.side == OrderSide.BUY) ->
                OrderRejectReason.INVALID_OCO_BRACKET
            sl != null && !isValidBracketPrice(sl, refEntry, mustBeAbove = request.side == OrderSide.SELL) ->
                OrderRejectReason.INVALID_OCO_BRACKET
            else -> null
        }
    }

    private fun referenceEntryPrice(request: OrderRequest, quote: MarketQuote): Money = when (request.type) {
        OrderType.LIMIT -> request.limitPrice ?: quote.midPrice
        OrderType.MARKET -> if (request.side == OrderSide.BUY) quote.askPrice else quote.bidPrice
        else -> quote.midPrice
    }

    private fun isValidBracketPrice(price: Money, refEntry: Money, mustBeAbove: Boolean): Boolean =
        isValidMoneyPrice(price) && if (mustBeAbove) price > refEntry else price < refEntry

    private fun validateCashCoverage(
        portfolio: PortfolioState,
        request: OrderRequest,
        quote: MarketQuote
    ): OrderRejectReason? {
        val checkPrice = OrderFormulas.referenceExecutionPriceForMargin(request, quote)
        val notional = checkPrice * request.quantity
        val requiredMargin = RiskEngine.calculateRequiredMargin(notional, request.leverage)
        val commission = OrderFormulas.calculateCommission(notional)
        return if (portfolio.availableCash < requiredMargin + commission) {
            OrderRejectReason.INSUFFICIENT_CASH
        } else {
            null
        }
    }

    private fun isValidMoneyPrice(price: Money?): Boolean =
        price != null && price.minorUnits >= GameBalance.MIN_ASSET_PRICE_MINOR
}
