package com.tradinginaja.domain.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Hasil satu pengambilan angka acak murni tanpa mutasi state eksternal.
 */
data class RngSample(val value: Double, val nextState: Long)

/**
 * PRNG deterministik pure Kotlin berbasis SplitMix64 (stateless/immutable state transition).
 * Menghasilkan nilai identik pada seluruh JVM/Android untuk pasangan (state) yang sama.
 */
object DeterministicRng {

    private const val GOLDEN_GAMMA: Long = -7046029254386353131L // 0x9E3779B97F4A7C15L
    private const val MIX_MUL_1: Long = -4658895280553007687L // 0xBF58476D1CE4E5B9L
    private const val MIX_MUL_2: Long = -7723592293110705685L // 0x94D049BB133111EBL
    private const val SHIFT_30 = 30
    private const val SHIFT_27 = 27
    private const val SHIFT_31 = 31
    private const val DOUBLE_MANTISSA_SHIFT = 11
    private const val DOUBLE_SCALE = 1.0 / (1L shl 53)
    private const val MIN_UNIFORM_POSITIVE = 1e-12
    private const val TWO = 2.0

    fun initialState(seed: Long, salt: Long = 0L): Long {
        val combined = seed xor (salt * GOLDEN_GAMMA)
        return mix64(combined)
    }

    /**
     * Menghasilkan bilangan seragam di rentang [0.0, 1.0) beserta state berikutnya.
     */
    fun nextUniform(state: Long): RngSample {
        val nextState = state + GOLDEN_GAMMA
        val z = mix64(nextState)
        val mantissa = (z ushr DOUBLE_MANTISSA_SHIFT).toDouble()
        val unit = mantissa * DOUBLE_SCALE
        return RngSample(value = unit, nextState = nextState)
    }

    /**
     * Menghasilkan variabel acak normal standar N(0, 1) memakai transformasi Box-Muller.
     */
    fun nextStandardNormal(state: Long): RngSample {
        val s1 = nextUniform(state)
        val s2 = nextUniform(s1.nextState)
        val u1 = max(s1.value, MIN_UNIFORM_POSITIVE)
        val u2 = s2.value
        val z0 = sqrt(-TWO * ln(u1)) * cos(TWO * PI * u2)
        return RngSample(value = z0, nextState = s2.nextState)
    }

    private fun mix64(input: Long): Long {
        var z = input
        z = (z xor (z ushr SHIFT_30)) * MIX_MUL_1
        z = (z xor (z ushr SHIFT_27)) * MIX_MUL_2
        return z xor (z ushr SHIFT_31)
    }
}
