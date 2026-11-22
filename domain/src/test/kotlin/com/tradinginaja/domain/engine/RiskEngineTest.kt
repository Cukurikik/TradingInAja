package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.core.common.Money
import com.tradinginaja.domain.model.Candle
import com.tradinginaja.domain.model.CareerLevel
import com.tradinginaja.domain.model.EquityPoint
import com.tradinginaja.domain.model.LiquidityTier
import com.tradinginaja.domain.model.MarginStatus
import com.tradinginaja.domain.model.MarketQuote
import com.tradinginaja.domain.model.OrderRejectReason
import com.tradinginaja.domain.model.OrderRequest
import com.tradinginaja.domain.model.OrderSide
import com.tradinginaja.domain.model.OrderStatus
import com.tradinginaja.domain.model.OrderSubmissionResult
import com.tradinginaja.domain.model.OrderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pengujian komprehensif Fase 4:
 * - Stop-Market & Stop-Limit (termasuk gap harga melewati stop + slippage)
 * - Take-Profit / Stop-Loss OCO (pembatalan peer & prioritas SL konservatif)
 * - Short selling, biaya pinjam per tick, dan batas leverage per level karier
 * - Margin Call (<= 110%) & Likuidasi Paksa (<= 100%)
 * - Metrik risiko portofolio (drawdown, win rate, risk/reward, exposure, Sharpe sederhana)
 * - Kasus tepi: posisi 0, kas habis/nol, dan gap ekstrem melebihi ekuitas (insolvency guard)
 */
class RiskEngineTest {

    private fun quoteAt(midMajor: Long, spreadMajor: Long = 2L): MarketQuote = MarketQuote(
        symbol = "NSKP",
        midPrice = Money.fromMajor(midMajor),
        bidPrice = Money.fromMajor(midMajor - spreadMajor),
        askPrice = Money.fromMajor(midMajor + spreadMajor),
        spreadBps = 10L,
        referenceTickVolume = 10_000L,
        liquidityTier = LiquidityTier.HIGH
    )

    private fun candleAt(index: Long, open: Double, high: Double, low: Double, close: Double): Candle = Candle(
        index = index,
        startTick = index,
        open = open,
        high = high,
        low = low,
        close = close,
        volume = 10_000L
    )

    @Test
    fun `career level enforces leverage limits and short selling permissions`() {
        val quote = quoteAt(1_000L)
        val novice = OrderEngine.createInitialPortfolio(
            initialCash = Money.fromMajor(1_000_000L),
            careerLevel = CareerLevel.LEVEL_1_NOVICE
        )

        val (_, lev3Novice) = OrderEngine.submitOrder(
            novice,
            OrderRequest("n1", "NSKP", OrderSide.BUY, OrderType.MARKET, quantity = 10L, leverage = 3),
            quote,
            tickIndex = 1L
        )
        assertEquals(
            OrderRejectReason.LEVERAGE_EXCEEDS_CAREER_LIMIT,
            (lev3Novice as OrderSubmissionResult.Rejected).reason
        )

        val (_, shortNovice) = OrderEngine.submitOrder(
            novice,
            OrderRequest("n2", "NSKP", OrderSide.SELL, OrderType.MARKET, quantity = 10L, leverage = 1),
            quote,
            tickIndex = 2L
        )
        assertEquals(
            OrderRejectReason.SHORT_NOT_ALLOWED_FOR_LEVEL,
            (shortNovice as OrderSubmissionResult.Rejected).reason
        )

        val activeLv2 = novice.copy(careerLevel = CareerLevel.LEVEL_2_ACTIVE)
        val (_, lev2Active) = OrderEngine.submitOrder(
            activeLv2,
            OrderRequest("a1", "NSKP", OrderSide.BUY, OrderType.MARKET, quantity = 10L, leverage = 2),
            quote,
            tickIndex = 3L
        )
        assertEquals(
            OrderRejectReason.LEVERAGE_EXCEEDS_CAREER_LIMIT,
            (lev2Active as OrderSubmissionResult.Rejected).reason
        )

        val proLv3 = novice.copy(careerLevel = CareerLevel.LEVEL_3_PRO)
        val (_, shortPro3x) = OrderEngine.submitOrder(
            proLv3,
            OrderRequest("p1", "NSKP", OrderSide.SELL, OrderType.MARKET, quantity = 10L, leverage = 3),
            quote,
            tickIndex = 4L
        )
        assertTrue(shortPro3x is OrderSubmissionResult.Executed)

        val (_, lev5Pro) = OrderEngine.submitOrder(
            proLv3,
            OrderRequest("p2", "NSKP", OrderSide.BUY, OrderType.MARKET, quantity = 10L, leverage = 5),
            quote,
            tickIndex = 5L
        )
        assertEquals(
            OrderRejectReason.LEVERAGE_EXCEEDS_CAREER_LIMIT,
            (lev5Pro as OrderSubmissionResult.Rejected).reason
        )

        val fundLv4 = novice.copy(careerLevel = CareerLevel.LEVEL_4_FUND_MANAGER)
        val (_, lev5Fund) = OrderEngine.submitOrder(
            fundLv4,
            OrderRequest("f1", "NSKP", OrderSide.BUY, OrderType.MARKET, quantity = 10L, leverage = 5),
            quote,
            tickIndex = 6L
        )
        assertTrue(lev5Fund is OrderSubmissionResult.Executed)
    }

