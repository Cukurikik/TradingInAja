package com.tradinginaja.feature.market

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradinginaja.domain.engine.MultiAssetMarketState
import com.tradinginaja.domain.model.EventTargetScope
import com.tradinginaja.domain.model.MarketRegime
import com.tradinginaja.domain.model.NewsItem
import com.tradinginaja.domain.model.SimulationSpeed
import com.tradinginaja.domain.repository.MarketSimulationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NewsFilterMode {
    ALL,
    ACTIVE_ONLY
}

/**
 * Representasi UI untuk berita pasar.
 * Sengaja tidak memuat angka drift/volatilitas agar pemain menafsirkan berita secara mandiri.
 */
data class NewsArticleUiItem(
    val newsId: String,
    val tickIndex: Long,
    val eventTypeLabel: String,
    val targetScope: EventTargetScope,
    val targetValue: String?,
    val affectedSymbols: List<String>,
    val headlineId: String,
    val headlineEn: String,
    val summaryId: String,
    val summaryEn: String,
    val bullCaseId: String,
    val bullCaseEn: String,
    val bearCaseId: String,
    val bearCaseEn: String,
    val isActive: Boolean
) {
    fun localizedHeadline(isIndonesian: Boolean): String = if (isIndonesian) headlineId else headlineEn
    fun localizedSummary(isIndonesian: Boolean): String = if (isIndonesian) summaryId else summaryEn
    fun localizedBullCase(isIndonesian: Boolean): String = if (isIndonesian) bullCaseId else bullCaseEn
    fun localizedBearCase(isIndonesian: Boolean): String = if (isIndonesian) bearCaseId else bearCaseEn
}

sealed interface NewsUiState {
    data object Loading : NewsUiState

    data class Ready(
        val articles: List<NewsArticleUiItem>,
        val totalNewsCount: Int,
        val activeNewsCount: Int,
        val currentRegime: MarketRegime,
        val ticksInRegime: Long,
        val tickIndex: Long,
        val simulationSpeed: SimulationSpeed,
        val filterMode: NewsFilterMode,
        val selectedSymbolFilter: String?,
        val availableSymbols: List<String>
    ) : NewsUiState
}

sealed interface NewsUiEvent {
    data class SelectFilterMode(val mode: NewsFilterMode) : NewsUiEvent
    data class SelectSymbolFilter(val symbol: String?) : NewsUiEvent
    data class SelectSpeed(val speed: SimulationSpeed) : NewsUiEvent
    data object StepOnce : NewsUiEvent
}

@HiltViewModel
class NewsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val simulationRepository: MarketSimulationRepository
) : ViewModel() {

    private val filterMode = MutableStateFlow(NewsFilterMode.ALL)
    private val selectedSymbolFilter = MutableStateFlow(savedStateHandle.get<String>(ARG_SYMBOL_FILTER))

    val uiState: StateFlow<NewsUiState> = combine(
        simulationRepository.marketState,
        simulationRepository.simulationSpeed,
        filterMode,
        selectedSymbolFilter
    ) { marketState, speed, mode, symbolFilter ->
        if (marketState == null) {
            NewsUiState.Loading
        } else {
            buildUiState(
                marketState = marketState,
                speed = speed,
                mode = mode,
                symbolFilter = symbolFilter
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = NewsUiState.Loading
    )

    init {
        viewModelScope.launch {
            simulationRepository.ensureInitialized()
        }
    }

    fun onEvent(event: NewsUiEvent) {
        when (event) {
            is NewsUiEvent.SelectFilterMode -> filterMode.value = event.mode
            is NewsUiEvent.SelectSymbolFilter -> selectedSymbolFilter.value = event.symbol
            is NewsUiEvent.SelectSpeed -> simulationRepository.selectSpeed(event.speed)
            NewsUiEvent.StepOnce -> simulationRepository.stepOnce()
        }
    }

    private fun buildUiState(
        marketState: MultiAssetMarketState,
        speed: SimulationSpeed,
        mode: NewsFilterMode,
        symbolFilter: String?
    ): NewsUiState.Ready {
        val activeEventIds = marketState.activeEvents
            .mapTo(HashSet(marketState.activeEvents.size)) { "${it.definition.eventId}_${it.startedAtTick}" }

        val allArticles = marketState.newsHistory.map { item ->
            item.toUiItem(isActive = activeEventIds.contains(item.newsId))
        }

        val activeCount = allArticles.count { it.isActive }
        val filtered = allArticles.filter { article ->
            val matchesMode = mode == NewsFilterMode.ALL || article.isActive
            val matchesSymbol = symbolFilter == null || article.affectedSymbols.contains(symbolFilter)
            matchesMode && matchesSymbol
        }

        return NewsUiState.Ready(
            articles = filtered,
            totalNewsCount = allArticles.size,
            activeNewsCount = activeCount,
            currentRegime = marketState.currentRegime,
            ticksInRegime = marketState.ticksInRegime,
            tickIndex = marketState.tickIndex,
            simulationSpeed = speed,
            filterMode = mode,
            selectedSymbolFilter = symbolFilter,
            availableSymbols = marketState.assetStates.map { it.spec.symbol }
        )
    }

    private fun NewsItem.toUiItem(isActive: Boolean): NewsArticleUiItem = NewsArticleUiItem(
        newsId = newsId,
        tickIndex = tickIndex,
        eventTypeLabel = eventType.replace('_', ' '),
        targetScope = targetScope,
        targetValue = targetValue,
        affectedSymbols = affectedSymbols,
        headlineId = headlineId,
        headlineEn = headlineEn,
        summaryId = summaryId,
        summaryEn = summaryEn,
        bullCaseId = bullCaseId,
        bullCaseEn = bullCaseEn,
        bearCaseId = bearCaseId,
        bearCaseEn = bearCaseEn,
        isActive = isActive
    )

    companion object {
        const val ARG_SYMBOL_FILTER = "symbolFilter"
    }
}
