package com.tradinginaja.feature.trade

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.domain.engine.CholeskyDecomposition
import com.tradinginaja.domain.engine.MarketEngine
import com.tradinginaja.domain.engine.MultiAssetMarketState
import com.tradinginaja.domain.engine.OrderEngine
import com.tradinginaja.domain.engine.OrderFormulas
import com.tradinginaja.domain.model.AssetCategory
import com.tradinginaja.domain.model.AssetSpec
import com.tradinginaja.domain.model.LiquidityTier
import com.tradinginaja.domain.model.MarketCatalog
import com.tradinginaja.domain.model.Order
import com.tradinginaja.domain.model.OrderRejectReason
import com.tradinginaja.domain.model.OrderRequest
import com.tradinginaja.domain.model.OrderStatus
import com.tradinginaja.domain.model.OrderSubmissionResult
import com.tradinginaja.domain.model.PortfolioState
import com.tradinginaja.domain.model.SimulationSpeed
import com.tradinginaja.domain.repository.MarketSimulationRepository
import com.tradinginaja.domain.repository.PortfolioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FakeMarketSimulationRepository(private val scope: CoroutineScope) : MarketSimulationRepository {

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
            )
        ),
        correlationMatrix = CholeskyDecomposition.identityMatrix(2)
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
        initialCandles = GameBalance.DEFAULT_INITIAL_HISTORY_CANDLES
    )
}

class FakePortfolioRepository(private val simulationRepository: MarketSimulationRepository) : PortfolioRepository {

    private val _portfolioState = MutableStateFlow<PortfolioState?>(OrderEngine.createInitialPortfolio())
    override val portfolioState: StateFlow<PortfolioState?> = _portfolioState.asStateFlow()

    override suspend fun ensureInitialized() {
        if (_portfolioState.value == null) {
            _portfolioState.value = OrderEngine.createInitialPortfolio()
        }
    }

    override suspend fun submitOrder(request: OrderRequest): OrderSubmissionResult {
        val current = _portfolioState.value ?: OrderEngine.createInitialPortfolio()
        val market = simulationRepository.marketState.value
        val asset = market?.findAsset(request.symbol)
        if (market == null || asset == null) {
            val rejected = Order(
                orderId = request.orderId,
                symbol = request.symbol,
                side = request.side,
                type = request.type,
                quantity = request.quantity,
                limitPrice = request.limitPrice,
                reservedCash = com.tradinginaja.core.common.Money.ZERO,
                status = OrderStatus.REJECTED,
                createdAtTick = market?.tickIndex ?: 0L,
                createdAtEpochMillis = request.createdAtEpochMillis,
                updatedAtTick = market?.tickIndex ?: 0L,
                rejectReason = OrderRejectReason.UNKNOWN_ASSET
            )
            return OrderSubmissionResult.Rejected(rejected, OrderRejectReason.UNKNOWN_ASSET)
        }
        val quote = OrderFormulas.quoteFromAssetState(asset)
        val (nextState, result) = OrderEngine.submitOrder(current, request, quote, market.tickIndex)
        _portfolioState.value = nextState
        return result
    }

    override suspend fun cancelOrder(orderId: String) {
        val current = _portfolioState.value ?: return
        val tickIndex = simulationRepository.marketState.value?.tickIndex ?: 0L
        _portfolioState.value = OrderEngine.cancelOrder(current, orderId, tickIndex)
    }

    override suspend fun resetPortfolio() {
        val tickIndex = simulationRepository.marketState.value?.tickIndex ?: 0L
        _portfolioState.value = OrderEngine.createInitialPortfolio(initialTickIndex = tickIndex)
    }

    override suspend fun setCareerLevel(level: com.tradinginaja.domain.model.CareerLevel) {
        _portfolioState.value = _portfolioState.value?.copy(careerLevel = level)
    }
}
