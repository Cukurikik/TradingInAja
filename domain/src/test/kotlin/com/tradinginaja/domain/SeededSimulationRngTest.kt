package com.tradinginaja.domain

import com.tradinginaja.domain.model.SeededSimulationRng
import org.junit.Assert.assertEquals
import org.junit.Test

class SeededSimulationRngTest {

    @Test
    fun `same seed produces identical deterministic sequence`() {
        val rngA = SeededSimulationRng(seed = 42_026L)
        val rngB = SeededSimulationRng(seed = 42_026L)

        repeat(10) {
            assertEquals(rngA.nextDouble(), rngB.nextDouble(), 0.0)
            assertEquals(rngA.nextGaussianApprox(), rngB.nextGaussianApprox(), 0.0)
        }
    }
}
