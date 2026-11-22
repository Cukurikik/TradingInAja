package com.tradinginaja.feature.market

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MarketListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(testScope: TestScope): MarketListViewModel = MarketListViewModel(
        simulationRepository = FakeMarketSimulationRepository(testScope.backgroundScope),
        watchlistRepository = FakeWatchlistRepository()
    )

    @Test
    fun `initial list loads all catalog assets with sparkline prices`() = runTest(testDispatcher) {
        val viewModel = createViewModel(this)
        runCurrent()

        val state = viewModel.uiState.value as MarketListUiState.Ready
        assertEquals(3, state.totalAssetCount)
        assertEquals(3, state.items.size)
        assertTrue(state.items.all { it.sparklinePrices.size == 32 })
    }

    @Test
    fun `search query and category filter narrow visible assets`() = runTest(testDispatcher) {
        val viewModel = createViewModel(this)
        runCurrent()

        viewModel.onEvent(MarketListUiEvent.SearchQueryChanged("garuda"))
        runCurrent()
        val afterSearch = viewModel.uiState.value as MarketListUiState.Ready
        assertEquals(listOf("GRDC"), afterSearch.items.map { it.spec.symbol })

        viewModel.onEvent(MarketListUiEvent.SearchQueryChanged(""))
        viewModel.onEvent(MarketListUiEvent.SelectFilter(MarketFilter.FOREX))
        runCurrent()
        val afterFilter = viewModel.uiState.value as MarketListUiState.Ready
        assertEquals(listOf("VUD/VRP"), afterFilter.items.map { it.spec.symbol })
    }

    @Test
    fun `watchlist toggle pins asset and filters correctly`() = runTest(testDispatcher) {
        val viewModel = createViewModel(this)
        runCurrent()

        viewModel.onEvent(MarketListUiEvent.ToggleWatchlist("NSKP"))
        viewModel.onEvent(MarketListUiEvent.SelectFilter(MarketFilter.WATCHLIST))
        runCurrent()

        val watchState = viewModel.uiState.value as MarketListUiState.Ready
        assertEquals(1, watchState.watchlistCount)
        assertEquals(listOf("NSKP"), watchState.items.map { it.spec.symbol })
        assertTrue(watchState.items.first().isWatchlisted)

        viewModel.onEvent(MarketListUiEvent.ToggleWatchlist("NSKP"))
        runCurrent()

        val emptyWatchState = viewModel.uiState.value as MarketListUiState.Ready
        assertEquals(0, emptyWatchState.watchlistCount)
        assertTrue(emptyWatchState.items.isEmpty())
    }
}