    @Test
    fun `stop market executes with extra gap slippage when market gaps past trigger`() {
        val quote = quoteAt(1_000L)
        val initial = OrderEngine.createInitialPortfolio(initialCash = Money.fromMajor(1_000_000L))
        val (withLong, _) = OrderEngine.submitOrder(
            initial,
            OrderRequest("buy-1", "NSKP", OrderSide.BUY, OrderType.MARKET, quantity = 100L),
            quote,
            tickIndex = 1L
        )

        val triggerPrice = Money.fromMajor(950L)
        val (withStop, stopRes) = OrderEngine.submitOrder(
            withLong,
            OrderRequest(
                orderId = "stop-1",
                symbol = "NSKP",
                side = OrderSide.SELL,
                type = OrderType.STOP_MARKET,
                quantity = 100L,
                triggerPrice = triggerPrice
            ),
            quote,
            tickIndex = 2L
        )
        assertTrue(stopRes is OrderSubmissionResult.PlacedPending)

        // Gap down melewati trigger 950: open di 860, quote bid 855
        val gapQuote = quoteAt(857L, spreadMajor = 2L)
        val gapCandle = candleAt(index = 3L, open = 860.0, high = 870.0, low = 850.0, close = 857.0)
        val afterGap = OrderEngine.processMarketTick(
            state = withStop,
            candlesBySymbol = mapOf("NSKP" to gapCandle),
            quotesBySymbol = mapOf("NSKP" to gapQuote),
            tickIndex = 3L,
            executedAtEpochMillis = 3_000L
        )

        assertNull(afterGap.positions["NSKP"])
        val stopFill = afterGap.fills.last()
        assertEquals("stop-1", stopFill.orderId)
        assertTrue(stopFill.slippageBps >= GameBalance.STOP_GAP_EXTRA_SLIPPAGE_BPS)
        assertTrue(stopFill.executionPrice < Money.fromMajor(860L))
        assertTrue(stopFill.realizedPnl.isNegative)
    }

