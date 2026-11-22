package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.domain.model.AssetCategory
import com.tradinginaja.domain.model.AssetSpec
import com.tradinginaja.domain.model.MarketRegime
import com.tradinginaja.domain.model.RegimeTransitionMatrix
import kotlin.math.abs

data class RegimeAssetModifier(
    val driftDelta: Double,
    val volatilityMultiplier: Double,
    val jumpIntensityMultiplier: Double
)

/**
 * Mesin transisi rezim pasar berbasis rantai Markov 4 keadaan (Bull/Bear/Sideways/Crisis).
 * Pure Kotlin, murni deterministik.
 */
object RegimeEngine {

    private const val STATIONARY_MAX_ITERATIONS = 500
    private const val STATIONARY_CONVERGENCE_EPS = 1e-11
    private const val UNIFORM_UPPER_GUARD = 1.0

    fun nextRegime(
        current: MarketRegime,
        uniformSample: Double,
        matrix: RegimeTransitionMatrix = RegimeTransitionMatrix.default()
    ): MarketRegime {
        val clampedU = uniformSample.coerceIn(0.0, UNIFORM_UPPER_GUARD)
        val row = matrix.rowFor(current)
        var cumulative = 0.0
        var chosen = MarketRegime.entries.last()
        for (index in MarketRegime.entries.indices) {
            cumulative += row[index]
            if (clampedU < cumulative) {
                chosen = MarketRegime.entries[index]
                break
            }
        }
        return chosen
    }

    fun effectiveModifier(regime: MarketRegime, spec: AssetSpec): RegimeAssetModifier {
        val modifier = when {
            spec.isSafeHaven -> safeHavenModifier(regime)
            spec.category == AssetCategory.FOREX -> forexModifier(regime)
            else -> standardRiskModifier(regime)
        }
        return modifier
    }

    /**
     * Menghitung distribusi stasioner teoretis (pi * P = pi) dengan iterasi pangkat.
     */
    fun stationaryDistribution(matrix: RegimeTransitionMatrix): DoubleArray {
        val size = MarketRegime.entries.size
        var distribution = DoubleArray(size) { 1.0 / size.toDouble() }
        repeat(STATIONARY_MAX_ITERATIONS) {
            val next = DoubleArray(size)
            for (fromIdx in 0 until size) {
                val weight = distribution[fromIdx]
                val row = matrix.matrix[fromIdx]
                for (toIdx in 0 until size) {
                    next[toIdx] += weight * row[toIdx]
                }
            }
            var maxDiff = 0.0
            for (idx in 0 until size) {
                val diff = abs(next[idx] - distribution[idx])
                if (diff > maxDiff) maxDiff = diff
            }
            distribution = next
            if (maxDiff < STATIONARY_CONVERGENCE_EPS) {
                return normalizeVector(distribution)
            }
        }
        return normalizeVector(distribution)
    }

    private fun standardRiskModifier(regime: MarketRegime): RegimeAssetModifier = when (regime) {
        MarketRegime.BULL -> RegimeAssetModifier(
            driftDelta = GameBalance.REGIME_BULL_DRIFT_DELTA,
            volatilityMultiplier = GameBalance.REGIME_BULL_VOL_MULT,
            jumpIntensityMultiplier = GameBalance.REGIME_BULL_JUMP_MULT
        )
        MarketRegime.SIDEWAYS -> RegimeAssetModifier(
            driftDelta = GameBalance.REGIME_SIDEWAYS_DRIFT_DELTA,
            volatilityMultiplier = GameBalance.REGIME_SIDEWAYS_VOL_MULT,
            jumpIntensityMultiplier = GameBalance.REGIME_SIDEWAYS_JUMP_MULT
        )
        MarketRegime.BEAR -> RegimeAssetModifier(
            driftDelta = GameBalance.REGIME_BEAR_DRIFT_DELTA,
            volatilityMultiplier = GameBalance.REGIME_BEAR_VOL_MULT,
            jumpIntensityMultiplier = GameBalance.REGIME_BEAR_JUMP_MULT
        )
        MarketRegime.CRISIS -> RegimeAssetModifier(
            driftDelta = GameBalance.REGIME_CRISIS_DRIFT_DELTA,
            volatilityMultiplier = GameBalance.REGIME_CRISIS_VOL_MULT,
            jumpIntensityMultiplier = GameBalance.REGIME_CRISIS_JUMP_MULT
        )
    }

    private fun safeHavenModifier(regime: MarketRegime): RegimeAssetModifier = when (regime) {
        MarketRegime.BULL -> RegimeAssetModifier(
            driftDelta = GameBalance.SAFE_HAVEN_BULL_DRIFT_DELTA,
            volatilityMultiplier = GameBalance.REGIME_BULL_VOL_MULT,
            jumpIntensityMultiplier = GameBalance.REGIME_BULL_JUMP_MULT
        )
        MarketRegime.SIDEWAYS -> RegimeAssetModifier(
            driftDelta = 0.0,
            volatilityMultiplier = GameBalance.REGIME_SIDEWAYS_VOL_MULT,
            jumpIntensityMultiplier = GameBalance.REGIME_SIDEWAYS_JUMP_MULT
        )
        MarketRegime.BEAR -> RegimeAssetModifier(
            driftDelta = GameBalance.SAFE_HAVEN_BEAR_DRIFT_DELTA,
            volatilityMultiplier = 1.0,
            jumpIntensityMultiplier = 1.0
        )
        MarketRegime.CRISIS -> RegimeAssetModifier(
            driftDelta = GameBalance.SAFE_HAVEN_CRISIS_DRIFT_DELTA,
            volatilityMultiplier = GameBalance.SAFE_HAVEN_CRISIS_VOL_MULT,
            jumpIntensityMultiplier = 1.0
        )
    }

    private fun forexModifier(regime: MarketRegime): RegimeAssetModifier {
        val base = standardRiskModifier(regime)
        val volMult = if (regime == MarketRegime.CRISIS) {
            GameBalance.FOREX_CRISIS_VOL_MULT
        } else {
            base.volatilityMultiplier
        }
        return RegimeAssetModifier(
            driftDelta = base.driftDelta * GameBalance.FOREX_REGIME_DRIFT_SCALE,
            volatilityMultiplier = volMult,
            jumpIntensityMultiplier = 1.0
        )
    }

    private fun normalizeVector(vector: DoubleArray): DoubleArray {
        val total = vector.sum()
        return if (total > 0.0) DoubleArray(vector.size) { vector[it] / total } else vector
    }
}
