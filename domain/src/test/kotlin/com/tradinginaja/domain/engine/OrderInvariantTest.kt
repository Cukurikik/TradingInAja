package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.Money
import com.tradinginaja.domain.model.AssetCategory
import com.tradinginaja.domain.model.AssetSpec
import com.tradinginaja.domain.model.LiquidityTier
import com.tradinginaja.domain.model.MarketCatalog
import com.tradinginaja.domain.model.OrderRequest
import com.tradinginaja.domain.model.OrderSide
import com.tradinginaja.domain.model.OrderStatus
import com.tradinginaja.domain.model.OrderSubmissionResult
import com.tradinginaja.domain.model.OrderType
import com.tradinginaja.domain.model.PortfolioState
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uji properti/invarian deterministik untuk OrderEngine + MarketEngine:
 * 1. Saldo tunai dan availableCash tidak pernah negatif.
 * 2. Total ekuitas = saldo tunai + nilai pasar seluruh posisi terbuka.
 * 3. Total PnL terealisasi = jumlah seluruh net realizedPnL pada TradeFill.
 * 4. Ekuitas - modal awal = Realized PnL + Unrealized PnL (konsisten presisi 0 sen).
 * 5. Idempotensi pada pengulangan orderId apa pun.
 */
class OrderInvariantTest {

    private val testCatalog = MarketCatalog(
        schemaVersion = 1,
        assets = listOf(
            AssetSpec(
                symbol = "NSKP",
                name = "Nusantara Komputasi Tbk",
                sector = "Teknologi",
                category = AssetCategory.STOCK,
                initialPrice = 4_500.0,
                annualVolatility = 0.45,
                annualDrift = 0.08,
                liquidityTier = LiquidityTier.HIGH,
                baseVolumePerTick = 12_000L
            ),
            AssetSpec(
                symbol = "GRDC",
                name = "GarudaChain",
                sector = "Layer-1 Fiktif",
                category = AssetCategory.CRYPTO,
                initialPrice = 125_000.0,
                annualVolatility = 0.85,
                annualDrift = 0.05,
                liquidityTier = LiquidityTier.MEDIUM,
                baseVolumePerTick = 8_000L
            ),
            AssetSpec(
                symbol = "XVMG",
                name = "Emas Murni Virtual",
                sector = "Logam Mulia",
                category = AssetCategory.COMMODITY,
                initialPrice = 1_350_000.0,
                annualVolatility = 0.20,
                annualDrift = 0.03,
                liquidityTier = LiquidityTier.VERY_HIGH,
                baseVolumePerTick = 15_000L
            )
        ),
        correlationMatrix = CholeskyDecomposition.identityMatrix(3)
    )