    @Test
    fun `stop limit converts to limit order on adverse gap and fills only when price recovers`() {
        val quote = quoteAt(1_000L)
        val initial = OrderEngine.createInitialPortfolio(initialCash = Money.fromMajor(1_000_000L))
        val (withLong, _) = OrderEngine.submitOrder(
            initial,
            OrderRequest("buy-sl", "NSKP", OrderSide.BUY, OrderType.MARKET, quantity = 50L),
            quote,
            tickIndex = 1L
        )

        val (withStopLimit, res) = OrderEngine.submitOrder(
            withLong,
            OrderRequest(
                orderId = "sl-1",
                symbol = "NSKP",
                side = OrderSide.SELL,
                type = OrderType.STOP_LIMIT,
                quantity = 50L,
                triggerPrice = Money.fromMajor(950L),
                limitPrice = Money.fromMajor(940L)
            ),
            quote,
            tickIndex = 2L
        )
        assertTrue(res is OrderSubmissionResult.PlacedPending)

        // Gap down di bawah limit 940 (high hanya 920): stop terpicu, TIDAK boleh fill di bawah 940 -> ubah ke LIMIT
        val gapBelowLimitCandle = candleAt(index = 3L, open = 910.0, high = 920.0, low = 895.0, close = 905.0)
        val afterTriggerOnly = OrderEngine.processMarketTick(
            state = withStopLimit,
            candlesBySymbol = mapOf("NSKP" to gapBelowLimitCandle),
            quotesBySymbol = mapOf("NSKP" to quoteAt(905L)),
            tickIndex = 3L,
            executedAtEpochMillis = 3_000L
        )

        assertNotNull(afterTriggerOnly.positions["NSKP"])
        val convertedOrder = afterTriggerOnly.orders.first { it.orderId == "sl-1" }
        assertEquals(OrderStatus.PENDING, convertedOrder.status)
        assertEquals(OrderType.LIMIT, convertedOrder.type)
        assertNull(convertedOrder.triggerPrice)

        // Tick berikutnya harga memantul melewati limit 940 -> order LIMIT terisi >= 940
        val reboundCandle = candleAt(index = 4L, open = 930.0, high = 948.0, low = 925.0, close = 945.0)
        val afterRebound = OrderEngine.processMarketTick(
            state = afterTriggerOnly,
            candlesBySymbol = mapOf("NSKP" to reboundCandle),
            quotesBySymbol = mapOf("NSKP" to quoteAt(945L)),
            tickIndex = 4L,
            executedAtEpochMillis = 4_000L
        )
        assertNull(afterRebound.positions["NSKP"])
        val fill = afterRebound.fills.last()
        assertTrue(fill.executionPrice >= Money.fromMajor(940L))
    }

    @Test
    fun `oco bracket spawns tp and sl and cancels peer when one fills prioritizing sl on wide candle`() {
        val quote = quoteAt(1_000L)
        val initial = OrderEngine.createInitialPortfolio(
            initialCash = Money.fromMajor(1_000_000L),
            careerLevel = CareerLevel.LEVEL_3_PRO
        )
        val entryReq = OrderRequest(
            orderId = "entry-oco",
            symbol = "NSKP",
            side = OrderSide.BUY,
            type = OrderType.MARKET,
            quantity = 40L,
            takeProfitPrice = Money.fromMajor(1_100L),
            stopLossPrice = Money.fromMajor(920L)
        )
        val (withOco, _) = OrderEngine.submitOrder(initial, entryReq, quote, tickIndex = 1L)
        val tpOrder = withOco.orders.first { it.orderId == "entry-oco_tp" }
        val slOrder = withOco.orders.first { it.orderId == "entry-oco_sl" }
        assertEquals(OrderStatus.PENDING, tpOrder.status)
        assertEquals(OrderStatus.PENDING, slOrder.status)
        assertEquals(tpOrder.ocoGroupId, slOrder.ocoGroupId)
        assertEquals(40L, withOco.reservedClosingQuantity("NSKP", OrderSide.SELL))

        // Skenario 1: TP tercapai -> TP FILLED dan SL otomatis CANCELLED
        val tpCandle = candleAt(index = 2L, open = 1_060.0, high = 1_120.0, low = 1_050.0, close = 1_110.0)
        val afterTp = OrderEngine.processMarketTick(
            state = withOco,
            candlesBySymbol = mapOf("NSKP" to tpCandle),
            quotesBySymbol = mapOf("NSKP" to quoteAt(1_110L)),
            tickIndex = 2L,
            executedAtEpochMillis = 2_000L
        )
        assertNull(afterTp.positions["NSKP"])
        assertEquals(OrderStatus.FILLED, afterTp.orders.first { it.orderId == "entry-oco_tp" }.status)
        assertEquals(OrderStatus.CANCELLED, afterTp.orders.first { it.orderId == "entry-oco_sl" }.status)
        assertEquals(0L, afterTp.reservedClosingQuantity("NSKP", OrderSide.SELL))

        // Skenario 2: Candle lebar menyentuh SL dan TP bersamaan -> SL diprioritaskan konservatif & TP dibatalkan
        val wideCandle = candleAt(index = 2L, open = 1_000.0, high = 1_150.0, low = 900.0, close = 1_080.0)
        val afterWide = OrderEngine.processMarketTick(
            state = withOco,
            candlesBySymbol = mapOf("NSKP" to wideCandle),
            quotesBySymbol = mapOf("NSKP" to quoteAt(1_080L)),
            tickIndex = 2L,
            executedAtEpochMillis = 2_000L
        )
        assertNull(afterWide.positions["NSKP"])
        assertEquals(OrderStatus.FILLED, afterWide.orders.first { it.orderId == "entry-oco_sl" }.status)
        assertEquals(OrderStatus.CANCELLED, afterWide.orders.first { it.orderId == "entry-oco_tp" }.status)
    }

