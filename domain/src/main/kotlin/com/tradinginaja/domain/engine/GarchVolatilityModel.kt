package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.domain.model.GarchParams
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Model volatilitas kondisional GARCH(1,1) sederhana untuk fenomena volatility clustering:
 * h_t = omegaWeight * h_bar + alpha * epsilon_{t-1}^2 + beta * h_{t-1}
 * di mana h_t adalah varians per-tick dan dikonversi kembali ke volatilitas tahunan.
 */
object GarchVolatilityModel {

    private const val MAX_PERSISTENCE_SUM = 0.999
    private const val ZERO = 0.0

    fun nextAnnualVolatility(
        baseAnnualVolatility: Double,
        previousAnnualVolatility: Double,
        previousStepInnovation: Double,
        params: GarchParams,
        dt: Double = 1.0 / GameBalance.TICKS_PER_TRADING_YEAR
    ): Double {
        val safeBase = max(baseAnnualVolatility, GameBalance.MIN_EFFECTIVE_VOLATILITY)
        val safePrev = max(previousAnnualVolatility, GameBalance.MIN_EFFECTIVE_VOLATILITY)
        val safeDt = max(dt, GameBalance.MIN_ASSET_PRICE_DOUBLE * GameBalance.MIN_ASSET_PRICE_DOUBLE)

        val alpha = params.alpha.coerceAtLeast(ZERO)
        val beta = params.beta.coerceAtLeast(ZERO)
        val persistence = (alpha + beta).coerceAtMost(MAX_PERSISTENCE_SUM)
        val scaleDown = if (alpha + beta > persistence && (alpha + beta) > ZERO) {
            persistence / (alpha + beta)
        } else {
            1.0
        }
        val adjAlpha = alpha * scaleDown
        val adjBeta = beta * scaleDown
        val adjOmega = max(params.omegaWeight, 1.0 - adjAlpha - adjBeta).coerceAtLeast(ZERO)

        val baseTickVariance = safeBase * safeBase * safeDt
        val prevTickVariance = safePrev * safePrev * safeDt
        val squaredShock = previousStepInnovation * previousStepInnovation

        val nextTickVariance = adjOmega * baseTickVariance +
            adjAlpha * squaredShock +
            adjBeta * prevTickVariance

        val rawAnnualVol = sqrt(max(nextTickVariance / safeDt, ZERO))
        val minBound = max(safeBase * GameBalance.GARCH_MIN_VOL_RATIO, GameBalance.MIN_EFFECTIVE_VOLATILITY)
        val maxBound = minOf(safeBase * GameBalance.GARCH_MAX_VOL_RATIO, GameBalance.MAX_EFFECTIVE_VOLATILITY)
        return rawAnnualVol.coerceIn(minBound, maxOf(minBound, maxBound))
    }
}
