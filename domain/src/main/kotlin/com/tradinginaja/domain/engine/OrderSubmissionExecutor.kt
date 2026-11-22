package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.Money
import com.tradinginaja.domain.model.MarketQuote
import com.tradinginaja.domain.model.Order
import com.tradinginaja.domain.model.OrderRequest
import com.tradinginaja.domain.model.OrderSide
import com.tradinginaja.domain.model.OrderStatus
import com.tradinginaja.domain.model.OrderSubmissionResult
import com.tradinginaja.domain.model.PortfolioState

internal object OrderSubmissionExecutor {

    fun executeMarketOrder(
        state: PortfolioState,
        request: OrderRequest,
        quote: MarketQuote,
        tickIndex: Long
    ): Pair<PortfolioState, OrderSubmissionResult> {
        val slippageBps = OrderFormulas.calculateSlippageBps(request.quantity, quote)
        val refPrice = if (request.side == OrderSide.BUY) quote.askPrice else quote.bidPrice
        val execPrice = OrderFormulas.applySlippage(refPrice, request.side, slippageBps)
        val order = buildOrderFromRequest(request, reservedCash = Money.ZERO, tickIndex = tickIndex)
        val stateWithOrder = state.copy(
            orders = state.orders + order,
            processedOrderIds = state.processedOrderIds + request.orderId
        )
        val (nextState, fill) = OrderAccounting.applyFillToOrder(
            state = stateWithOrder,
            order = order,
            executionPrice = execPrice,
            slippageBps = slippageBps,
            tickIndex = tickIndex,
            executedAtEpochMillis = request.createdAtEpochMillis
        )
        val filledOrder = nextState.orders.last { it.orderId == request.orderId }
        return nextState to OrderSubmissionResult.Executed(filledOrder, fill)
    }

    fun placeOrExecuteLimitOrder(
        state: PortfolioState,
        request: OrderRequest,
        quote: MarketQuote,
        tickIndex: Long
    ): Pair<PortfolioState, OrderSubmissionResult> {
        val limitPrice = requireNotNull(request.limitPrice)
        val isMarketableNow = when (request.side) {
            OrderSide.BUY -> quote.askPrice <= limitPrice
            OrderSide.SELL -> quote.bidPrice >= limitPrice
        }
        val isClosing = OrderValidation.isClosingOrder(state.positions[request.symbol], request.side)
        val reservedCash = if (!isMarketableNow && !isClosing) {
            val maxNotional = limitPrice * request.quantity
            RiskEngine.calculateRequiredMargin(maxNotional, request.leverage) +
                OrderFormulas.calculateCommission(maxNotional)
        } else {
            Money.ZERO
        }

        val order = buildOrderFromRequest(request, reservedCash = reservedCash, tickIndex = tickIndex)
        val stateWithOrder = state.copy(
            orders = state.orders + order,
            processedOrderIds = state.processedOrderIds + request.orderId
        )

        return if (isMarketableNow) {
            val rawSlippage = OrderFormulas.calculateSlippageBps(request.quantity, quote)
            val execPrice = if (request.side == OrderSide.BUY) {
                minOf(OrderFormulas.applySlippage(quote.askPrice, OrderSide.BUY, rawSlippage), limitPrice)
            } else {
                maxOf(OrderFormulas.applySlippage(quote.bidPrice, OrderSide.SELL, rawSlippage), limitPrice)
            }
            val (nextState, fill) = OrderAccounting.applyFillToOrder(
                state = stateWithOrder,
                order = order,
                executionPrice = execPrice,
                slippageBps = 0L,
                tickIndex = tickIndex,
                executedAtEpochMillis = request.createdAtEpochMillis
            )
            val filledOrder = nextState.orders.last { it.orderId == request.orderId }
            nextState to OrderSubmissionResult.Executed(filledOrder, fill)
        } else {
            stateWithOrder to OrderSubmissionResult.PlacedPending(order)
        }
    }

    fun placePendingTriggerOrder(
        state: PortfolioState,
        request: OrderRequest,
        tickIndex: Long
    ): Pair<PortfolioState, OrderSubmissionResult> {
        val isClosing = OrderValidation.isClosingOrder(state.positions[request.symbol], request.side)
        val refPrice = request.limitPrice ?: requireNotNull(request.triggerPrice)
        val reservedCash = if (!isClosing) {
            val estNotional = refPrice * request.quantity
            RiskEngine.calculateRequiredMargin(estNotional, request.leverage) +
                OrderFormulas.calculateCommission(estNotional)
        } else {
            Money.ZERO
        }
        val order = buildOrderFromRequest(request, reservedCash = reservedCash, tickIndex = tickIndex)
        val nextState = state.copy(
            orders = state.orders + order,
            processedOrderIds = state.processedOrderIds + request.orderId
        )
        return nextState to OrderSubmissionResult.PlacedPending(order)
    }

    private fun buildOrderFromRequest(request: OrderRequest, reservedCash: Money, tickIndex: Long): Order = Order(
        orderId = request.orderId,
        symbol = request.symbol,
        side = request.side,
        type = request.type,
        quantity = request.quantity,
        limitPrice = request.limitPrice,
        triggerPrice = request.triggerPrice,
        leverage = request.leverage,
        takeProfitPrice = request.takeProfitPrice,
        stopLossPrice = request.stopLossPrice,
        ocoGroupId = request.ocoGroupId,
        reservedCash = reservedCash,
        status = OrderStatus.PENDING,
        createdAtTick = tickIndex,
        createdAtEpochMillis = request.createdAtEpochMillis,
        updatedAtTick = tickIndex
    )
}