    @Test
    fun `randomized multi asset trading and market ticks preserve all accounting invariants`() {
        val random = Random(20_260_303)
        var marketState = MarketEngine.createMultiAssetInitialState(testCatalog, seed = 77_8899L, initialCandles = 30)
        var portfolio = OrderEngine.createInitialPortfolio(
            initialCash = Money.fromMajor(50_000_000L),
            initialTickIndex = marketState.tickIndex
        )
        val submittedIds = mutableListOf<String>()

        repeat(400) { stepIndex ->
            marketState = MarketEngine.stepAll(marketState)
            val quotes = marketState.assetStates.associate { it.spec.symbol to OrderFormulas.quoteFromAssetState(it) }
            val latestCandles = marketState.assetStates.associate { it.spec.symbol to it.baseCandles.last() }

            portfolio = OrderEngine.processMarketTick(
                state = portfolio,
                candlesBySymbol = latestCandles,
                quotesBySymbol = quotes,
                tickIndex = marketState.tickIndex,
                executedAtEpochMillis = stepIndex.toLong()
            )
            assertAccountingInvariants(portfolio, quotes)

            val asset = marketState.assetStates[random.nextInt(marketState.assetStates.size)]
            val symbol = asset.spec.symbol
            val quote = quotes.getValue(symbol)
            val orderId = "fuzz-$stepIndex"
            val side = if (random.nextBoolean()) OrderSide.BUY else OrderSide.SELL
            val type = if (random.nextDouble() < 0.65) OrderType.MARKET else OrderType.LIMIT
            val qty = random.nextLong(1L, 120L)
            val limitOffsetPct = if (side == OrderSide.BUY) {
                random.nextDouble(0.92, 1.03)
            } else {
                random.nextDouble(0.97, 1.08)
            }
            val limitPrice = if (type == OrderType.LIMIT) {
                Money((quote.midPrice.minorUnits * limitOffsetPct).toLong().coerceAtLeast(1L))
            } else {
                null
            }

            val req = OrderRequest(
                orderId = orderId,
                symbol = symbol,
                side = side,
                type = type,
                quantity = qty,
                limitPrice = limitPrice,
                createdAtEpochMillis = stepIndex.toLong()
            )
            val (nextPortfolio, _) = OrderEngine.submitOrder(portfolio, req, quote, marketState.tickIndex)
            portfolio = nextPortfolio
            submittedIds.add(orderId)

            // Uji idempotensi: kirim ulang order yang baru saja diproses tidak mengubah state
            val (afterDup, dupResult) = OrderEngine.submitOrder(portfolio, req, quote, marketState.tickIndex)
            assertTrue(dupResult is OrderSubmissionResult.DuplicateIgnored)
            assertEquals(portfolio, afterDup)

            // Sesekali batalkan order pending acak
            if (stepIndex % 7 == 0) {
                val pending = portfolio.orders.firstOrNull { it.status == OrderStatus.PENDING }
                if (pending != null) {
                    portfolio = OrderEngine.cancelOrder(portfolio, pending.orderId, marketState.tickIndex)
                }
            }

            portfolio = OrderEngine.recordEquitySnapshot(
                state = portfolio,
                quotesBySymbol = quotes,
                tickIndex = marketState.tickIndex,
                recordedAtEpochMillis = stepIndex.toLong()
            )
            assertAccountingInvariants(portfolio, quotes)
        }

        // Pastikan seluruh orderId yang pernah disubmit idempoten saat diputar ulang
        val quotes = marketState.assetStates.associate { it.spec.symbol to OrderFormulas.quoteFromAssetState(it) }
        for (oldId in submittedIds.take(25)) {
            val replayReq = OrderRequest(
                orderId = oldId,
                symbol = "NSKP",
                side = OrderSide.BUY,
                type = OrderType.MARKET,
                quantity = 1L
            )
            val (replayedState, replayRes) = OrderEngine.submitOrder(
                portfolio,
                replayReq,
                quotes.getValue("NSKP"),
                999L
            )
            assertTrue(replayRes is OrderSubmissionResult.DuplicateIgnored)
            assertEquals(portfolio, replayedState)
        }
    }

    @Test
    fun `randomized leveraged short stop and oco trading preserves accounting invariants`() {
        val random = Random(99_441_122)
        var marketState = MarketEngine.createMultiAssetInitialState(testCatalog, seed = 44_5566L, initialCandles = 30)
        var portfolio = OrderEngine.createInitialPortfolio(
            initialCash = Money.fromMajor(10_000_000L),
            careerLevel = com.tradinginaja.domain.model.CareerLevel.LEVEL_5_HEDGE_FUND,
            initialTickIndex = marketState.tickIndex
        )

        repeat(300) { stepIndex ->
            marketState = MarketEngine.stepAll(marketState)
            val quotes = marketState.assetStates.associate { it.spec.symbol to OrderFormulas.quoteFromAssetState(it) }
            val latestCandles = marketState.assetStates.associate { it.spec.symbol to it.baseCandles.last() }

            portfolio = OrderEngine.processMarketTick(
                state = portfolio,
                candlesBySymbol = latestCandles,
                quotesBySymbol = quotes,
                tickIndex = marketState.tickIndex,
                executedAtEpochMillis = stepIndex.toLong()
            )
            assertAccountingInvariants(portfolio, quotes)

            val asset = marketState.assetStates[random.nextInt(marketState.assetStates.size)]
            val symbol = asset.spec.symbol
            val quote = quotes.getValue(symbol)
            val req = buildRandomLeveragedOrderRequest(
                random,
                stepIndex,
                symbol,
                quote,
                portfolio.positions[symbol] != null
            )
            portfolio = OrderEngine.submitOrder(portfolio, req, quote, marketState.tickIndex).first
            portfolio = OrderEngine.recordEquitySnapshot(
                state = portfolio,
                quotesBySymbol = quotes,
                tickIndex = marketState.tickIndex,
                recordedAtEpochMillis = stepIndex.toLong()
            )
            assertAccountingInvariants(portfolio, quotes)
        }
    }

