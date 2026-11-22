package com.tradinginaja.domain.model

import kotlin.random.Random

/**
 * Kontrak RNG deterministik untuk mesin simulasi pasar pure Kotlin.
 * Memastikan hasil simulasi dapat direproduksi dari seed yang sama.
 */
interface SimulationRng {
    fun nextDouble(): Double
    fun nextGaussianApprox(): Double
}

/**
 * Implementasi deterministik berbasis [Random] dengan seed eksplisit.
 */
class SeededSimulationRng(seed: Long) : SimulationRng {
    private val random: Random = Random(seed)

    override fun nextDouble(): Double = random.nextDouble()

    /**
     * Pendekatan distribusi normal standar via Irwin-Hall (rata-rata 12 sampel seragam).
     * Bebas java.util.Random agar 100% portabel dan deterministik di Kotlin.
     */
    override fun nextGaussianApprox(): Double {
        var sum = 0.0
        repeat(IRWIN_HALL_SAMPLES) {
            sum += random.nextDouble()
        }
        return sum - IRWIN_HALL_MEAN
    }

    private companion object {
        const val IRWIN_HALL_SAMPLES = 12
        const val IRWIN_HALL_MEAN = 6.0
    }
}
