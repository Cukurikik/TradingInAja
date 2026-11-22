package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.core.common.Money
import com.tradinginaja.domain.model.Candle
import com.tradinginaja.domain.model.CareerLevel
import com.tradinginaja.domain.model.EquityPoint
import com.tradinginaja.domain.model.MarketQuote
import com.tradinginaja.domain.model.Order
import com.tradinginaja.domain.model.OrderRejectReason
import com.tradinginaja.domain.model.OrderRequest
import com.tradinginaja.domain.model.OrderStatus
import com.tradinginaja.domain.model.OrderSubmissionResult
import com.tradinginaja.domain.model.OrderType
import com.tradinginaja.domain.model.PortfolioState

/**
 * Mesin eksekusi order & akuntansi portofolio pure Kotlin (tanpa import Android).
 * Seluruh transisi bersifat murni dan idempoten berdasarkan [OrderRequest.orderId].
 */
object OrderEngine {

    fun createInitialPortfolio(
        initialCash: Money = Money(GameBalance.INITIAL_VIRTUAL_CASH_MINOR),
        careerLevel: CareerLevel = CareerLevel.LEVEL_1_NOVICE,
        initialTickIndex: Long = 0L,
        recordedAtEpochMillis: Long = 0L
    ): PortfolioState = PortfolioState(
        initialCash = initialCash,
        cashBalance = initialCash,
        careerLevel = careerLevel,
        equityCurve = listOf(
            EquityPoint(
                tickIndex = initialTickIndex,
                equity = initialCash,
                cash = initialCash,
                positionsValue = Money.ZERO,
                recordedAtEpochMillis = recordedAtEpochMillis
            )
        )
    )

    fun submitOrder(
        state: PortfolioState,
        request: OrderRequest,
        quote: MarketQuote,
        tickIndex: Long
    ): Pair<PortfolioState, OrderSubmissionResult> {
        val existingOrder = state.orders.firstOrNull { it.orderId == request.orderId }
        val isDuplicate = existingOrder != null || state.processedOrderIds.contains(request.orderId)
        return if (isDuplicate) {
            val fallback = existingOrder ?: OrderAccounting.createRejectedOrder(
                request = request,
                tickIndex = tickIndex,
                reason = OrderRejectReason.INVALID_QUANTITY
            )
            state to OrderSubmissionResult.DuplicateIgnored(fallback)
        } else {
            val rejectReason = OrderFormulas.validateRequest(state, request, quote)
            if (rejectReason != null) {
                val rejected = OrderAccounting.createRejectedOrder(request, tickIndex, rejectReason)
                val nextState = state.copy(
                    orders = state.orders + rejected,
                    processedOrderIds = state.processedOrderIds + request.orderId
                )
                nextState to OrderSubmissionResult.Rejected(rejected, rejectReason)
            } else {
                when (request.type) {
                    OrderType.MARKET -> OrderSubmissionExecutor.executeMarketOrder(state, request, quote, tickIndex)
                    OrderType.LIMIT ->
                        OrderSubmissionExecutor.placeOrExecuteLimitOrder(state, request, quote, tickIndex)
                    OrderType.STOP_MARKET,
                    OrderType.STOP_LIMIT ->
                        OrderSubmissionExecutor.placePendingTriggerOrder(state, request, tickIndex)
                }
            }
        }
    }

    fun cancelOrder(state: PortfolioState, orderId: String, tickIndex: Long): PortfolioState {
        val target = state.orders.firstOrNull { it.orderId == orderId }
        return if (target == null || target.status != OrderStatus.PENDING) {
            state
        } else {
            val updatedOrders = state.orders.map { order ->
                if (order.orderId == orderId) {
                    order.copy(status = OrderStatus.CANCELLED, reservedCash = Money.ZERO, updatedAtTick = tickIndex)
                } else {
                    order
                }
            }
            state.copy(orders = updatedOrders)
        }
    }

    /**
     * Memproses satu tick pasar penuh:
     * 1. Biaya pinjam (short & leverage) per tick
     * 2. Evaluasi likuidasi paksa bila Margin Level <= 100%
     * 3. Pencocokan order tertunda (Limit, Stop, Stop-Limit, dan OCO)
     * 4. Evaluasi ulang margin setelah fill baru
     */
    fun processMarketTick(
        state: PortfolioState,
        candlesBySymbol: Map<String, Candle>,
        quotesBySymbol: Map<String, MarketQuote>,
        tickIndex: Long,
        executedAtEpochMillis: Long
    ): PortfolioState {
        val afterBorrow = RiskEngine.applyTickBorrowFees(state, quotesBySymbol)
        val afterPreLiq = RiskEngine.liquidateIfNeeded(afterBorrow, quotesBySymbol, tickIndex, executedAtEpochMillis)
        val afterMatches = matchPendingOrders(
            state = afterPreLiq,
            candlesBySymbol = candlesBySymbol,
            quotesBySymbol = quotesBySymbol,
            tickIndex = tickIndex,
            executedAtEpochMillis = executedAtEpochMillis
        )
        return RiskEngine.liquidateIfNeeded(afterMatches, quotesBySymbol, tickIndex, executedAtEpochMillis)
    }

