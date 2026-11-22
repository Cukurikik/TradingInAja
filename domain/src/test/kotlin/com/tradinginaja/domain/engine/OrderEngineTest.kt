package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.Money
import com.tradinginaja.domain.model.Candle
import com.tradinginaja.domain.model.LiquidityTier
import com.tradinginaja.domain.model.MarketQuote
import com.tradinginaja.domain.model.OrderRejectReason
import com.tradinginaja.domain.model.OrderRequest
import com.tradinginaja.domain.model.OrderSide
import com.tradinginaja.domain.model.OrderStatus
import com.tradinginaja.domain.model.OrderSubmissionResult
import com.tradinginaja.domain.model.OrderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderEngineTest {

    private val highLiqQuote = MarketQuote(
        symbol = "NSKP",
        midPrice = Money.fromMajor(4_500L),
        bidPrice = Money.fromMajor(4_498L),
        askPrice = Money.fromMajor(4_502L),
        spreadBps = 10L,
        referenceTickVolume = 18_000L,
        liquidityTier = LiquidityTier.HIGH
    )

    private val lowLiqQuote = MarketQuote(
        symbol = "KPSW",
        midPrice = Money.fromMajor(18_400L),
        bidPrice = Money.fromMajor(18_368L),
        askPrice = Money.fromMajor(18_432L),
        spreadBps = 35L,
        referenceTickVolume = 2_880L,
        liquidityTier = LiquidityTier.LOW_MEDIUM
    )

    @Test
    fun `slippage grows with order size and lower liquidity tier`() {
        val smallHigh = OrderFormulas.calculateSlippageBps(quantity = 10L, quote = highLiqQuote)
        val largeHigh = OrderFormulas.calculateSlippageBps(quantity = 9_000L, quote = highLiqQuote)
        val smallLow = OrderFormulas.calculateSlippageBps(quantity = 10L, quote = lowLiqQuote)
        val largeLow = OrderFormulas.calculateSlippageBps(quantity = 2_500L, quote = lowLiqQuote)

        assertTrue(largeHigh > smallHigh)
        assertTrue(smallLow > smallHigh)
        assertTrue(largeLow > largeHigh)
    }

    @Test
    fun `market buy and partial sell update average entry, realized pnl, and cash accurately`() {
        val initial = OrderEngine.createInitialPortfolio(initialCash = Money.fromMajor(1_000_000L))
        val buyReq = OrderRequest(
            orderId = "ord-buy-1",
            symbol = "NSKP",
            side = OrderSide.BUY,
            type = OrderType.MARKET,
            quantity = 10L,
            createdAtEpochMillis = 1_000L
        )
        val (afterBuy, buyResult) = OrderEngine.submitOrder(initial, buyReq, highLiqQuote, tickIndex = 1L)
        assertTrue(buyResult is OrderSubmissionResult.Executed)
        val buyFill = (buyResult as OrderSubmissionResult.Executed).fill

        val positionAfterBuy = afterBuy.positions["NSKP"]!!
        assertEquals(10L, positionAfterBuy.quantity)
        assertEquals(buyFill.executionPrice, positionAfterBuy.averageEntryPrice)
        assertEquals(-buyFill.commission, afterBuy.totalRealizedPnl)

        val higherQuote = highLiqQuote.copy(
            midPrice = Money.fromMajor(5_000L),
            bidPrice = Money.fromMajor(4_998L),
            askPrice = Money.fromMajor(5_002L)
        )
        val sellReq = OrderRequest(
            orderId = "ord-sell-1",
            symbol = "NSKP",
            side = OrderSide.SELL,
            type = OrderType.MARKET,
            quantity = 4L,
            createdAtEpochMillis = 2_000L
        )
        val (afterSell, sellResult) = OrderEngine.submitOrder(afterBuy, sellReq, higherQuote, tickIndex = 2L)
        assertTrue(sellResult is OrderSubmissionResult.Executed)
        val sellFill = (sellResult as OrderSubmissionResult.Executed).fill

        val remainingPos = afterSell.positions["NSKP"]!!
        assertEquals(6L, remainingPos.quantity)
        assertEquals(buyFill.executionPrice, remainingPos.averageEntryPrice)
        assertTrue(sellFill.realizedPnl.isPositive)
        assertEquals(buyFill.realizedPnl + sellFill.realizedPnl, afterSell.totalRealizedPnl)

        val closeAllReq = OrderRequest(
            orderId = "ord-sell-2",
            symbol = "NSKP",
            side = OrderSide.SELL,
            type = OrderType.MARKET,
            quantity = 6L,
            createdAtEpochMillis = 3_000L
        )
        val (afterClose, _) = OrderEngine.submitOrder(afterSell, closeAllReq, higherQuote, tickIndex = 3L)
        assertNull(afterClose.positions["NSKP"])
    }

    @Test
    fun `resubmitting same orderId is idempotent and does not duplicate cash or fill effects`() {
        val initial = OrderEngine.createInitialPortfolio(initialCash = Money.fromMajor(500_000L))
        val request = OrderRequest(
            orderId = "idempotent-123",
            symbol = "NSKP",
            side = OrderSide.BUY,
            type = OrderType.MARKET,
            quantity = 5L
        )

        val (once, res1) = OrderEngine.submitOrder(initial, request, highLiqQuote, tickIndex = 5L)
        assertTrue(res1 is OrderSubmissionResult.Executed)

        val (twice, res2) = OrderEngine.submitOrder(once, request, highLiqQuote, tickIndex = 6L)
        assertTrue(res2 is OrderSubmissionResult.DuplicateIgnored)
        assertEquals(once, twice)
        assertEquals(1, twice.fills.size)
        assertEquals(5L, twice.positions["NSKP"]?.quantity)
    }

    @Test
    fun `limit order reserves funds and matches on candle crossing limit price`() {
        val initial = OrderEngine.createInitialPortfolio(initialCash = Money.fromMajor(100_000L))
        val limitPrice = Money.fromMajor(4_300L)
        val limitReq = OrderRequest(
            orderId = "lim-buy-1",
            symbol = "NSKP",
            side = OrderSide.BUY,
            type = OrderType.LIMIT,
            quantity = 10L,
            limitPrice = limitPrice
        )

        val (pendingState, res) = OrderEngine.submitOrder(initial, limitReq, highLiqQuote, tickIndex = 10L)
        assertTrue(res is OrderSubmissionResult.PlacedPending)
        assertEquals(OrderStatus.PENDING, pendingState.orders.single().status)
        assertEquals(initial.cashBalance, pendingState.cashBalance)
        assertTrue(pendingState.availableCash < initial.cashBalance)

        val triggerCandle = Candle(
            index = 11L,
            startTick = 11L,
            open = 4_400.0,
            high = 4_420.0,
            low = 4_250.0,
            close = 4_350.0,
            volume = 12_000L
        )
        val filledState = OrderEngine.matchPendingOrders(
            state = pendingState,
            candlesBySymbol = mapOf("NSKP" to triggerCandle),
            quotesBySymbol = mapOf("NSKP" to highLiqQuote),
            tickIndex = 11L,
            executedAtEpochMillis = 9_999L
        )

        assertEquals(OrderStatus.FILLED, filledState.orders.single().status)
        assertEquals(Money.ZERO, filledState.reservedCashForPendingBuys)
        assertEquals(1, filledState.fills.size)
        assertTrue(filledState.fills.single().executionPrice <= limitPrice)
        assertEquals(10L, filledState.positions["NSKP"]?.quantity)
    }

    @Test
    fun `order exceeding cash or owned position is rejected without negative balance`() {
        val initial = OrderEngine.createInitialPortfolio(initialCash = Money.fromMajor(10_000L))
        val tooBigBuy = OrderRequest(
            orderId = "reject-buy",
            symbol = "NSKP",
            side = OrderSide.BUY,
            type = OrderType.MARKET,
            quantity = 10L
        )
        val (afterBuyReject, buyRes) = OrderEngine.submitOrder(initial, tooBigBuy, highLiqQuote, tickIndex = 1L)
        assertTrue(buyRes is OrderSubmissionResult.Rejected)
        assertEquals(OrderRejectReason.INSUFFICIENT_CASH, (buyRes as OrderSubmissionResult.Rejected).reason)
        assertEquals(initial.cashBalance, afterBuyReject.cashBalance)

        val sellWithoutPos = OrderRequest(
            orderId = "reject-sell",
            symbol = "NSKP",
            side = OrderSide.SELL,
            type = OrderType.MARKET,
            quantity = 1L
        )
        val (_, sellRes) = OrderEngine.submitOrder(afterBuyReject, sellWithoutPos, highLiqQuote, tickIndex = 2L)
        assertTrue(sellRes is OrderSubmissionResult.Rejected)
        assertEquals(
            OrderRejectReason.SHORT_NOT_ALLOWED_FOR_LEVEL,
            (sellRes as OrderSubmissionResult.Rejected).reason
        )

        val smallBuy = OrderRequest(
            orderId = "small-buy",
            symbol = "NSKP",
            side = OrderSide.BUY,
            type = OrderType.MARKET,
            quantity = 1L
        )
        val (withOneShare, _) = OrderEngine.submitOrder(afterBuyReject, smallBuy, highLiqQuote, tickIndex = 3L)
        val oversell = OrderRequest(
            orderId = "oversell",
            symbol = "NSKP",
            side = OrderSide.SELL,
            type = OrderType.MARKET,
            quantity = 3L
        )
        val (_, oversellRes) = OrderEngine.submitOrder(withOneShare, oversell, highLiqQuote, tickIndex = 4L)
        assertTrue(oversellRes is OrderSubmissionResult.Rejected)
        assertEquals(OrderRejectReason.INSUFFICIENT_POSITION, (oversellRes as OrderSubmissionResult.Rejected).reason)
    }
}
