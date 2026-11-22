package com.tradinginaja.data.repository

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.domain.engine.MarketEngine
import com.tradinginaja.domain.engine.MultiAssetMarketState
import com.tradinginaja.domain.model.EventCatalog
import com.tradinginaja.domain.model.MarketCatalog
import com.tradinginaja.domain.model.SimulationSpeed
import com.tradinginaja.domain.repository.EventCatalogRepository
import com.tradinginaja.domain.repository.MarketCatalogRepository
import com.tradinginaja.domain.repository.MarketSimulationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class MarketSimulationRepositoryImpl @Inject constructor(
    private val catalogRepository: MarketCatalogRepository,
    private val eventCatalogRepository: EventCatalogRepository
) : MarketSimulationRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateMutex = Mutex()
    private var cachedCatalog: MarketCatalog? = null
    private var cachedEventCatalog: EventCatalog? = null
    private var tickerJob: Job? = null

    private val _marketState = MutableStateFlow<MultiAssetMarketState?>(null)
    override val marketState: StateFlow<MultiAssetMarketState?> = _marketState.asStateFlow()

    private val _simulationSpeed = MutableStateFlow(SimulationSpeed.PAUSED)
    override val simulationSpeed: StateFlow<SimulationSpeed> = _simulationSpeed.asStateFlow()

    override suspend fun ensureInitialized() {
        if (_marketState.value != null) return
        stateMutex.withLock {
            if (_marketState.value != null) return
            val catalog = catalogRepository.getCatalog()
            val eventCatalog = eventCatalogRepository.getEventCatalog()
            cachedCatalog = catalog
            cachedEventCatalog = eventCatalog
            _marketState.value = createInitialState(catalog, eventCatalog)
        }
    }

    override fun selectSpeed(speed: SimulationSpeed) {
        _simulationSpeed.value = speed
        tickerJob?.cancel()
        tickerJob = null

        if (speed != SimulationSpeed.PAUSED) {
            tickerJob = scope.launch {
                ensureInitialized()
                while (isActive) {
                    delay(speed.intervalMillis)
                    stepInternal()
                }
            }
        }
    }

    override fun stepOnce() {
        scope.launch {
            ensureInitialized()
            stepInternal()
        }
    }

    override fun resetSimulation() {
        _simulationSpeed.value = SimulationSpeed.PAUSED
        tickerJob?.cancel()
        tickerJob = null

        scope.launch {
            stateMutex.withLock {
                val catalog = cachedCatalog ?: catalogRepository.getCatalog().also { cachedCatalog = it }
                val eventCatalog = cachedEventCatalog
                    ?: eventCatalogRepository.getEventCatalog().also { cachedEventCatalog = it }
                _marketState.value = createInitialState(catalog, eventCatalog)
            }
        }
    }

    private suspend fun stepInternal() {
        stateMutex.withLock {
            val current = _marketState.value ?: return
            _marketState.value = MarketEngine.stepAll(current)
        }
    }

    private fun createInitialState(catalog: MarketCatalog, eventCatalog: EventCatalog): MultiAssetMarketState =
        MarketEngine.createMultiAssetInitialState(
            catalog = catalog,
            seed = GameBalance.DEFAULT_SIMULATION_SEED,
            initialCandles = GameBalance.DEFAULT_INITIAL_HISTORY_CANDLES,
            eventCatalog = eventCatalog,
            enableRegimeSwitching = true
        )
}
