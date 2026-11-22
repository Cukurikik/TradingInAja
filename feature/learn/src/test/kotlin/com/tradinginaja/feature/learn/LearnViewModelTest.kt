package com.tradinginaja.feature.learn

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.core.common.Money
import com.tradinginaja.domain.engine.CholeskyDecomposition
import com.tradinginaja.domain.engine.MarketEngine
import com.tradinginaja.domain.engine.MultiAssetMarketState
import com.tradinginaja.domain.engine.OrderEngine
import com.tradinginaja.domain.model.AssetCategory
import com.tradinginaja.domain.model.AssetSpec
import com.tradinginaja.domain.model.CareerLevel
import com.tradinginaja.domain.model.LearnLessonIds
import com.tradinginaja.domain.model.LearningProgress
import com.tradinginaja.domain.model.LiquidityTier
import com.tradinginaja.domain.model.MarketCatalog
import com.tradinginaja.domain.model.OrderRequest
import com.tradinginaja.domain.model.OrderSubmissionResult
import com.tradinginaja.domain.model.PortfolioState
import com.tradinginaja.domain.model.SimulationSpeed
import com.tradinginaja.domain.model.ThemeMode
import com.tradinginaja.domain.repository.MarketSimulationRepository
import com.tradinginaja.domain.repository.PortfolioRepository
import com.tradinginaja.domain.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LearnViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `completing lessons and candle drills updates learning progress without affecting portfolio cash`() =
        runTest(testDispatcher) {
            val simRepo = FakeLearnMarketRepo()
            val portfolioRepo = FakeLearnPortfolioRepo()
            val prefsRepo = FakeLearnPrefsRepo()
            val viewModel = LearnViewModel(simRepo, portfolioRepo, prefsRepo)
            runCurrent()

            val initialCash = portfolioRepo.portfolioState.value!!.cashBalance

            viewModel.onEvent(LearnUiEvent.MarkLessonCompleted(LearnLessonIds.BASICS_SPREAD_SL))
            runCurrent()

            val afterLesson = viewModel.uiState.value as LearnUiState.Ready
            assertTrue(afterLesson.learningProgress.completedLessonIds.contains(LearnLessonIds.BASICS_SPREAD_SL))

            val correctAnswer = afterLesson.candleDrill.correctPattern
            viewModel.onEvent(LearnUiEvent.AnswerCandleDrill(correctAnswer))
            runCurrent()

            val afterDrill = viewModel.uiState.value as LearnUiState.Ready
            assertEquals(1, afterDrill.learningProgress.candleDrillsPassed)
            assertEquals(true, afterDrill.isDrillCorrect)

            viewModel.onEvent(LearnUiEvent.UpdateRiskPct(1.0))
            runCurrent()
            val afterSizing = viewModel.uiState.value as LearnUiState.Ready
            assertNotNull(afterSizing.sizingResult)
            assertTrue(afterSizing.sizingResult!!.isWithinRule)
            assertEquals(initialCash, portfolioRepo.portfolioState.value!!.cashBalance)
        }
}

private class FakeLearnMarketRepo : MarketSimulationRepository {
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
            )
        ),
        correlationMatrix = CholeskyDecomposition.identityMatrix(1)
    )
    private val marketStateFlow = MutableStateFlow<MultiAssetMarketState?>(
        MarketEngine.createMultiAssetInitialState(catalog, GameBalance.DEFAULT_SIMULATION_SEED, 20)
    )
    override val marketState: StateFlow<MultiAssetMarketState?> = marketStateFlow.asStateFlow()
    override val simulationSpeed: StateFlow<SimulationSpeed> = MutableStateFlow(SimulationSpeed.PAUSED)
    override suspend fun ensureInitialized() = Unit
    override fun selectSpeed(speed: SimulationSpeed) = Unit
    override fun stepOnce() = Unit
    override fun resetSimulation() = Unit
}

private class FakeLearnPortfolioRepo : PortfolioRepository {
    private val portfolioFlow = MutableStateFlow<PortfolioState?>(
        OrderEngine.createInitialPortfolio(initialCash = Money(GameBalance.INITIAL_VIRTUAL_CASH_MINOR))
    )
    override val portfolioState: StateFlow<PortfolioState?> = portfolioFlow.asStateFlow()
    override suspend fun ensureInitialized() = Unit
    override suspend fun submitOrder(request: OrderRequest): OrderSubmissionResult =
        error("Not needed in LearnViewModelTest")
    override suspend fun cancelOrder(orderId: String) = Unit
    override suspend fun setCareerLevel(level: CareerLevel) {
        portfolioFlow.value = portfolioFlow.value?.copy(careerLevel = level)
    }
    override suspend fun resetPortfolio() = Unit
}

private class FakeLearnPrefsRepo : PreferencesRepository {
    private val themeModeFlow = MutableStateFlow(ThemeMode.SYSTEM)
    private val learningFlow = MutableStateFlow(LearningProgress())
    override val themeMode: Flow<ThemeMode> = themeModeFlow
    override val learningProgress: Flow<LearningProgress> = learningFlow
    override suspend fun setThemeMode(mode: ThemeMode) {
        themeModeFlow.value = mode
    }
    override suspend fun setTutorialCompleted(completed: Boolean) {
        learningFlow.value = learningFlow.value.copy(tutorialCompleted = completed)
    }
    override suspend fun markLessonCompleted(lessonId: String) {
        learningFlow.value = learningFlow.value.copy(
            completedLessonIds = learningFlow.value.completedLessonIds + lessonId
        )
    }
    override suspend fun incrementCandleDrillsPassed() {
        learningFlow.value = learningFlow.value.copy(
            candleDrillsPassed = learningFlow.value.candleDrillsPassed + 1
        )
    }
}
