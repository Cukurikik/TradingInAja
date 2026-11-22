package com.tradinginaja.data.repository

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.core.common.Money
import com.tradinginaja.core.database.DatabaseTransactionRunner
import com.tradinginaja.core.database.dao.EquitySnapshotDao
import com.tradinginaja.core.database.dao.LiquidationEventDao
import com.tradinginaja.core.database.dao.OrderDao
import com.tradinginaja.core.database.dao.PositionDao
import com.tradinginaja.core.database.dao.TradeFillDao
import com.tradinginaja.core.database.dao.VirtualAccountDao
import com.tradinginaja.core.database.entity.VirtualAccountEntity
import com.tradinginaja.data.mapper.toDomain
import com.tradinginaja.data.mapper.toEntity
import com.tradinginaja.domain.engine.MultiAssetMarketState
import com.tradinginaja.domain.engine.OrderEngine
import com.tradinginaja.domain.engine.OrderFormulas
import com.tradinginaja.domain.engine.ProgressionEngine
import com.tradinginaja.domain.engine.RiskEngine
import com.tradinginaja.domain.model.CareerLevel
import com.tradinginaja.domain.model.LearningProgress
import com.tradinginaja.domain.model.MarketQuote
import com.tradinginaja.domain.model.Order
import com.tradinginaja.domain.model.OrderRejectReason
import com.tradinginaja.domain.model.OrderRequest
import com.tradinginaja.domain.model.OrderStatus
import com.tradinginaja.domain.model.OrderSubmissionResult
import com.tradinginaja.domain.model.PortfolioState
import com.tradinginaja.domain.repository.MarketSimulationRepository
import com.tradinginaja.domain.repository.PortfolioRepository
import com.tradinginaja.domain.repository.PreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Suppress("LongParameterList")
@Singleton
class PortfolioRepositoryImpl @Inject constructor(
    private val simulationRepository: MarketSimulationRepository,
    private val preferencesRepository: PreferencesRepository,
    private val accountDao: VirtualAccountDao,
    private val positionDao: PositionDao,
    private val orderDao: OrderDao,
    private val tradeFillDao: TradeFillDao,
    private val equitySnapshotDao: EquitySnapshotDao,
    private val liquidationEventDao: LiquidationEventDao,
    private val transactionRunner: DatabaseTransactionRunner
) : PortfolioRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val portfolioMutex = Mutex()
    private var lastProcessedTick: Long = -1L
    private var latestLearningProgress: LearningProgress = LearningProgress()

    private val _portfolioState = MutableStateFlow<PortfolioState?>(null)
    override val portfolioState: StateFlow<PortfolioState?> = _portfolioState.asStateFlow()

    init {
        scope.launch {
            simulationRepository.marketState.collectLatest { marketState ->
                if (marketState != null) {
                    onMarketTick(marketState)
                }
            }
        }
        scope.launch {
            preferencesRepository.learningProgress.collectLatest { progress ->
                latestLearningProgress = progress
                onLearningProgressChanged(progress)
            }
        }
    }

    override suspend fun ensureInitialized() {
        if (_portfolioState.value != null) return
        portfolioMutex.withLock {
            if (_portfolioState.value != null) return
            val loaded = loadOrSeedFromDatabase()
            _portfolioState.value = loaded
            lastProcessedTick = simulationRepository.marketState.value?.tickIndex
                ?: loaded.equityCurve.lastOrNull()?.tickIndex
                ?: 0L
        }
    }

    override suspend fun submitOrder(request: OrderRequest): OrderSubmissionResult {
        ensureInitialized()
        simulationRepository.ensureInitialized()

        return portfolioMutex.withLock {
            val current = _portfolioState.value ?: loadOrSeedFromDatabase()
            val marketState = simulationRepository.marketState.value
            val assetState = marketState?.findAsset(request.symbol)
            if (marketState == null || assetState == null) {
                val rejected = Order(
                    orderId = request.orderId,
                    symbol = request.symbol,
                    side = request.side,
                    type = request.type,
                    quantity = request.quantity,
                    limitPrice = request.limitPrice,
                    triggerPrice = request.triggerPrice,
                    leverage = request.leverage,
                    takeProfitPrice = request.takeProfitPrice,
                    stopLossPrice = request.stopLossPrice,
                    ocoGroupId = request.ocoGroupId,
                    reservedCash = Money.ZERO,
                    status = OrderStatus.REJECTED,
                    createdAtTick = 0L,
                    createdAtEpochMillis = request.createdAtEpochMillis,
                    updatedAtTick = 0L,
                    rejectReason = OrderRejectReason.UNKNOWN_ASSET
                )
                OrderSubmissionResult.Rejected(rejected, OrderRejectReason.UNKNOWN_ASSET)
            } else {
                val quotesBySymbol = buildQuotesMap(marketState)
                val quote = quotesBySymbol.getValue(request.symbol)
                val requestWithTime = if (request.createdAtEpochMillis == 0L) {
                    request.copy(createdAtEpochMillis = System.currentTimeMillis())
                } else {
                    request
                }

                val (afterOrder, result) = OrderEngine.submitOrder(
                    state = current,
                    request = requestWithTime,
                    quote = quote,
                    tickIndex = marketState.tickIndex
                )

                if (result !is OrderSubmissionResult.DuplicateIgnored) {
                    val withSnapshot = OrderEngine.recordEquitySnapshot(
                        state = afterOrder,
                        quotesBySymbol = quotesBySymbol,
                        tickIndex = marketState.tickIndex,
                        recordedAtEpochMillis = System.currentTimeMillis()
                    )
                    val promoted = applyCompetencyPromotion(withSnapshot, marketState, quotesBySymbol)
                    persistPortfolioChanges(previous = current, next = promoted)
                    _portfolioState.value = promoted
                }
                result
            }
        }
    }

    override suspend fun cancelOrder(orderId: String) {
        ensureInitialized()
        portfolioMutex.withLock {
            val current = _portfolioState.value ?: return
            val tick = simulationRepository.marketState.value?.tickIndex ?: 0L
            val updated = OrderEngine.cancelOrder(current, orderId, tick)
            if (updated != current) {
                persistPortfolioChanges(previous = current, next = updated)
                _portfolioState.value = updated
            }
        }
    }

    override suspend fun setCareerLevel(level: CareerLevel) {
        ensureInitialized()
        portfolioMutex.withLock {
            val current = _portfolioState.value ?: return
            if (current.careerLevel == level) return
            val next = current.copy(careerLevel = level)
            persistPortfolioChanges(previous = current, next = next)
            _portfolioState.value = next
        }
    }

    override suspend fun resetPortfolio() {
        simulationRepository.ensureInitialized()
        portfolioMutex.withLock {
            val currentLevel = _portfolioState.value?.careerLevel ?: CareerLevel.LEVEL_1_NOVICE
            val tickIndex = simulationRepository.marketState.value?.tickIndex ?: 0L
            val now = System.currentTimeMillis()
            val fresh = OrderEngine.createInitialPortfolio(
                initialCash = Money(GameBalance.INITIAL_VIRTUAL_CASH_MINOR),
                careerLevel = currentLevel,
                initialTickIndex = tickIndex,
                recordedAtEpochMillis = now
            )
            transactionRunner.runInTransaction {
                positionDao.clearAll()
                orderDao.clearAll()
                tradeFillDao.clearAll()
                equitySnapshotDao.clearAll()
                liquidationEventDao.clearAll()
                accountDao.upsertAccount(
                    VirtualAccountEntity(
                        cashMinorUnits = fresh.cashBalance.minorUnits,
                        careerLevel = fresh.careerLevel.levelNumber,
                        totalBorrowFeesMinorUnits = 0L,
                        insolvencyCoverageMinorUnits = 0L,
                        updatedAtEpochMillis = now
                    )
                )
                equitySnapshotDao.upsertAll(fresh.equityCurve.map { it.toEntity() })
            }
            lastProcessedTick = tickIndex
            _portfolioState.value = fresh
        }
    }

    private suspend fun onLearningProgressChanged(progress: LearningProgress) {
        val marketState = simulationRepository.marketState.value
        if (_portfolioState.value != null && marketState != null) {
            portfolioMutex.withLock {
                val lockedCurrent = _portfolioState.value
                if (lockedCurrent != null) {
                    val quotesBySymbol = buildQuotesMap(marketState)
                    val promoted = applyCompetencyPromotion(
                        state = lockedCurrent,
                        marketState = marketState,
                        quotesBySymbol = quotesBySymbol,
                        learningProgress = progress
                    )
                    if (promoted != lockedCurrent) {
                        persistPortfolioChanges(previous = lockedCurrent, next = promoted)
                        _portfolioState.value = promoted
                    }
                }
            }
        }
    }

    private suspend fun onMarketTick(marketState: MultiAssetMarketState) {
        portfolioMutex.withLock {
            val current = _portfolioState.value ?: loadOrSeedFromDatabase().also {
                _portfolioState.value = it
            }

            if (marketState.tickIndex < lastProcessedTick) {
                lastProcessedTick = marketState.tickIndex
            }
            if (marketState.tickIndex == lastProcessedTick && current.equityCurve.isNotEmpty()) {
                return
            }

            val quotesBySymbol = buildQuotesMap(marketState)
            val candlesBySymbol = marketState.assetStates.mapNotNull { assetState ->
                val lastCandle = assetState.baseCandles.lastOrNull() ?: return@mapNotNull null
                assetState.spec.symbol to lastCandle
            }.toMap()
            val now = System.currentTimeMillis()

            val afterTick = OrderEngine.processMarketTick(
                state = current,
                candlesBySymbol = candlesBySymbol,
                quotesBySymbol = quotesBySymbol,
                tickIndex = marketState.tickIndex,
                executedAtEpochMillis = now
            )
            val withSnapshot = OrderEngine.recordEquitySnapshot(
                state = afterTick,
                quotesBySymbol = quotesBySymbol,
                tickIndex = marketState.tickIndex,
                recordedAtEpochMillis = now
            )
            val updated = applyCompetencyPromotion(withSnapshot, marketState, quotesBySymbol)

            persistPortfolioChanges(previous = current, next = updated)
            lastProcessedTick = marketState.tickIndex
            _portfolioState.value = updated
        }
    }

    private fun applyCompetencyPromotion(
        state: PortfolioState,
        marketState: MultiAssetMarketState,
        quotesBySymbol: Map<String, MarketQuote>,
        learningProgress: LearningProgress = latestLearningProgress
    ): PortfolioState {
        val categories = marketState.assetStates.associate { it.spec.symbol to it.spec.category }
        val metrics = RiskEngine.computePortfolioMetrics(state, quotesBySymbol)
        val snapshot = ProgressionEngine.evaluateCareerProgress(
            portfolio = state,
            categoriesBySymbol = categories,
            learningProgress = learningProgress,
            metrics = metrics
        )
        val unlocked = snapshot.highestCompetencyUnlockedLevel
        return if (unlocked.levelNumber > state.careerLevel.levelNumber) {
            state.copy(careerLevel = unlocked)
        } else {
            state
        }
    }

    private suspend fun loadOrSeedFromDatabase(): PortfolioState {
        val existingAccount = accountDao.getAccount()
        if (existingAccount == null) {
            val now = System.currentTimeMillis()
            val initialTick = simulationRepository.marketState.value?.tickIndex ?: 0L
            val fresh = OrderEngine.createInitialPortfolio(
                initialCash = Money(GameBalance.INITIAL_VIRTUAL_CASH_MINOR),
                careerLevel = CareerLevel.LEVEL_1_NOVICE,
                initialTickIndex = initialTick,
                recordedAtEpochMillis = now
            )
            transactionRunner.runInTransaction {
                accountDao.upsertAccount(
                    VirtualAccountEntity(
                        cashMinorUnits = fresh.cashBalance.minorUnits,
                        careerLevel = fresh.careerLevel.levelNumber,
                        totalBorrowFeesMinorUnits = 0L,
                        insolvencyCoverageMinorUnits = 0L,
                        updatedAtEpochMillis = now
                    )
                )
                equitySnapshotDao.upsertAll(fresh.equityCurve.map { it.toEntity() })
            }
            return fresh
        }

        val positions = positionDao.getAll().map { it.toDomain() }.associateBy { it.symbol }
        val orders = orderDao.getAll().map { it.toDomain() }
        val fills = tradeFillDao.getAll().map { it.toDomain() }
        val equityCurve = equitySnapshotDao.getAll().map { it.toDomain() }
        val liquidations = liquidationEventDao.getAll().map { it.toDomain() }

        return PortfolioState(
            initialCash = Money(GameBalance.INITIAL_VIRTUAL_CASH_MINOR),
            cashBalance = Money(existingAccount.cashMinorUnits),
            careerLevel = CareerLevel.fromLevelNumber(existingAccount.careerLevel),
            positions = positions,
            orders = orders,
            fills = fills,
            equityCurve = equityCurve,
            liquidationEvents = liquidations,
            totalBorrowFeesPaid = Money(existingAccount.totalBorrowFeesMinorUnits),
            insolvencyCoverage = Money(existingAccount.insolvencyCoverageMinorUnits),
            processedOrderIds = orders.map { it.orderId }.toSet()
        )
    }

    private suspend fun persistPortfolioChanges(previous: PortfolioState, next: PortfolioState) {
        val now = System.currentTimeMillis()
        transactionRunner.runInTransaction {
            val accountChanged = previous.cashBalance != next.cashBalance ||
                previous.careerLevel != next.careerLevel ||
                previous.totalBorrowFeesPaid != next.totalBorrowFeesPaid ||
                previous.insolvencyCoverage != next.insolvencyCoverage
            if (accountChanged) {
                accountDao.upsertAccount(
                    VirtualAccountEntity(
                        cashMinorUnits = next.cashBalance.minorUnits,
                        careerLevel = next.careerLevel.levelNumber,
                        totalBorrowFeesMinorUnits = next.totalBorrowFeesPaid.minorUnits,
                        insolvencyCoverageMinorUnits = next.insolvencyCoverage.minorUnits,
                        updatedAtEpochMillis = now
                    )
                )
            }
            if (previous.positions != next.positions) {
                positionDao.clearAll()
                if (next.positions.isNotEmpty()) {
                    positionDao.upsertAll(next.positions.values.map { it.toEntity() })
                }
            }
            if (previous.orders != next.orders) {
                orderDao.upsertAll(next.orders.map { it.toEntity() })
            }
            if (previous.fills.size < next.fills.size) {
                val newFills = next.fills.drop(previous.fills.size)
                tradeFillDao.insertAllIgnore(newFills.map { it.toEntity() })
            }
            if (previous.liquidationEvents.size < next.liquidationEvents.size) {
                val newEvents = next.liquidationEvents.drop(previous.liquidationEvents.size)
                liquidationEventDao.insertAllIgnore(newEvents.map { it.toEntity() })
            }
            val latestPoint = next.equityCurve.lastOrNull()
            if (latestPoint != null && previous.equityCurve.lastOrNull() != latestPoint) {
                equitySnapshotDao.upsertAll(listOf(latestPoint.toEntity()))
            }
        }
    }

    private fun buildQuotesMap(marketState: MultiAssetMarketState): Map<String, MarketQuote> =
        marketState.assetStates.associate { assetState ->
            assetState.spec.symbol to OrderFormulas.quoteFromAssetState(assetState)
        }
}
