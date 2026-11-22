package com.tradinginaja.feature.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradinginaja.core.common.Money
import com.tradinginaja.domain.engine.MarketAssetState
import com.tradinginaja.domain.engine.MultiAssetMarketState
import com.tradinginaja.domain.model.AssetSpec
import com.tradinginaja.domain.model.Candle
import com.tradinginaja.domain.model.MarketRegime
import com.tradinginaja.domain.model.SimulationSpeed
import com.tradinginaja.domain.repository.MarketSimulationRepository
import com.tradinginaja.domain.repository.WatchlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MarketAssetRowItem(
    val spec: AssetSpec,
    val currentPrice: Money,
    val changePercent: Double,
    val isPositive: Boolean,
    val sparklinePrices: List<Double>,
    val isWatchlisted: Boolean
)

sealed interface MarketListUiState {
    data object Loading : MarketListUiState

    data class Ready(
        val items: List<MarketAssetRowItem>,
        val totalAssetCount: Int,
        val watchlistCount: Int,
        val searchQuery: String,
        val selectedFilter: MarketFilter,
        val simulationSpeed: SimulationSpeed,
        val tickIndex: Long,
        val currentRegime: MarketRegime = MarketRegime.SIDEWAYS,
        val activeNewsCount: Int = 0,
        val latestNewsHeadlineId: String? = null,
        val latestNewsHeadlineEn: String? = null
    ) : MarketListUiState
}

sealed interface MarketListUiEvent {
    data class SearchQueryChanged(val query: String) : MarketListUiEvent
    data class SelectFilter(val filter: MarketFilter) : MarketListUiEvent
    data class ToggleWatchlist(val symbol: String) : MarketListUiEvent
    data class SelectSpeed(val speed: SimulationSpeed) : MarketListUiEvent
    data object StepOnce : MarketListUiEvent
    data object ResetSimulation : MarketListUiEvent
}

@HiltViewModel
class MarketListViewModel @Inject constructor(
    private val simulationRepository: MarketSimulationRepository,
    private val watchlistRepository: WatchlistRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow(MarketFilter.ALL)

    val uiState: StateFlow<MarketListUiState> = combine(
        simulationRepository.marketState,
        watchlistRepository.watchlistSymbols,
        simulationRepository.simulationSpeed,
        searchQuery,
        selectedFilter
    ) { marketState, watchlist, speed, query, filter ->
        if (marketState == null) {
            MarketListUiState.Loading
        } else {
            buildReadyState(
                marketState = marketState,
                watchlist = watchlist,
                speed = speed,
                query = query,
                filter = filter
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MarketListUiState.Loading
    )

    init {
        viewModelScope.launch {
            simulationRepository.ensureInitialized()
        }
    }

    fun onEvent(event: MarketListUiEvent) {
        when (event) {
            is MarketListUiEvent.SearchQueryChanged -> searchQuery.value = event.query
            is MarketListUiEvent.SelectFilter -> selectedFilter.value = event.filter
            is MarketListUiEvent.ToggleWatchlist -> {
                viewModelScope.launch {
                    watchlistRepository.toggleWatchlist(event.symbol)
                }
            }
            is MarketListUiEvent.SelectSpeed -> simulationRepository.selectSpeed(event.speed)
            MarketListUiEvent.StepOnce -> simulationRepository.stepOnce()
            MarketListUiEvent.ResetSimulation -> simulationRepository.resetSimulation()
        }
    }

    private fun buildReadyState(
        marketState: MultiAssetMarketState,
        watchlist: Set<String>,
        speed: SimulationSpeed,
        query: String,
        filter: MarketFilter
    ): MarketListUiState.Ready {
        val trimmedQuery = query.trim()
        val filteredItems = marketState.assetStates
            .filter { assetState ->
                val isWatchlisted = watchlist.contains(assetState.spec.symbol)
                val matchesFilter = filter.matches(assetState.spec.category, isWatchlisted)
                val matchesQuery = trimmedQuery.isEmpty() ||
                    assetState.spec.symbol.contains(trimmedQuery, ignoreCase = true) ||
                    assetState.spec.name.contains(trimmedQuery, ignoreCase = true) ||
                    assetState.spec.sector.contains(trimmedQuery, ignoreCase = true)
                matchesFilter && matchesQuery
            }
            .map { assetState ->
                assetState.toRowItem(isWatchlisted = watchlist.contains(assetState.spec.symbol))
            }

        val latestNews = marketState.newsHistory.firstOrNull()
        return MarketListUiState.Ready(
            items = filteredItems,
            totalAssetCount = marketState.assetStates.size,
            watchlistCount = watchlist.size,
            searchQuery = query,
            selectedFilter = filter,
            simulationSpeed = speed,
            tickIndex = marketState.tickIndex,
            currentRegime = marketState.currentRegime,
            activeNewsCount = marketState.activeEvents.size,
            latestNewsHeadlineId = latestNews?.headlineId,
            latestNewsHeadlineEn = latestNews?.headlineEn
        )
    }

    private fun MarketAssetState.toRowItem(isWatchlisted: Boolean): MarketAssetRowItem {
        val referencePrice = baseCandles.firstOrNull()?.open ?: spec.initialPrice
        val pctChange = if (referencePrice > 0.0) {
            ((currentPrice - referencePrice) / referencePrice) * PERCENT_FACTOR
        } else {
            0.0
        }
        val sparkline = baseCandles
            .takeLast(SPARKLINE_POINT_COUNT)
            .map { it.close }
            .ifEmpty { listOf(currentPrice) }

        return MarketAssetRowItem(
            spec = spec,
            currentPrice = Candle.doublePriceToMoney(currentPrice),
            changePercent = pctChange,
            isPositive = pctChange >= 0.0,
            sparklinePrices = sparkline,
            isWatchlisted = isWatchlisted
        )
    }

    private companion object {
        const val PERCENT_FACTOR = 100.0
        const val SPARKLINE_POINT_COUNT = 32
    }
}