    @Test
    fun `short selling profits on price decline and deducts borrow fee each tick`() {
        val entryQuote = quoteAt(2_000L, spreadMajor = 2L)
        val initial = OrderEngine.createInitialPortfolio(
            initialCash = Money.fromMajor(500_000L),
            careerLevel = CareerLevel.LEVEL_3_PRO
        )
        val (withShort, res) = OrderEngine.submitOrder(
            initial,
            OrderRequest("short-1", "NSKP", OrderSide.SELL, OrderType.MARKET, quantity = 100L, leverage = 2),
            entryQuote,
            tickIndex = 1L
        )
        assertTrue(res is OrderSubmissionResult.Executed)
        assertTrue(withShort.positions["NSKP"]?.isShort == true)

        val lowerQuote = quoteAt(1_800L, spreadMajor = 2L)
        val stableCandle = candleAt(index = 2L, open = 1_800.0, high = 1_810.0, low = 1_795.0, close = 1_800.0)
        val afterTick = OrderEngine.processMarketTick(
            state = withShort,
            candlesBySymbol = mapOf("NSKP" to stableCandle),
            quotesBySymbol = mapOf("NSKP" to lowerQuote),
            tickIndex = 2L,
            executedAtEpochMillis = 2_000L
        )

        assertTrue(afterTick.totalBorrowFeesPaid.isPositive)
        val shortPos = afterTick.positions["NSKP"]!!
        assertEquals(afterTick.totalBorrowFeesPaid, shortPos.accumulatedBorrowFee)
        assertTrue(shortPos.unrealizedPnl(lowerQuote.midPrice).isPositive)

        val (afterClose, closeRes) = OrderEngine.submitOrder(
            afterTick,
            OrderRequest("cover-1", "NSKP", OrderSide.BUY, OrderType.MARKET, quantity = 100L),
            lowerQuote,
            tickIndex = 3L
        )
        assertTrue(closeRes is OrderSubmissionResult.Executed)
        assertNull(afterClose.positions["NSKP"])
        assertTrue((closeRes as OrderSubmissionResult.Executed).fill.realizedPnl.isPositive)
    }

