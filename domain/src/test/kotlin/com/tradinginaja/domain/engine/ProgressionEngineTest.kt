package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.core.common.Money
import com.tradinginaja.domain.model.AssetCategory
import com.tradinginaja.domain.model.CareerLevel
import com.tradinginaja.domain.model.LearnLessonIds
import com.tradinginaja.domain.model.LearningProgress
import com.tradinginaja.domain.model.LiquidityTier
import com.tradinginaja.domain.model.MarketQuote
import com.tradinginaja.domain.model.MissionId
import com.tradinginaja.domain.model.Order
import com.tradinginaja.domain.model.OrderSide
import com.tradinginaja.domain.model.OrderStatus
import com.tradinginaja.domain.model.OrderType
import com.tradinginaja.domain.model.TradeFill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionEngineTest {

    private val categories = mapOf(
        "NSKP" to AssetCategory.STOCK,
        "BPAT" to AssetCategory.STOCK,
        "XVMG" to AssetCategory.COMMODITY,
        "GRDC" to AssetCategory.CRYPTO
    )

    private val quotes = mapOf(
        "NSKP" to quote("NSKP", 4_500_00L),
        "BPAT" to quote("BPAT", 8_200_00L)
    )

    @Test
    fun `money alone without risk missions does not unlock level 2`() {
        val richNovice = OrderEngine.createInitialPortfolio(
            initialCash = Money(GameBalance.INITIAL_VIRTUAL_CASH_MINOR * 3L)
        )
        val metrics = RiskEngine.computePortfolioMetrics(richNovice, quotes)
        val snapshot = ProgressionEngine.evaluateCareerProgress(
            portfolio = richNovice,
            categoriesBySymbol = categories,
            learningProgress = LearningProgress(),
            metrics = metrics
        )

        assertEquals(CareerLevel.LEVEL_1_NOVICE, snapshot.highestCompetencyUnlockedLevel)
        assertFalse(snapshot.allMissionsForNextLevelPassed)
        assertEquals(CareerLevel.LEVEL_2_ACTIVE, snapshot.nextLevel)
    }

    @Test
    fun `completing all level 1 competency missions unlocks level 2`() {
        val fills = listOf(
            sampleFill("f1", "NSKP", OrderSide.BUY, OrderType.MARKET, 10L, 4_500_00L, 0L, 1L),
            sampleFill("f2", "NSKP", OrderSide.SELL, OrderType.MARKET, 10L, 4_600_00L, 90_000L, 2L),
            sampleFill("f3", "BPAT", OrderSide.BUY, OrderType.MARKET, 10L, 8_200_00L, 0L, 3L),
            sampleFill("f4", "BPAT", OrderSide.SELL, OrderType.MARKET, 10L, 8_350_00L, 140_000L, 4L),
            sampleFill("f5", "NSKP", OrderSide.BUY, OrderType.MARKET, 5L, 4_500_00L, 0L, 5L)
        )
        val orders = listOf(
            sampleOrder("o1", "NSKP", OrderType.MARKET, stopLossMinor = 4_350_00L),
            sampleOrder("o2", "BPAT", OrderType.MARKET, stopLossMinor = 7_900_00L)
        )
        val portfolio = OrderEngine.createInitialPortfolio().copy(
            orders = orders,
            fills = fills
        )
        val metrics = RiskEngine.computePortfolioMetrics(portfolio, quotes)
        val learning = LearningProgress(completedLessonIds = setOf(LearnLessonIds.BASICS_SPREAD_SL))

        val snapshot = ProgressionEngine.evaluateCareerProgress(
            portfolio = portfolio,
            categoriesBySymbol = categories,
            learningProgress = learning,
            metrics = metrics
        )

        assertEquals(CareerLevel.LEVEL_2_ACTIVE, snapshot.highestCompetencyUnlockedLevel)
        assertTrue(snapshot.allMissionsForNextLevelPassed)
        assertTrue(
            snapshot.missionsForNextLevel
                .first { it.id == MissionId.USE_STOP_LOSS }
                .isCompleted
        )
    }

    @Test
    fun `estimateStopLossRiskPct calculates equity percentage accurately`() {
        val riskPct = ProgressionEngine.estimateStopLossRiskPct(
            entryPrice = Money(10_000_00L),
            stopLossPrice = Money(9_000_00L),
            quantity = 100L,
            accountEquity = Money(10_000_000_00L)
        )!!
        assertEquals(1.0, riskPct, 0.001)
    }

    private fun quote(symbol: String, midMinor: Long): MarketQuote = MarketQuote(
        symbol = symbol,
        midPrice = Money(midMinor),
        bidPrice = Money(midMinor - 100L),
        askPrice = Money(midMinor + 100L),
        spreadBps = 10L,
        referenceTickVolume = 10_000L,
        liquidityTier = LiquidityTier.HIGH
    )

    private fun sampleOrder(id: String, symbol: String, type: OrderType, stopLossMinor: Long?): Order = Order(
        orderId = id,
        symbol = symbol,
        side = OrderSide.BUY,
        type = type,
        quantity = 10L,
        limitPrice = null,
        stopLossPrice = stopLossMinor?.let { Money(it) },
        reservedCash = Money.ZERO,
        status = OrderStatus.FILLED,
        createdAtTick = 1L,
        createdAtEpochMillis = 1L,
        updatedAtTick = 1L
    )

    private fun sampleFill(
        id: String,
        symbol: String,
        side: OrderSide,
        type: OrderType,
        qty: Long,
        priceMinor: Long,
        pnlMinor: Long,
        tick: Long
    ): TradeFill = TradeFill(
        fillId = id,
        orderId = id,
        symbol = symbol,
        side = side,
        orderType = type,
        quantity = qty,
        executionPrice = Money(priceMinor),
        grossNotional = Money(priceMinor * qty),
        commission = Money(100L),
        slippageBps = 5L,
        realizedPnl = Money(pnlMinor),
        tickIndex = tick,
        executedAtEpochMillis = tick
    )
}
