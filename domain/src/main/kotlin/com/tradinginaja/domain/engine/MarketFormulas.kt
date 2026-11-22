package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.domain.model.JumpDiffusionParams
import com.tradinginaja.domain.model.LiquidityTier
import com.tradinginaja.domain.model.MeanReversionParams
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Kumpulan formula kuantitatif murni untuk simulasi harga GBM, Ornstein-Uhlenbeck (Mean Reversion),
 * Jump Diffusion, wick intrabar, volume sintetis, dan spread bid-ask.
 */
object MarketFormulas {

    private const val HALF = 0.5
    private const val TWO = 2.0
    private const val MAX_LOG_RETURN_BOUND = 0.35
    private const val WICK_VOLATILITY_SCALE = 0.55
    private const val VOLUME_SENSITIVITY = 1.6
    private const val VOLUME_NOISE_BASE = 0.7
    private const val VOLUME_NOISE_SPAN = 0.6
    private const val MAX_SHOCK_RATIO = 4.0
    private const val MIN_VOLUME = 1L

    /**
     * Langkah Geometric Brownian Motion (GBM) diskrit:
     * S_{t+dt} = S_t * exp((mu - 0.5 * sigma^2) * dt + sigma * sqrt(dt) * Z)
     *
     * Dijaga agar harga selalu >= [GameBalance.MIN_ASSET_PRICE_DOUBLE] (> 0).
     */
    fun nextGbmClosePrice(
        previousPrice: Double,
        annualDrift: Double,
        annualVolatility: Double,
        standardNormalShock: Double,
        dt: Double = 1.0 / GameBalance.TICKS_PER_TRADING_YEAR
    ): Double {
        val safePrev = max(previousPrice, GameBalance.MIN_ASSET_PRICE_DOUBLE)
        val driftTerm = (annualDrift - HALF * annualVolatility * annualVolatility) * dt
        val diffusionTerm = annualVolatility * sqrt(dt) * standardNormalShock
        val rawLogReturn = driftTerm + diffusionTerm
        val boundedLogReturn = rawLogReturn.coerceIn(-MAX_LOG_RETURN_BOUND, MAX_LOG_RETURN_BOUND)
        val nextPrice = safePrev * exp(boundedLogReturn)
        return max(nextPrice, GameBalance.MIN_ASSET_PRICE_DOUBLE)
    }

    /**
     * Langkah Ornstein-Uhlenbeck (Mean Reversion) pada log-harga X_t = ln(S_t):
     * X_{t+dt} = X_t * e^{-kappa*dt} + theta * (1 - e^{-kappa*dt})
     *            + sigma * sqrt((1 - e^{-2*kappa*dt}) / (2*kappa)) * Z
     *
     * Bekerja di ruang log agar harga hasil S_{t+dt} = exp(X_{t+dt}) selalu > 0.
     */
    fun nextOrnsteinUhlenbeckClosePrice(
        previousPrice: Double,
        annualVolatility: Double,
        params: MeanReversionParams,
        standardNormalShock: Double,
        dt: Double = 1.0 / GameBalance.TICKS_PER_TRADING_YEAR
    ): Double {
        val safePrev = max(previousPrice, GameBalance.MIN_ASSET_PRICE_DOUBLE)
        val safeMean = max(params.longTermMeanPrice, GameBalance.MIN_ASSET_PRICE_DOUBLE)
        val kappa = max(params.reversionSpeedPerYear, GameBalance.MIN_ASSET_PRICE_DOUBLE)

        val currentLog = ln(safePrev)
        val targetLog = ln(safeMean)
        val decay = exp(-kappa * dt)
        val ouStd = annualVolatility * sqrt((1.0 - exp(-TWO * kappa * dt)) / (TWO * kappa))

        val rawNextLog = currentLog * decay + targetLog * (1.0 - decay) + ouStd * standardNormalShock
        val logReturn = (rawNextLog - currentLog).coerceIn(-MAX_LOG_RETURN_BOUND, MAX_LOG_RETURN_BOUND)
        val nextPrice = safePrev * exp(logReturn)
        return max(nextPrice, GameBalance.MIN_ASSET_PRICE_DOUBLE)
    }

