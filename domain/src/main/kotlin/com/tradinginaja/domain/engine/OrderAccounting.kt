package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.Money
import com.tradinginaja.domain.model.Candle
import com.tradinginaja.domain.model.MarketQuote
import com.tradinginaja.domain.model.Order
import com.tradinginaja.domain.model.OrderRejectReason
import com.tradinginaja.domain.model.OrderRequest
import com.tradinginaja.domain.model.OrderSide
import com.tradinginaja.domain.model.OrderStatus
import com.tradinginaja.domain.model.OrderType
import com.tradinginaja.domain.model.PortfolioState
import com.tradinginaja.domain.model.Position
import com.tradinginaja.domain.model.TradeFill

internal object OrderAccounting {

    fun applyFillToOrder(
        state: PortfolioState,
        order: Order,
        executionPrice: Money,
        slippageBps: Long,
        tickIndex: Long,
        executedAtEpochMillis: Long,
        isLiquidation: Boolean = false
    ): Pair<PortfolioState, TradeFill> {
        val grossNotional = executionPrice * order.quantity
        val commission = OrderFormulas.calculateCommission(grossNotional)
        val updatedPositions = state.positions.toMutableMap()
        val existingPos = updatedPositions[order.symbol]
        val isClosing = OrderValidation.isClosingOrder(existingPos, order.side)

        val deltaResult = if (isClosing && existingPos != null) {
            applyClosingFill(state, existingPos, order, grossNotional, commission, updatedPositions)
        } else {
            applyOpeningFill(state, existingPos, order, grossNotional, commission, updatedPositions)
        }

        val fill = TradeFill(
            fillId = "${order.orderId}_fill",
            orderId = order.orderId,
            symbol = order.symbol,
            side = order.side,
            orderType = order.type,
            quantity = order.quantity,
            executionPrice = executionPrice,
            grossNotional = grossNotional,
            commission = commission,
            slippageBps = slippageBps,
            realizedPnl = deltaResult.realizedPnl,
            tickIndex = tickIndex,
            executedAtEpochMillis = executedAtEpochMillis,
            isLiquidation = isLiquidation
        )

        val positionCompletelyClosed = !updatedPositions.containsKey(order.symbol)
        val updatedOrders = updateOrdersAfterFill(
            orders = state.orders,
            filledOrder = order,
            positionCompletelyClosed = positionCompletelyClosed,
            tickIndex = tickIndex
        )
        val spawnedBrackets = spawnBracketOrdersIfAny(
            entryOrder = order,
            wasOpening = !isClosing,
            tickIndex = tickIndex,
            executedAtEpochMillis = executedAtEpochMillis
        )
        val nextProcessedIds = state.processedOrderIds + order.orderId + spawnedBrackets.map { it.orderId }

        val nextState = state.copy(
            cashBalance = deltaResult.nextCash,
            insolvencyCoverage = deltaResult.nextInsolvency,
            positions = updatedPositions,
            orders = updatedOrders + spawnedBrackets,
            fills = state.fills + fill,
            processedOrderIds = nextProcessedIds
        )
        return nextState to fill
    }

    fun executeForcedLiquidation(
        state: PortfolioState,
        position: Position,
        orderId: String,
        closeSide: OrderSide,
        executionPrice: Money,
        slippageBps: Long,
        tickIndex: Long,
        executedAtEpochMillis: Long
    ): Pair<PortfolioState, TradeFill> {
        val liqOrder = Order(
            orderId = orderId,
            symbol = position.symbol,
            side = closeSide,
            type = OrderType.MARKET,
            quantity = position.absoluteQuantity,
            limitPrice = null,
            leverage = position.leverage,
            reservedCash = Money.ZERO,
            status = OrderStatus.PENDING,
            createdAtTick = tickIndex,
            createdAtEpochMillis = executedAtEpochMillis,
            updatedAtTick = tickIndex
        )
        val withOrder = state.copy(
            orders = state.orders + liqOrder,
            processedOrderIds = state.processedOrderIds + orderId
        )
        return applyFillToOrder(
            state = withOrder,
            order = liqOrder,
            executionPrice = executionPrice,
            slippageBps = slippageBps,
            tickIndex = tickIndex,
            executedAtEpochMillis = executedAtEpochMillis,
            isLiquidation = true
        )
    }

    fun evaluatePendingTrigger(
        order: Order,
        candle: Candle,
        quote: MarketQuote
    ): PendingTriggerEvaluator.TriggerOutcome? = PendingTriggerEvaluator.evaluate(order, candle, quote)

    fun createRejectedOrder(request: OrderRequest, tickIndex: Long, reason: OrderRejectReason): Order = Order(
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
        reservedCash = Money.ZERO,
        status = OrderStatus.REJECTED,
        createdAtTick = tickIndex,
        createdAtEpochMillis = request.createdAtEpochMillis,
        updatedAtTick = tickIndex,
        rejectReason = reason
    )

