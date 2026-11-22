package com.tradinginaja.feature.profile

import com.tradinginaja.domain.engine.OrderEngine
import com.tradinginaja.domain.model.CareerLevel
import com.tradinginaja.domain.model.LearnLessonIds
import com.tradinginaja.domain.model.LearningProgress
import com.tradinginaja.domain.model.OrderRequest
import com.tradinginaja.domain.model.OrderSubmissionResult
import com.tradinginaja.domain.model.PortfolioState
import com.tradinginaja.domain.model.ThemeMode
import com.tradinginaja.domain.repository.PortfolioRepository
import com.tradinginaja.domain.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

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
    fun `profile exposes career level learning progress and theme updates`() = runTest(testDispatcher) {
        val prefs = FakeProfilePrefsRepo()
        val portfolio = FakeProfilePortfolioRepo()
        val viewModel = ProfileViewModel(prefs, portfolio)
        backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        runCurrent()

        viewModel.onEvent(ProfileUiEvent.SelectThemeMode(ThemeMode.AMOLED))
        runCurrent()

        val state = viewModel.uiState.value as ProfileUiState.Content
        assertEquals(ThemeMode.AMOLED, state.themeMode)
        assertEquals(CareerLevel.LEVEL_2_ACTIVE, state.careerLevel)
        assertEquals(1, state.learningProgress.completedLessonIds.size)
        assertEquals(2, state.learningProgress.candleDrillsPassed)
    }
}

private class FakeProfilePrefsRepo : PreferencesRepository {
    private val themeModeFlow = MutableStateFlow(ThemeMode.SYSTEM)
    private val learningFlow = MutableStateFlow(
        LearningProgress(
            tutorialCompleted = true,
            completedLessonIds = setOf(LearnLessonIds.BASICS_SPREAD_SL),
            candleDrillsPassed = 2
        )
    )
    override val themeMode: Flow<ThemeMode> = themeModeFlow
    override val learningProgress: Flow<LearningProgress> = learningFlow
    override suspend fun setThemeMode(mode: ThemeMode) {
        themeModeFlow.value = mode
    }
    override suspend fun setTutorialCompleted(completed: Boolean) = Unit
    override suspend fun markLessonCompleted(lessonId: String) = Unit
    override suspend fun incrementCandleDrillsPassed() = Unit
}

private class FakeProfilePortfolioRepo : PortfolioRepository {
    private val portfolioFlow = MutableStateFlow<PortfolioState?>(
        OrderEngine.createInitialPortfolio(careerLevel = CareerLevel.LEVEL_2_ACTIVE)
    )
    override val portfolioState: StateFlow<PortfolioState?> = portfolioFlow.asStateFlow()
    override suspend fun ensureInitialized() = Unit
    override suspend fun submitOrder(request: OrderRequest): OrderSubmissionResult = error("Not used")
    override suspend fun cancelOrder(orderId: String) = Unit
    override suspend fun setCareerLevel(level: CareerLevel) = Unit
    override suspend fun resetPortfolio() = Unit
}
