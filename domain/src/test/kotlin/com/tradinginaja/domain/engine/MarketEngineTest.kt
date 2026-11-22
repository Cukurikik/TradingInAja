package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.domain.model.AssetCategory
import com.tradinginaja.domain.model.AssetSpec
import com.tradinginaja.domain.model.LiquidityTier
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketEngineTest {

    private val testStockSpec = AssetSpec(
        symbol = "NSKP",
        name = "Nusantara Komputasi Tbk",
        sector = "Teknologi",
        category = AssetCategory.STOCK,
        initialPrice = 4_500.0,
        annualVolatility = 0.38,
        annualDrift = 0.11,
        liquidityTier = LiquidityTier.HIGH,
        baseVolumePerTick = 14_500L
    )

    @Test
    fun `same seed produces identical deterministic state and candle history`() {
        val seed = 99_887_766L
        val stateA = MarketEngine.stepMany(
            state = MarketEngine.createInitialState(testStockSpec, seed = seed, initialCandles = 50),
            ticks = 250
        )
        val stateB = MarketEngine.stepMany(
            state = MarketEngine.createInitialState(testStockSpec, seed = seed, initialCandles = 50),
            ticks = 250
        )

        assertEquals(stateA.tickIndex, stateB.tickIndex)
        assertEquals(stateA.rngState, stateB.rngState)
        assertEquals(stateA.currentPrice, stateB.currentPrice, 0.0)
        assertEquals(stateA.baseCandles, stateB.baseCandles)
    }

    @Test
    fun `different seeds produce different trajectories`() {
        val stateA = MarketEngine.createInitialState(testStockSpec, seed = 1001L, initialCandles = 40)
        val stateB = MarketEngine.createInitialState(testStockSpec, seed = 2002L, initialCandles = 40)

        assertNotEquals(stateA.currentPrice, stateB.currentPrice)
    }

    @Test
    fun `step is pure and does not mutate previous state`() {
        val initial = MarketEngine.createInitialState(testStockSpec, seed = 777L, initialCandles = 10)
        val nextFirst = MarketEngine.step(initial)
        val nextSecond = MarketEngine.step(initial)

        assertEquals(10L, initial.tickIndex)
        assertEquals(10, initial.baseCandles.size)
        assertEquals(nextFirst, nextSecond)
    }

    @Test
    fun `prices remain strictly positive even under extreme volatility and negative drift`() {
        val extremeSpec = AssetSpec(
            symbol = "EXTM",
            name = "Aset Uji Ekstrem",
            sector = "Uji",
            category = AssetCategory.CRYPTO,
            initialPrice = 10.0,
            annualVolatility = 2.2,
            annualDrift = -0.9,
            liquidityTier = LiquidityTier.LOW_MEDIUM,
            baseVolumePerTick = 1_000L
        )

        var state = MarketEngine.createInitialState(extremeSpec, seed = 12_345L, initialCandles = 0)
        repeat(3_000) {
            state = MarketEngine.step(state)
            val candle = state.baseCandles.last()
            assertTrue("Open must be > 0", candle.open > 0.0)
            assertTrue("High must be > 0", candle.high > 0.0)
            assertTrue("Low must be > 0", candle.low > 0.0)
            assertTrue("Close must be > 0", candle.close > 0.0)
            assertTrue("Low <= bodyLow", candle.low <= min(candle.open, candle.close))
            assertTrue("High >= bodyHigh", candle.high >= max(candle.open, candle.close))
            assertTrue("Volume >= 1", candle.volume >= 1L)
            assertTrue("Money minor units >= 1", candle.closeAsMoney().minorUnits >= 1L)
        }
    }

    @Test
    fun `empirical log return distribution matches configured GBM parameters`() {
        val targetDrift = 0.12
        val targetVol = 0.35
        val spec = AssetSpec(
            symbol = "GBM1",
            name = "Aset Uji GBM",
            sector = "Uji",
            category = AssetCategory.STOCK,
            initialPrice = 10_000.0,
            annualVolatility = targetVol,
            annualDrift = targetDrift,
            liquidityTier = LiquidityTier.HIGH,
            baseVolumePerTick = 50_000L
        )

        val sampleCount = 12_000
        var state = MarketEngine.createInitialState(spec, seed = 202_601L, initialCandles = 0)
        val logReturns = DoubleArray(sampleCount)

        for (i in 0 until sampleCount) {
            val prevPrice = state.currentPrice
            state = MarketEngine.step(state)
            logReturns[i] = ln(state.currentPrice / prevPrice)
        }

        val meanReturn = logReturns.average()
        var sumSquaredDiff = 0.0
        for (r in logReturns) {
            val diff = r - meanReturn
            sumSquaredDiff += diff * diff
        }
        val sampleStdDev = sqrt(sumSquaredDiff / (sampleCount - 1))

        val annualizedVol = sampleStdDev * sqrt(GameBalance.TICKS_PER_TRADING_YEAR)
        val volRelativeError = abs(annualizedVol - targetVol) / targetVol
        assertTrue(
            "Annualized empirical volatility ($annualizedVol) should be within 5% of $targetVol",
            volRelativeError < 0.05
        )

        val theoreticalAnnualLogMean = targetDrift - 0.5 * targetVol * targetVol
        val annualizedEmpiricalMean = meanReturn * GameBalance.TICKS_PER_TRADING_YEAR
        val standardErrorAnnualMean = targetVol / sqrt(sampleCount / GameBalance.TICKS_PER_TRADING_YEAR)
        assertTrue(
            "Annualized empirical log-mean ($annualizedEmpiricalMean) " +
                "should be within 3 SE of $theoreticalAnnualLogMean",
            abs(annualizedEmpiricalMean - theoreticalAnnualLogMean) <= 3.0 * standardErrorAnnualMean
        )
    }
}
