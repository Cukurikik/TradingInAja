package com.tradinginaja.feature.market

import androidx.lifecycle.SavedStateHandle
import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.core.designsystem.component.ChartDisplayMode
import com.tradinginaja.domain.model.CandleTimeframe
import com.tradinginaja.domain.model.SimulationSpeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MarketDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(testScope: TestScope, symbol: String = "NSKP"): MarketDetailViewModel {
        val simRepo = FakeMarketSimulationRepository(testScope.backgroundScope)
        val watchlistRepo = FakeWatchlistRepository()
        return MarketDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf(MarketDetailViewModel.ARG_SYMBOL to symbol)),
            simulationRepository = simRepo,
            watchlistRepository = watchlistRepo
        )
    }

    @Test
    fun `initial state starts paused with 120 history candles for requested symbol`() = runTest(testDispatcher) {
        val viewModel = createViewModel(this, symbol = "GRDC")
        runCurrent()

        val state = viewModel.uiState.value as MarketDetailUiState.Ready
        assertEquals("GRDC", state.spec.symbol)
        assertEquals(SimulationSpeed.PAUSED, state.simulationSpeed)
        assertEquals(GameBalance.DEFAULT_INITIAL_HISTORY_CANDLES.toLong(), state.tickIndex)
        assertEquals(GameBalance.DEFAULT_INITIAL_HISTORY_CANDLES, state.candles.size)
        assertFalse(state.isWatchlisted)
    }

    @Test
    fun `step once advances tick and reset restores initial seed state`() = runTest(testDispatcher) {
        val viewModel = createViewModel(this)
        runCurrent()
        val initial = viewModel.uiState.value as MarketDetailUiState.Ready

        viewModel.onEvent(MarketDetailUiEvent.StepOnce)
        runCurrent()
        val stepped = viewModel.uiState.value as MarketDetailUiState.Ready
        assertEquals(initial.tickIndex + 1L, stepped.tickIndex)

        viewModel.onEvent(MarketDetailUiEvent.ResetSimulation)
        runCurrent()
        val reset = viewModel.uiState.value as MarketDetailUiState.Ready
        assertEquals(initial.tickIndex, reset.tickIndex)
        assertEquals(initial.currentPrice, reset.currentPrice)
        assertEquals(SimulationSpeed.PAUSED, reset.simulationSpeed)
    }

    @Test
    fun `speed selection advances simulation ticks at expected cadence and pause stops it`() = runTest(testDispatcher) {
        val viewModel = createViewModel(this)
        runCurrent()
        val startTick = (viewModel.uiState.value as MarketDetailUiState.Ready).tickIndex

        viewModel.onEvent(MarketDetailUiEvent.SelectSpeed(SimulationSpeed.SPEED_5X))
        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()

        val after5x = viewModel.uiState.value as MarketDetailUiState.Ready
        assertEquals(startTick + 5L, after5x.tickIndex)

        viewModel.onEvent(MarketDetailUiEvent.SelectSpeed(SimulationSpeed.PAUSED))
        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()

        val afterPause = viewModel.uiState.value as MarketDetailUiState.Ready
        assertEquals(after5x.tickIndex, afterPause.tickIndex)
    }

    @Test
    fun `timeframe chart mode and watchlist toggle update state cleanly`() = runTest(testDispatcher) {
        val viewModel = createViewModel(this)
        runCurrent()

        viewModel.onEvent(MarketDetailUiEvent.SelectTimeframe(CandleTimeframe.HOUR_1))
        viewModel.onEvent(MarketDetailUiEvent.SelectChartMode(ChartDisplayMode.LINE))
        viewModel.onEvent(MarketDetailUiEvent.ToggleWatchlist)
        runCurrent()

        val updated = viewModel.uiState.value as MarketDetailUiState.Ready
        assertEquals(CandleTimeframe.HOUR_1, updated.selectedTimeframe)
        assertEquals(ChartDisplayMode.LINE, updated.chartDisplayMode)
        assertTrue(updated.candles.size < GameBalance.DEFAULT_INITIAL_HISTORY_CANDLES)
        assertTrue(updated.isWatchlisted)
    }
}
