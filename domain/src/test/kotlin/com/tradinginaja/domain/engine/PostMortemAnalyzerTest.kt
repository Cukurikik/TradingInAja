package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.Money
import com.tradinginaja.domain.model.Order
import com.tradinginaja.domain.model.OrderSide
import com.tradinginaja.domain.model.OrderStatus
import com.tradinginaja.domain.model.OrderType
import com.tradinginaja.domain.model.PostMortemCause
import com.tradinginaja.domain.model.PostMortemSeverity
import com.tradinginaja.domain.model.TradeFill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostMortemAnalyzerTest {

    @Test
    fun `detects unhedged loss without stop loss`() {
        val orders = listOf(
            order("open-1", "NSKP", OrderSide.BUY, OrderType.MARKET, 100L, leverage = 1, slMinor = null, tick = 1L),
            order("close-1", "NSKP", OrderSide.SELL, OrderType.MARKET, 100L, leverage = 1, slMinor = null, tick = 4L)
        )
        val fills = listOf(
            fill("f1", "open-1", "NSKP", OrderSide.BUY, OrderType.MARKET, 100L, 4_500_00L, 0L, 1L),
            fill("f2", "close-1", "NSKP", OrderSide.SELL, OrderType.MARKET, 100L, 4_100_00L, -40_000_00L, 4L)
        )
        val portfolio = OrderEngine.createInitialPortfolio().copy(orders = orders, fills = fills)

        val result = PostMortemAnalyzer.analyzePortfolio(portfolio)
        assertEquals(1, result.size)
        val pm = result.first()
        assertFalse(pm.hadStopLossProtection)
        assertEquals(PostMortemCause.NO_STOP_LOSS, pm.primaryCause)
        assertEquals(PostMortemSeverity.WARNING, pm.severity)
    }

    @Test
    fun `detects over-leverage and forced liquidation on losing leveraged exit`() {
        val orders = listOf(
            order("open-lev", "GRDC", OrderSide.BUY, OrderType.MARKET, 20L, leverage = 3, slMinor = null, tick = 1L),
            order("liq-1", "GRDC", OrderSide.SELL, OrderType.MARKET, 20L, leverage = 3, slMinor = null, tick = 3L)
        )
        val fills = listOf(
            fill("f1", "open-lev", "GRDC", OrderSide.BUY, OrderType.MARKET, 20L, 125_000_00L, 0L, 1L),
            fill(
                id = "f2",
                orderId = "liq-1",
                symbol = "GRDC",
                side = OrderSide.SELL,
                type = OrderType.MARKET,
                qty = 20L,
                priceMinor = 90_000_00L,
                pnlMinor = -700_000_00L,
                tick = 3L,
                isLiquidation = true
            )
        )
        val portfolio = OrderEngine.createInitialPortfolio().copy(orders = orders, fills = fills)

        val result = PostMortemAnalyzer.analyzePortfolio(portfolio)
        assertEquals(1, result.size)
        val pm = result.first()
        assertEquals(PostMortemCause.FORCED_LIQUIDATION, pm.primaryCause)
        assertEquals(PostMortemSeverity.CRITICAL, pm.severity)
        assertTrue(pm.contributingCauses.contains(PostMortemCause.OVER_LEVERAGE))
    }

    @Test
    fun `rewards disciplined stop loss exit within 2 percent risk as constructive`() {
        val orders = listOf(
            order(
                "open-ok",
                "NSKP",
                OrderSide.BUY,
                OrderType.MARKET,
                50L,
                leverage = 1,
                slMinor = 4_300_00L,
                tick = 1L
            ),
            order(
                "open-ok_sl",
                "NSKP",
                OrderSide.SELL,
                OrderType.STOP_MARKET,
                50L,
                leverage = 1,
                slMinor = null,
                tick = 5L
            )
        )
        val fills = listOf(
            fill("f1", "open-ok", "NSKP", OrderSide.BUY, OrderType.MARKET, 50L, 4_500_00L, 0L, 1L),
            fill("f2", "open-ok_sl", "NSKP", OrderSide.SELL, OrderType.STOP_MARKET, 50L, 4_300_00L, -10_000_00L, 5L)
        )
        val portfolio = OrderEngine.createInitialPortfolio().copy(orders = orders, fills = fills)

        val result = PostMortemAnalyzer.analyzePortfolio(portfolio)
        assertEquals(1, result.size)
        val pm = result.first()
        assertTrue(pm.hadStopLossProtection)
        assertEquals(PostMortemCause.CONTROLLED_STOP_EXIT, pm.primaryCause)
        assertEquals(PostMortemSeverity.CONSTRUCTIVE, pm.severity)
    }

    private fun order(
        id: String,
        symbol: String,
        side: OrderSide,
        type: OrderType,
        qty: Long,
        leverage: Int,
        slMinor: Long?,
        tick: Long
    ): Order = Order(
        orderId = id,
        symbol = symbol,
        side = side,
        type = type,
        quantity = qty,
        limitPrice = null,
        stopLossPrice = slMinor?.let { Money(it) },
        leverage = leverage,
        reservedCash = Money.ZERO,
        status = OrderStatus.FILLED,
        createdAtTick = tick,
        createdAtEpochMillis = tick,
        updatedAtTick = tick
    )

    private fun fill(
        id: String,
        orderId: String,
        symbol: String,
        side: OrderSide,
        type: OrderType,
        qty: Long,
        priceMinor: Long,
        pnlMinor: Long,
        tick: Long,
        isLiquidation: Boolean = false
    ): TradeFill = TradeFill(
        fillId = id,
        orderId = orderId,
        symbol = symbol,
        side = side,
        orderType = type,
        quantity = qty,
        executionPrice = Money(priceMinor),
        grossNotional = Money(priceMinor * qty),
        commission = Money(50L),
        slippageBps = 5L,
        realizedPnl = Money(pnlMinor),
        tickIndex = tick,
        executedAtEpochMillis = tick,
        isLiquidation = isLiquidation
    )
}
