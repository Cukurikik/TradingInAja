package com.tradinginaja.feature.market

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.domain.engine.CholeskyDecomposition
import com.tradinginaja.domain.engine.MarketEngine
import com.tradinginaja.domain.engine.MultiAssetMarketState
import com.tradinginaja.domain.model.AssetCategory
import com.tradinginaja.domain.model.AssetSpec
import com.tradinginaja.domain.model.EventCatalog
import com.tradinginaja.domain.model.LiquidityTier
import com.tradinginaja.domain.model.MarketCatalog
import com.tradinginaja.domain.model.MeanReversionParams
import com.tradinginaja.domain.model.PriceModelType
import com.tradinginaja.domain.model.SimulationSpeed
import com.tradinginaja.domain.repository.MarketSimulationRepository
import com.tradinginaja.domain.repository.WatchlistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FakeMarketSimulationRepository(
    private val scope: CoroutineScope,
    private val eventCatalog: EventCatalog = EventCatalog.EMPTY,
    private val enableRegimeSwitching: Boolean = false,
    private val initialCandles: Int = GameBalance.DEFAULT_INITIAL_HISTORY_CANDLES
) : MarketSimulationRepository {

    private val catalog = MarketCatalog(
        schemaVersion = 1,
        assets = listOf(
            AssetSpec(
                symbol = "NSKP",
                name = "Nusantara Komputasi Tbk",
                sector = "Teknologi",
                category = AssetCategory.STOCK,
                initialPrice = 4_500.0,
                annualVolatility = 0.38,
                annualDrift = 0.11,
                liquidityTier = LiquidityTier.HIGH,
                baseVolumePerTick = 14_500L
            ),
            AssetSpec(
                symbol = "GRDC",
                name = "GarudaChain",
                sector = "Layer-1 Fiktif",
                category = AssetCategory.CRYPTO,
                initialPrice = 125_000.0,
                annualVolatility = 0.78,
                annualDrift = 0.14,
                liquidityTier = LiquidityTier.HIGH,
                baseVolumePerTick = 18_500L
            ),
            AssetSpec(
                symbol = "VUD/VRP",
                name = "Dolar Aether / vRupiah",
                sector = "Pasangan Mata Uang Fiktif",
                category = AssetCategory.FOREX,
                initialPrice = 15_500.0,
                annualVolatility = 0.11,
                annualDrift = 0.0,
                liquidityTier = LiquidityTier.VERY_HIGH,
                baseVolumePerTick = 38_000L,
                priceModel = PriceModelType.MEAN_REVERSION_OU,
                meanReversionParams = MeanReversionParams(
                    reversionSpeedPerYear = 2.8,
                    longTermMeanPrice = 15_500.0
                )
            )
        ),
        correlationMatrix = CholeskyDecomposition.identityMatrix(3)
    )

    private var tickerJob: Job? = null

    private val _marketState = MutableStateFlow<MultiAssetMarketState?>(createInitial())
    override val marketState: StateFlow<MultiAssetMarketState?> = _marketState.asStateFlow()

    private val _simulationSpeed = MutableStateFlow(SimulationSpeed.PAUSED)
    override val simulationSpeed: StateFlow<SimulationSpeed> = _simulationSpeed.asStateFlow()

    override suspend fun ensureInitialized() {
        if (_marketState.value == null) {
            _marketState.value = createInitial()
        }
    }

    override fun selectSpeed(speed: SimulationSpeed) {
        _simulationSpeed.value = speed
        tickerJob?.cancel()
        tickerJob = null
        if (speed != SimulationSpeed.PAUSED) {
            tickerJob = scope.launch {
                while (isActive) {
                    delay(speed.intervalMillis)
                    _marketState.value = _marketState.value?.let { MarketEngine.stepAll(it) }
                }
            }
        }
    }

    override fun stepOnce() {
        _marketState.value = _marketState.value?.let { MarketEngine.stepAll(it) }
    }

    override fun resetSimulation() {
        _simulationSpeed.value = SimulationSpeed.PAUSED
        tickerJob?.cancel()
        tickerJob = null
        _marketState.value = createInitial()
    }

    private fun createInitial(): MultiAssetMarketState = MarketEngine.createMultiAssetInitialState(
        catalog = catalog,
        seed = GameBalance.DEFAULT_SIMULATION_SEED,
        initialCandles = initialCandles,
        eventCatalog = eventCatalog,
        enableRegimeSwitching = enableRegimeSwitching
    )
}

class FakeWatchlistRepository : WatchlistRepository {
    private val symbolsFlow = MutableStateFlow<Set<String>>(emptySet())
    override val watchlistSymbols: Flow<Set<String>> = symbolsFlow

    override suspend fun toggleWatchlist(symbol: String) {
        val current = symbolsFlow.value
        symbolsFlow.value = if (current.contains(symbol)) current - symbol else current + symbol
    }
}