    @Test
    fun `margin call and forced liquidation trigger at exact thresholds`() {
        // Modal 100.000, beli 400 unit @ ~1.000 dengan leverage 5x -> margin ~80.100, ekuitas ~98.800 (~123% HEALTHY)
        val entryQuote = quoteAt(1_000L, spreadMajor = 1L)
        val initial = OrderEngine.createInitialPortfolio(
            initialCash = Money.fromMajor(100_000L),
            careerLevel = CareerLevel.LEVEL_5_HEDGE_FUND
        )
        val (withLeveraged, res) = OrderEngine.submitOrder(
            initial,
            OrderRequest("lev-5x", "NSKP", OrderSide.BUY, OrderType.MARKET, quantity = 400L, leverage = 5),
            entryQuote,
            tickIndex = 1L
        )
        assertTrue(res is OrderSubmissionResult.Executed)
        assertEquals(
            MarginStatus.HEALTHY,
            RiskEngine.evaluateMarginHealth(withLeveraged, mapOf("NSKP" to entryQuote)).status
        )

        // Turun ke 970: rugi ~12.000 -> ekuitas ~86.800 / margin ~80.100 = ~108% -> MARGIN_CALL (<= 110%)
        val warnQuote = quoteAt(970L, spreadMajor = 1L)
        val warnHealth = RiskEngine.evaluateMarginHealth(withLeveraged, mapOf("NSKP" to warnQuote))
        assertEquals(MarginStatus.MARGIN_CALL, warnHealth.status)

        // Turun ke 940: ekuitas turun di bawah 80.100 (~93%) -> marginLevel <= 100% -> LIQUIDATION
        val crashQuote = quoteAt(940L, spreadMajor = 1L)
        val crashCandle = candleAt(index = 2L, open = 941.0, high = 943.0, low = 938.0, close = 940.0)
        val afterLiq = OrderEngine.processMarketTick(
            state = withLeveraged,
            candlesBySymbol = mapOf("NSKP" to crashCandle),
            quotesBySymbol = mapOf("NSKP" to crashQuote),
            tickIndex = 2L,
            executedAtEpochMillis = 2_000L
        )

        assertTrue(afterLiq.positions.isEmpty())
        assertEquals(1, afterLiq.liquidationEvents.size)
        assertTrue(afterLiq.fills.last().isLiquidation)
        assertTrue(afterLiq.cashBalance >= Money.ZERO)
        assertEquals(
            MarginStatus.HEALTHY,
            RiskEngine.evaluateMarginHealth(afterLiq, mapOf("NSKP" to crashQuote)).status
        )
    }

    @Test
    fun `edge cases zero position exhausted cash and black swan gap keep accounting intact`() {
        val quote = quoteAt(1_000L)

        // Kasus tepi 1: kas habis (0) tidak boleh membuka posisi baru
        val zeroCash = OrderEngine.createInitialPortfolio(
            initialCash = Money.ZERO,
            careerLevel = CareerLevel.LEVEL_3_PRO
        )
        val (_, noCashRes) = OrderEngine.submitOrder(
            zeroCash,
            OrderRequest("zc-1", "NSKP", OrderSide.BUY, OrderType.MARKET, quantity = 1L),
            quote,
            tickIndex = 1L
        )
        assertEquals(OrderRejectReason.INSUFFICIENT_CASH, (noCashRes as OrderSubmissionResult.Rejected).reason)

        // Kasus tepi 2: posisi 0 ditolak saat mencoba menutup di Level 1 & likuidasi posisi kosong aman (no-op)
        val noPosState = OrderEngine.createInitialPortfolio(initialCash = Money.fromMajor(50_000L))
        assertEquals(noPosState, RiskEngine.liquidateIfNeeded(noPosState, mapOf("NSKP" to quote), 1L, 1_000L))
        assertEquals(MarginStatus.HEALTHY, RiskEngine.evaluateMarginHealth(noPosState, mapOf("NSKP" to quote)).status)

        // Kasus tepi 3: gap black-swan 5x Long anjlok 50% (rugi > 100% ekuitas)
        // Kas wajib tetap >= 0 lewat insolvencyCoverage
        val initial = OrderEngine.createInitialPortfolio(
            initialCash = Money.fromMajor(100_000L),
            careerLevel = CareerLevel.LEVEL_5_HEDGE_FUND
        )
        val (with5x, _) = OrderEngine.submitOrder(
            initial,
            OrderRequest("bs-1", "NSKP", OrderSide.BUY, OrderType.MARKET, quantity = 450L, leverage = 5),
            quote,
            tickIndex = 1L
        )
        val blackSwanQuote = quoteAt(500L, spreadMajor = 2L)
        val blackSwanCandle = candleAt(index = 2L, open = 500.0, high = 505.0, low = 495.0, close = 500.0)
        val afterCrash = OrderEngine.processMarketTick(
            state = with5x,
            candlesBySymbol = mapOf("NSKP" to blackSwanCandle),
            quotesBySymbol = mapOf("NSKP" to blackSwanQuote),
            tickIndex = 2L,
            executedAtEpochMillis = 2_000L
        )

        assertTrue(afterCrash.positions.isEmpty())
        assertEquals(Money.ZERO, afterCrash.cashBalance)
        assertTrue(afterCrash.insolvencyCoverage.isPositive)
        val totalEquity =
            afterCrash.cashBalance + OrderEngine.computePositionsNetEquity(afterCrash, mapOf("NSKP" to blackSwanQuote))
        assertEquals(
            totalEquity - afterCrash.initialCash,
            afterCrash.totalRealizedPnl - afterCrash.totalBorrowFeesPaid + afterCrash.insolvencyCoverage
        )
    }

