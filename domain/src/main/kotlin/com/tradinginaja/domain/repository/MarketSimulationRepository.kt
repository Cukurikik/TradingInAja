package com.tradinginaja.domain.repository

import com.tradinginaja.domain.engine.MultiAssetMarketState
import com.tradinginaja.domain.model.SimulationSpeed
import kotlinx.coroutines.flow.StateFlow

/**
 * Sesi simulasi multi-aset aktif yang dibagi antara daftar pasar dan layar detail aset.
 */
interface MarketSimulationRepository {
    val marketState: StateFlow<MultiAssetMarketState?>
    val simulationSpeed: StateFlow<SimulationSpeed>

    suspend fun ensureInitialized()
    fun selectSpeed(speed: SimulationSpeed)
    fun stepOnce()
    fun resetSimulation()
}