    private fun applyOpeningFill(
        state: PortfolioState,
        existingPos: Position?,
        order: Order,
        grossNotional: Money,
        commission: Money,
        updatedPositions: MutableMap<String, Position>
    ): CashAccountingDelta {
        val addedMargin = RiskEngine.calculateRequiredMargin(grossNotional, order.leverage)
        val cashOut = addedMargin + commission
        val rawCash = state.cashBalance - cashOut
        val (safeCash, extraInsolvency) = clampCashWithInsolvency(rawCash, state.insolvencyCoverage)

        val deltaQty = if (order.side == OrderSide.BUY) order.quantity else -order.quantity
        val nextQty = (existingPos?.signedQuantity ?: 0L) + deltaQty
        val nextCost = (existingPos?.costBasis ?: Money.ZERO) + grossNotional
        val nextMargin = (existingPos?.marginLocked ?: Money.ZERO) + addedMargin
        val nextLeverage = maxOf(existingPos?.leverage ?: 1, order.leverage)
        val nextBorrowFee = existingPos?.accumulatedBorrowFee ?: Money.ZERO

        updatedPositions[order.symbol] = Position(
            symbol = order.symbol,
            signedQuantity = nextQty,
            costBasis = nextCost,
            marginLocked = nextMargin,
            leverage = nextLeverage,
            accumulatedBorrowFee = nextBorrowFee
        )
        return CashAccountingDelta(safeCash, extraInsolvency, -commission)
    }

    private fun applyClosingFill(
        state: PortfolioState,
        currentPos: Position,
        order: Order,
        grossNotional: Money,
        commission: Money,
        updatedPositions: MutableMap<String, Position>
    ): CashAccountingDelta {
        val absQty = currentPos.absoluteQuantity
        val closeQty = order.quantity.coerceAtMost(absQty)
        val remainingAbs = absQty - closeQty
        val closedCost: Money
        val releasedMargin: Money
        if (remainingAbs == 0L) {
            closedCost = currentPos.costBasis
            releasedMargin = currentPos.marginLocked
            updatedPositions.remove(order.symbol)
        } else {
            val weights = listOf(closeQty, remainingAbs)
            closedCost = currentPos.costBasis.allocate(weights).first()
            releasedMargin = currentPos.marginLocked.allocate(weights).first()
            updatedPositions[order.symbol] = currentPos.copy(
                signedQuantity = if (currentPos.isLong) remainingAbs else -remainingAbs,
                costBasis = currentPos.costBasis - closedCost,
                marginLocked = currentPos.marginLocked - releasedMargin
            )
        }
        val grossPnl = if (currentPos.isLong) grossNotional - closedCost else closedCost - grossNotional
        val rawNextCash = state.cashBalance + releasedMargin + grossPnl - commission
        val (safeCash, nextInsolvency) = clampCashWithInsolvency(rawNextCash, state.insolvencyCoverage)
        return CashAccountingDelta(safeCash, nextInsolvency, grossPnl - commission)
    }

    private fun clampCashWithInsolvency(rawCash: Money, currentInsolvency: Money): Pair<Money, Money> =
        if (rawCash.minorUnits < 0L) {
            Money.ZERO to (currentInsolvency - rawCash)
        } else {
            rawCash to currentInsolvency
        }

    private fun updateOrdersAfterFill(
        orders: List<Order>,
        filledOrder: Order,
        positionCompletelyClosed: Boolean,
        tickIndex: Long
    ): List<Order> {
        val groupId = filledOrder.ocoGroupId
        return orders.map { existing ->
            val isTarget = existing.orderId == filledOrder.orderId
            val isOcoPeer = groupId != null && existing.ocoGroupId == groupId && !isTarget
            val isOrphanClose = positionCompletelyClosed &&
                existing.symbol == filledOrder.symbol &&
                existing.status == OrderStatus.PENDING &&
                !isTarget
            when {
                isTarget -> existing.copy(
                    status = OrderStatus.FILLED,
                    reservedCash = Money.ZERO,
                    updatedAtTick = tickIndex
                )
                (isOcoPeer || isOrphanClose) && existing.status == OrderStatus.PENDING -> existing.copy(
                    status = OrderStatus.CANCELLED,
                    reservedCash = Money.ZERO,
                    updatedAtTick = tickIndex
                )
                else -> existing
            }
        }
    }

    private fun spawnBracketOrdersIfAny(
        entryOrder: Order,
        wasOpening: Boolean,
        tickIndex: Long,
        executedAtEpochMillis: Long
    ): List<Order> {
        val tp = entryOrder.takeProfitPrice
        val sl = entryOrder.stopLossPrice
        if (!wasOpening || (tp == null && sl == null)) return emptyList()

        val closingSide = if (entryOrder.side == OrderSide.BUY) OrderSide.SELL else OrderSide.BUY
        val groupId = "${entryOrder.orderId}_oco"
        return buildList {
            if (tp != null) {
                add(
                    Order(
                        orderId = "${entryOrder.orderId}_tp",
                        symbol = entryOrder.symbol,
                        side = closingSide,
                        type = OrderType.LIMIT,
                        quantity = entryOrder.quantity,
                        limitPrice = tp,
                        leverage = entryOrder.leverage,
                        ocoGroupId = groupId,
                        reservedCash = Money.ZERO,
                        status = OrderStatus.PENDING,
                        createdAtTick = tickIndex,
                        createdAtEpochMillis = executedAtEpochMillis,
                        updatedAtTick = tickIndex
                    )
                )
            }
            if (sl != null) {
                add(
                    Order(
                        orderId = "${entryOrder.orderId}_sl",
                        symbol = entryOrder.symbol,
                        side = closingSide,
                        type = OrderType.STOP_MARKET,
                        quantity = entryOrder.quantity,
                        limitPrice = null,
                        triggerPrice = sl,
                        leverage = entryOrder.leverage,
                        ocoGroupId = groupId,
                        reservedCash = Money.ZERO,
                        status = OrderStatus.PENDING,
                        createdAtTick = tickIndex,
                        createdAtEpochMillis = executedAtEpochMillis,
                        updatedAtTick = tickIndex
                    )
                )
            }
        }
    }

    private data class CashAccountingDelta(val nextCash: Money, val nextInsolvency: Money, val realizedPnl: Money)
}