    @Test
    fun `risk metrics accurately compute drawdown win rate risk reward exposure and sharpe`() {
        val quoteNskp = quoteAt(1_000L)
        val base = OrderEngine.createInitialPortfolio(
            initialCash = Money.fromMajor(100_000L),
            careerLevel = CareerLevel.LEVEL_3_PRO
        )

        // Trade 1: Buy 100 @ ~1001, Sell @ 1200 (Win)
        var state = OrderEngine.submitOrder(
            base,
            OrderRequest("w-buy", "NSKP", OrderSide.BUY, OrderType.MARKET, quantity = 50L),
            quoteNskp,
            1L
        ).first
        state = OrderEngine.submitOrder(
            state,
            OrderRequest("w-sell", "NSKP", OrderSide.SELL, OrderType.MARKET, quantity = 50L),
            quoteAt(1_200L),
            2L
        ).first

        // Trade 2: Buy 50 @ ~1001, Sell @ 900 (Loss)
        state = OrderEngine.submitOrder(
            state,
            OrderRequest("l-buy", "NSKP", OrderSide.BUY, OrderType.MARKET, quantity = 50L),
            quoteNskp,
            3L
        ).first
        state = OrderEngine.submitOrder(
            state,
            OrderRequest("l-sell", "NSKP", OrderSide.SELL, OrderType.MARKET, quantity = 50L),
            quoteAt(900L),
            4L
        ).first

        // Trade 3: Posisi Short aktif untuk cek gross vs net exposure negatif
        state = OrderEngine.submitOrder(
            state,
            OrderRequest("s-open", "NSKP", OrderSide.SELL, OrderType.MARKET, quantity = 40L, leverage = 2),
            quoteAt(1_000L),
            5L
        ).first

        // Kurva ekuitas: 100k -> 110k (peak) -> 99k (10% max DD) -> 104.5k (5% current DD)
        val curve = listOf(
            EquityPoint(1L, Money.fromMajor(100_000L), Money.fromMajor(100_000L), Money.ZERO, 1L),
            EquityPoint(2L, Money.fromMajor(110_000L), Money.fromMajor(110_000L), Money.ZERO, 2L),
            EquityPoint(3L, Money.fromMajor(99_000L), Money.fromMajor(99_000L), Money.ZERO, 3L),
            EquityPoint(4L, Money.fromMajor(104_500L), Money.fromMajor(104_500L), Money.ZERO, 4L)
        )
        state = state.copy(equityCurve = curve)

        val metrics = RiskEngine.computePortfolioMetrics(state, mapOf("NSKP" to quoteAt(1_000L)))
        assertEquals(1, metrics.winCount)
        assertEquals(1, metrics.lossCount)
        assertEquals(50.0, metrics.winRatePct, 0.01)
        assertTrue(metrics.riskRewardRatio > 1.5)
        assertEquals(10.0, metrics.maxDrawdownPct, 0.01)
        assertTrue(metrics.grossExposurePct > 0.0)
        assertEquals(-metrics.grossExposurePct, metrics.netExposurePct, 0.01)
        assertNotNull(metrics.simpleSharpeRatio)
        assertFalse(metrics.simpleSharpeRatio!!.isNaN())

        // Kurva dengan < 4 titik -> Sharpe masih null
        val shortCurveMetrics = RiskEngine.computePortfolioMetrics(
            state.copy(equityCurve = curve.take(2)),
            mapOf("NSKP" to quoteAt(1_000L))
        )
        assertNull(shortCurveMetrics.simpleSharpeRatio)
    }
}