    private fun buildRandomLeveragedOrderRequest(
        random: Random,
        stepIndex: Int,
        symbol: String,
        quote: com.tradinginaja.domain.model.MarketQuote,
        hasExistingPosition: Boolean
    ): OrderRequest {
        val side = if (random.nextBoolean()) OrderSide.BUY else OrderSide.SELL
        val leverage = listOf(1, 2, 3, 5)[random.nextInt(4)]
        val qty = random.nextLong(1L, 40L)
        val type = pickRandomOrderType(random.nextDouble())
        val mid = quote.midPrice.minorUnits
        val limitPrice = randomLimitPrice(random, type, side, mid)
        val triggerPrice = randomTriggerPrice(random, type, side, mid)
        val attachOco = type == OrderType.MARKET && !hasExistingPosition && random.nextBoolean()
        val refEntry = if (side == OrderSide.BUY) quote.askPrice else quote.bidPrice
        val (tpPrice, slPrice) = randomOcoPrices(attachOco, side, refEntry)
        return OrderRequest(
            orderId = "lev-fuzz-$stepIndex",
            symbol = symbol,
            side = side,
            type = type,
            quantity = qty,
            limitPrice = limitPrice,
            triggerPrice = triggerPrice,
            leverage = leverage,
            takeProfitPrice = tpPrice,
            stopLossPrice = slPrice,
            createdAtEpochMillis = stepIndex.toLong()
        )
    }

    private fun pickRandomOrderType(roll: Double): OrderType = when {
        roll < 0.50 -> OrderType.MARKET
        roll < 0.75 -> OrderType.LIMIT
        roll < 0.90 -> OrderType.STOP_MARKET
        else -> OrderType.STOP_LIMIT
    }

    private fun randomLimitPrice(random: Random, type: OrderType, side: OrderSide, mid: Long): Money? =
        if (type == OrderType.LIMIT || type == OrderType.STOP_LIMIT) {
            val factor = if (side == OrderSide.BUY) random.nextDouble(0.95, 1.03) else random.nextDouble(0.97, 1.05)
            Money((mid * factor).toLong().coerceAtLeast(1L))
        } else {
            null
        }

    private fun randomTriggerPrice(random: Random, type: OrderType, side: OrderSide, mid: Long): Money? =
        if (type == OrderType.STOP_MARKET || type == OrderType.STOP_LIMIT) {
            val factor = if (side == OrderSide.BUY) random.nextDouble(1.01, 1.06) else random.nextDouble(0.94, 0.99)
            Money((mid * factor).toLong().coerceAtLeast(1L))
        } else {
            null
        }

    private fun randomOcoPrices(attachOco: Boolean, side: OrderSide, refEntry: Money): Pair<Money?, Money?> =
        if (!attachOco) {
            null to null
        } else {
            val tpMult = if (side == OrderSide.BUY) 1.04 else 0.96
            val slMult = if (side == OrderSide.BUY) 0.96 else 1.04
            Money((refEntry.minorUnits * tpMult).toLong().coerceAtLeast(1L)) to
                Money((refEntry.minorUnits * slMult).toLong().coerceAtLeast(1L))
        }

    private fun assertAccountingInvariants(
        portfolio: PortfolioState,
        quotesBySymbol: Map<String, com.tradinginaja.domain.model.MarketQuote>
    ) {
        assertTrue("Cash balance must never be negative", portfolio.cashBalance.minorUnits >= 0L)
        assertTrue("Available cash must never be negative", portfolio.availableCash.minorUnits >= 0L)

        val positionsNetEquity = OrderEngine.computePositionsNetEquity(portfolio, quotesBySymbol)
        val totalUnrealizedPnl = OrderEngine.computeTotalUnrealizedPnl(portfolio, quotesBySymbol)
        val totalEquity = portfolio.cashBalance + positionsNetEquity
        val sumFillsPnl = Money(portfolio.fills.sumOf { it.realizedPnl.minorUnits })

        assertEquals(
            "Total realized PnL must equal exact sum of fills",
            sumFillsPnl,
            portfolio.totalRealizedPnl
        )
        assertEquals(
            "Equity - InitialCash must equal RealizedPnl + UnrealizedPnl - BorrowFees + InsolvencyCoverage",
            totalEquity - portfolio.initialCash,
            portfolio.totalRealizedPnl + totalUnrealizedPnl -
                portfolio.totalBorrowFeesPaid + portfolio.insolvencyCoverage
        )
        assertTrue(
            portfolio.positions.values.all {
                it.absoluteQuantity > 0L && it.costBasis.minorUnits >= 0L && it.marginLocked.minorUnits >= 0L
            }
        )
    }
}
