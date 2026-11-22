package com.tradinginaja.domain.engine

import com.tradinginaja.domain.model.AssetCategory
import com.tradinginaja.domain.model.AssetSpec
import com.tradinginaja.domain.model.JumpDiffusionParams
import com.tradinginaja.domain.model.LiquidityTier
import com.tradinginaja.domain.model.MarketCatalog
import com.tradinginaja.domain.model.MeanReversionParams
import com.tradinginaja.domain.model.PriceModelType
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketCorrelationTest {

    @Test
    fun `cholesky decomposition reconstructs symmetric positive definite matrix`() {
        val target = arrayOf(
            doubleArrayOf(1.00, 0.70, -0.30, 0.15),
            doubleArrayOf(0.70, 1.00, -0.20, 0.25),
            doubleArrayOf(-0.30, -0.20, 1.00, -0.10),
            doubleArrayOf(0.15, 0.25, -0.10, 1.00)
        )

        val lower = CholeskyDecomposition.decompose(target)
        for (i in target.indices) {
            for (j in target.indices) {
                var dot = 0.0
                for (k in target.indices) {
                    dot += lower[i][k] * lower[j][k]
                }
                assertEquals(target[i][j], dot, 1e-9)
            }
        }
    }

    @Test
    fun `cholesky rejects non positive definite matrix`() {
        val invalid = arrayOf(
            doubleArrayOf(1.0, 0.9, 0.9),
            doubleArrayOf(0.9, 1.0, -0.9),
            doubleArrayOf(0.9, -0.9, 1.0)
        )

        assertThrows(IllegalArgumentException::class.java) {
            CholeskyDecomposition.decompose(invalid)
        }
    }

    @Test
    fun `empirical correlation across 10000 ticks approaches target correlation matrix`() {
        val targetMatrix = arrayOf(
            doubleArrayOf(1.00, 0.72, -0.35, 0.00),
            doubleArrayOf(0.72, 1.00, -0.25, 0.15),
            doubleArrayOf(-0.35, -0.25, 1.00, 0.10),
            doubleArrayOf(0.00, 0.15, 0.10, 1.00)
        )

        val assets = listOf(
            createTestAsset(symbol = "A1", category = AssetCategory.STOCK, initialPrice = 5_000.0, vol = 0.28),
            createTestAsset(symbol = "A2", category = AssetCategory.INDEX, initialPrice = 6_800.0, vol = 0.18),
            createTestAsset(symbol = "A3", category = AssetCategory.COMMODITY, initialPrice = 1_350_000.0, vol = 0.15),
            createTestAsset(
                symbol = "A4",
                category = AssetCategory.FOREX,
                initialPrice = 15_500.0,
                vol = 0.11,
                priceModel = PriceModelType.MEAN_REVERSION_OU,
                meanReversionParams = MeanReversionParams(reversionSpeedPerYear = 2.0, longTermMeanPrice = 15_500.0)
            )
        )
        val catalog = MarketCatalog(schemaVersion = 1, assets = assets, correlationMatrix = targetMatrix)

        val ticks = 10_000
        var state = MarketEngine.createMultiAssetInitialState(catalog, seed = 20_260_505L, initialCandles = 0)
        val returnsByAsset = Array(assets.size) { DoubleArray(ticks) }

        for (t in 0 until ticks) {
            val prevPrices = DoubleArray(assets.size) { idx -> state.assetStates[idx].currentPrice }
            state = MarketEngine.stepAll(state)
            for (idx in assets.indices) {
                returnsByAsset[idx][t] = ln(state.assetStates[idx].currentPrice / prevPrices[idx])
            }
        }

        for (i in assets.indices) {
            for (j in i + 1 until assets.size) {
                val empiricalCorr = pearsonCorrelation(returnsByAsset[i], returnsByAsset[j])
                val error = abs(empiricalCorr - targetMatrix[i][j])
                assertTrue(
                    "Empirical correlation[$i][$j] ($empiricalCorr) should be within 0.04 of ${targetMatrix[i][j]}",
                    error < 0.04
                )
            }
        }
    }

    @Test
    fun `ornstein uhlenbeck model pulls price toward long term mean`() {
        val longTermMean = 15_500.0
        val highStartSpec = createTestAsset(
            symbol = "FXOU_H",
            category = AssetCategory.FOREX,
            initialPrice = longTermMean * 1.5,
            vol = 0.06,
            priceModel = PriceModelType.MEAN_REVERSION_OU,
            meanReversionParams = MeanReversionParams(reversionSpeedPerYear = 8.0, longTermMeanPrice = longTermMean)
        )
        val lowStartSpec = highStartSpec.copy(symbol = "FXOU_L", initialPrice = longTermMean * 0.65)

        val highEnd = MarketEngine.stepMany(
            state = MarketEngine.createInitialState(highStartSpec, seed = 42_001L, initialCandles = 0),
            ticks = 1_500
        )
        val lowEnd = MarketEngine.stepMany(
            state = MarketEngine.createInitialState(lowStartSpec, seed = 42_002L, initialCandles = 0),
            ticks = 1_500
        )

        assertTrue(
            "Price starting 50% above mean should revert closer to mean (got ${highEnd.currentPrice})",
            highEnd.currentPrice < highStartSpec.initialPrice * 0.88
        )
        assertTrue(
            "Price starting 35% below mean should revert closer to mean (got ${lowEnd.currentPrice})",
            lowEnd.currentPrice > lowStartSpec.initialPrice * 1.15
        )
        assertTrue(highEnd.currentPrice > 0.0 && lowEnd.currentPrice > 0.0)
    }

    @Test
    fun `jump diffusion produces occasional larger tail moves while keeping prices positive`() {
        val jumpSpec = createTestAsset(
            symbol = "JMP1",
            category = AssetCategory.CRYPTO,
            initialPrice = 18_400.0,
            vol = 0.45,
            jumpParams = JumpDiffusionParams(
                jumpIntensityPerYear = 80.0,
                jumpMeanLogSize = -0.02,
                jumpStdDevLogSize = 0.08
            )
        )

        var state = MarketEngine.createInitialState(jumpSpec, seed = 88_123L, initialCandles = 0)
        var maxAbsLogReturn = 0.0
        repeat(4_000) {
            val prev = state.currentPrice
            state = MarketEngine.step(state)
            assertTrue(state.currentPrice > 0.0)
            val absRet = abs(ln(state.currentPrice / prev))
            maxAbsLogReturn = max(maxAbsLogReturn, absRet)
        }

        assertTrue(
            "Jump diffusion should exhibit tail move > 4% over 4000 ticks (max was $maxAbsLogReturn)",
            maxAbsLogReturn > 0.04
        )
    }

    private fun pearsonCorrelation(x: DoubleArray, y: DoubleArray): Double {
        val meanX = x.average()
        val meanY = y.average()
        var cov = 0.0
        var varX = 0.0
        var varY = 0.0
        for (i in x.indices) {
            val dx = x[i] - meanX
            val dy = y[i] - meanY
            cov += dx * dy
            varX += dx * dx
            varY += dy * dy
        }
        return cov / sqrt(varX * varY)
    }

    private fun createTestAsset(
        symbol: String,
        category: AssetCategory,
        initialPrice: Double,
        vol: Double,
        priceModel: PriceModelType = PriceModelType.GBM,
        meanReversionParams: MeanReversionParams? = null,
        jumpParams: JumpDiffusionParams? = null
    ): AssetSpec = AssetSpec(
        symbol = symbol,
        name = "Test $symbol",
        sector = "Testing",
        category = category,
        initialPrice = initialPrice,
        annualVolatility = vol,
        annualDrift = 0.05,
        liquidityTier = LiquidityTier.HIGH,
        baseVolumePerTick = 10_000L,
        priceModel = priceModel,
        meanReversionParams = meanReversionParams,
        jumpParams = jumpParams
    )
}