    /**
     * Menambahkan komponen Merton Jump Diffusion di atas return dasar:
     * kompensasi drift -lambda * E[e^J - 1] * dt + lompatan acak dengan probabilitas 1 - exp(-lambda * dt).
     */
    fun applyJumpDiffusion(
        previousPrice: Double,
        baseClosePrice: Double,
        jumpParams: JumpDiffusionParams,
        jumpTriggerUniform: Double,
        jumpSizeNormal: Double,
        dt: Double = 1.0 / GameBalance.TICKS_PER_TRADING_YEAR
    ): Double {
        val safePrev = max(previousPrice, GameBalance.MIN_ASSET_PRICE_DOUBLE)
        val safeBase = max(baseClosePrice, GameBalance.MIN_ASSET_PRICE_DOUBLE)
        val baseLogReturn = ln(safeBase / safePrev)

        val lambda = max(jumpParams.jumpIntensityPerYear, 0.0)
        val expectedJumpMultiplier = exp(
            jumpParams.jumpMeanLogSize + HALF * jumpParams.jumpStdDevLogSize * jumpParams.jumpStdDevLogSize
        ) - 1.0
        val driftCompensation = -lambda * expectedJumpMultiplier * dt

        val jumpProbability = 1.0 - exp(-lambda * dt)
        val jumpLogShock = if (jumpTriggerUniform < jumpProbability) {
            jumpParams.jumpMeanLogSize + jumpParams.jumpStdDevLogSize * jumpSizeNormal
        } else {
            0.0
        }

        val totalLogReturn = (baseLogReturn + driftCompensation + jumpLogShock)
            .coerceIn(-MAX_LOG_RETURN_BOUND, MAX_LOG_RETURN_BOUND)
        return max(safePrev * exp(totalLogReturn), GameBalance.MIN_ASSET_PRICE_DOUBLE)
    }

    /**
     * Menghitung harga high dan low sintetis pada satu tick candle agar wick terlihat alami
     * dan tetap mematuhi invarian: 0 < low <= min(open, close) <= max(open, close) <= high.
     */
    fun computeIntrabarHighLow(
        open: Double,
        close: Double,
        annualVolatility: Double,
        upperUniform: Double,
        lowerUniform: Double,
        dt: Double = 1.0 / GameBalance.TICKS_PER_TRADING_YEAR
    ): Pair<Double, Double> {
        val bodyHigh = max(open, close)
        val bodyLow = min(open, close)
        val stepSigma = annualVolatility * sqrt(dt) * WICK_VOLATILITY_SCALE
        val high = bodyHigh * (1.0 + stepSigma * upperUniform)
        val rawLow = bodyLow * (1.0 - stepSigma * lowerUniform)
        val low = max(min(rawLow, bodyLow), GameBalance.MIN_ASSET_PRICE_DOUBLE)
        return high to low
    }

    /**
     * Menghitung volume sintetis per tick sebagai fungsi dari besar lonjakan return
     * dan faktor likuiditas instrumen.
     */
    fun computeSyntheticVolume(
        baseVolume: Long,
        liquidityTier: LiquidityTier,
        open: Double,
        close: Double,
        annualVolatility: Double,
        uniformNoise: Double,
        dt: Double = 1.0 / GameBalance.TICKS_PER_TRADING_YEAR
    ): Long {
        val safeOpen = max(open, GameBalance.MIN_ASSET_PRICE_DOUBLE)
        val absReturn = abs(close - safeOpen) / safeOpen
        val expectedStepVol = max(annualVolatility * sqrt(dt), GameBalance.MIN_ASSET_PRICE_DOUBLE)
        val shockRatio = (absReturn / expectedStepVol).coerceAtMost(MAX_SHOCK_RATIO)
        val activityFactor = 1.0 + VOLUME_SENSITIVITY * shockRatio
        val noiseFactor = VOLUME_NOISE_BASE + VOLUME_NOISE_SPAN * uniformNoise
        val rawVolume = baseVolume.toDouble() *
            liquidityTier.baseVolumeMultiplier *
            activityFactor *
            noiseFactor
        return max(rawVolume.roundToLong(), MIN_VOLUME)
    }

    /**
     * Menghitung harga Bid dan Ask dari harga tengah (midPrice) berdasarkan tier likuiditas.
     */
    fun computeBidAsk(midPrice: Double, liquidityTier: LiquidityTier): Pair<Double, Double> {
        val safeMid = max(midPrice, GameBalance.MIN_ASSET_PRICE_DOUBLE)
        val halfSpreadRatio = (liquidityTier.baseSpreadBps.toDouble() / GameBalance.BASIS_POINTS_DIVISOR) * HALF
        val bid = max(safeMid * (1.0 - halfSpreadRatio), GameBalance.MIN_ASSET_PRICE_DOUBLE)
        val ask = max(safeMid * (1.0 + halfSpreadRatio), bid)
        return bid to ask
    }
}