    fun matchPendingOrders(
        state: PortfolioState,
        candlesBySymbol: Map<String, Candle>,
        quotesBySymbol: Map<String, MarketQuote>,
        tickIndex: Long,
        executedAtEpochMillis: Long
    ): PortfolioState {
        var current = state
        // Evaluasi Stop/Stop-Limit lebih dahulu daripada Limit agar skenario OCO konservatif (SL diprioritaskan).
        val pendingIds = state.orders
            .filter { it.status == OrderStatus.PENDING }
            .sortedBy { if (it.type == OrderType.LIMIT) 1 else 0 }
            .map { it.orderId }

        for (orderId in pendingIds) {
            val order = current.orders.firstOrNull { it.orderId == orderId && it.status == OrderStatus.PENDING }
            val candle = order?.let { candlesBySymbol[it.symbol] }
            val quote = order?.let { quotesBySymbol[it.symbol] }
            if (candle != null && quote != null) {
                current = applyPendingOrderOutcome(
                    current = current,
                    order = order,
                    candle = candle,
                    quote = quote,
                    tickIndex = tickIndex,
                    executedAtEpochMillis = executedAtEpochMillis
                )
            }
        }
        return current
    }

    fun recordEquitySnapshot(
        state: PortfolioState,
        quotesBySymbol: Map<String, MarketQuote>,
        tickIndex: Long,
        recordedAtEpochMillis: Long
    ): PortfolioState {
        val positionsContribution = computePositionsNetEquity(state, quotesBySymbol)
        val totalEquity = state.cashBalance + positionsContribution
        val point = EquityPoint(
            tickIndex = tickIndex,
            equity = totalEquity,
            cash = state.cashBalance,
            positionsValue = positionsContribution,
            recordedAtEpochMillis = recordedAtEpochMillis
        )
        val existing = state.equityCurve
        val updatedCurve = if (existing.lastOrNull()?.tickIndex == tickIndex) {
            existing.dropLast(1) + point
        } else {
            (existing + point).takeLast(GameBalance.MAX_EQUITY_CURVE_POINTS)
        }
        return state.copy(equityCurve = updatedCurve)
    }

    /**
     * Kontribusi ekuitas bersih seluruh posisi terbuka (`marginLocked + unrealizedPnl`).
     * Untuk posisi Spot 1x Long, nilainya identik dengan nilai pasar kotor (`markPrice * qty`).
     */
    fun computePositionsNetEquity(state: PortfolioState, quotesBySymbol: Map<String, MarketQuote>): Money =
        state.positions.values.fold(Money.ZERO) { acc, pos ->
            val markPrice = quotesBySymbol[pos.symbol]?.midPrice ?: pos.averageEntryPrice
            acc + pos.netEquityContribution(markPrice)
        }

    fun computePositionsMarketValue(state: PortfolioState, quotesBySymbol: Map<String, MarketQuote>): Money =
        computePositionsNetEquity(state, quotesBySymbol)

    fun computeTotalUnrealizedPnl(state: PortfolioState, quotesBySymbol: Map<String, MarketQuote>): Money =
        state.positions.values.fold(Money.ZERO) { acc, pos ->
            val markPrice = quotesBySymbol[pos.symbol]?.midPrice ?: pos.averageEntryPrice
            acc + pos.unrealizedPnl(markPrice)
        }

    private fun applyPendingOrderOutcome(
        current: PortfolioState,
        order: Order,
        candle: Candle,
        quote: MarketQuote,
        tickIndex: Long,
        executedAtEpochMillis: Long
    ): PortfolioState = when (val outcome = OrderAccounting.evaluatePendingTrigger(order, candle, quote)) {
        is PendingTriggerEvaluator.TriggerOutcome.Fill -> OrderAccounting.applyFillToOrder(
            state = current,
            order = order,
            executionPrice = outcome.executionPrice,
            slippageBps = outcome.slippageBps,
            tickIndex = tickIndex,
            executedAtEpochMillis = executedAtEpochMillis
        ).first
        PendingTriggerEvaluator.TriggerOutcome.ConvertStopLimitToLimit -> {
            val converted = current.orders.map { existing ->
                if (existing.orderId == order.orderId) {
                    existing.copy(type = OrderType.LIMIT, triggerPrice = null, updatedAtTick = tickIndex)
                } else {
                    existing
                }
            }
            current.copy(orders = converted)
        }
        null -> current
    }
}
