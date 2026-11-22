package com.tradinginaja.feature.market

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradinginaja.core.common.Money
import com.tradinginaja.core.designsystem.component.ChartDisplayMode
import com.tradinginaja.domain.engine.MarketAssetState
import com.tradinginaja.domain.engine.MarketEngine
import com.tradinginaja.domain.model.AssetSpec
import com.tradinginaja.domain.model.Candle
import com.tradinginaja.domain.model.CandleTimeframe
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

sealed interface MarketDetailUiState {
    data object Loading : MarketDetailUiState

    data class Ready(
        val spec: AssetSpec,
        val currentPrice: Money,
        val priceChange: Money,
        val changePercent: Double,
        val isPositive: Boolean,
        val bidPrice: Money,
        val askPrice: Money,
        val spreadBps: Long,
        val tickIndex: Long,
        val highSession: Money,
        val lowSession: Money,
        val lastVolume: Long,
        val candles: List<Candle>,
        val selectedTimeframe: CandleTimeframe,
        val chartDisplayMode: ChartDisplayMode,
        val simulationSpeed: SimulationSpeed,
        val isWatchlisted: Boolean,
        val relatedNewsCount: Int = 0,
        val currentRegime: MarketRegime = MarketRegime.SIDEWAYS
    ) : MarketDetailUiState
}

sealed interface MarketDetailUiEvent {
    data class SelectAsset(val symbol: String) : MarketDetailUiEvent
    data class SelectSpeed(val speed: SimulationSpeed) : MarketDetailUiEvent
    data class SelectTimeframe(val timeframe: CandleTimeframe) : MarketDetailUiEvent
    data class SelectChartMode(val mode: ChartDisplayMode) : MarketDetailUiEvent
    data object ToggleWatchlist : MarketDetailUiEvent
    data object StepOnce : MarketDetailUiEvent
    data object ResetSimulation : MarketDetailUiEvent
}

@HiltViewModel
class MarketDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val simulationRepository: MarketSimulationRepository,
    private val watchlistRepository: WatchlistRepository
) : ViewModel() {

    private val selectedSymbol = MutableStateFlow(savedStateHandle.get<String>(ARG_SYMBOL))
    private val selectedTimeframe = MutableStateFlow(CandleTimeframe.TICK_15M)
    private val chartDisplayMode = MutableStateFlow(ChartDisplayMode.CANDLESTICK)

    val uiState: StateFlow<MarketDetailUiState> = combine(
        simulationRepository.marketState,
        simulationRepository.simulationSpeed,
        watchlistRepository.watchlistSymbols,
        selectedSymbol,
        combine(selectedTimeframe, chartDisplayMode) { tf, mode -> tf to mode }
    ) { marketState, speed, watchlist, symbol, chartPrefs ->
        val assetState = if (marketState == null) {
            null
        } else if (symbol != null) {
            marketState.findAsset(symbol) ?: marketState.assetStates.firstOrNull()
        } else {
            marketState.assetStates.firstOrNull()
        }

        if (assetState == null || marketState == null) {
            MarketDetailUiState.Loading
        } else {
            val relatedNewsCount = marketState.newsHistory.count {
                it.affectedSymbols.contains(assetState.spec.symbol)
            }
            buildUiState(
                marketState = assetState,
                timeframe = chartPrefs.first,
                displayMode = chartPrefs.second,
                speed = speed,
                isWatchlisted = watchlist.contains(assetState.spec.symbol),
                relatedNewsCount = relatedNewsCount,
                currentRegime = marketState.currentRegime
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MarketDetailUiState.Loading
    )

    init {
        viewModelScope.launch {
            simulationRepository.ensureInitialized()
        }
    }

    fun onEvent(event: MarketDetailUiEvent) {
        when (event) {
            is MarketDetailUiEvent.SelectAsset -> selectedSymbol.value = event.symbol
            is MarketDetailUiEvent.SelectSpeed -> simulationRepository.selectSpeed(event.speed)
            is MarketDetailUiEvent.SelectTimeframe -> selectedTimeframe.value = event.timeframe
            is MarketDetailUiEvent.SelectChartMode -> chartDisplayMode.value = event.mode
            MarketDetailUiEvent.ToggleWatchlist -> {
                val symbolToToggle = (uiState.value as? MarketDetailUiState.Ready)?.spec?.symbol
                    ?: selectedSymbol.value
                if (symbolToToggle != null) {
                    viewModelScope.launch {
                        watchlistRepository.toggleWatchlist(symbolToToggle)
                    }
                }
            }
            MarketDetailUiEvent.StepOnce -> simulationRepository.stepOnce()
            MarketDetailUiEvent.ResetSimulation -> simulationRepository.resetSimulation()
        }
    }

    private fun buildUiState(
        marketState: MarketAssetState,
        timeframe: CandleTimeframe,
        displayMode: ChartDisplayMode,
        speed: SimulationSpeed,
        isWatchlisted: Boolean,
        relatedNewsCount: Int,
        currentRegime: MarketRegime
    ): MarketDetailUiState.Ready {
        val currentPriceMoney = Candle.doublePriceToMoney(marketState.currentPrice)
        val referencePrice = marketState.baseCandles.firstOrNull()?.open ?: marketState.spec.initialPrice
        val referenceMoney = Candle.doublePriceToMoney(referencePrice)
        val deltaMoney = currentPriceMoney - referenceMoney
        val pct = if (referencePrice > 0.0) {
            ((marketState.currentPrice - referencePrice) / referencePrice) * PERCENT_FACTOR
        } else {
            0.0
        }

        val (bidDouble, askDouble) = MarketEngine.bidAskFor(marketState)
        val aggregatedCandles = MarketEngine.candlesForTimeframe(marketState, timeframe)

        var sessionHigh = marketState.currentPrice
        var sessionLow = marketState.currentPrice
        for (candle in marketState.baseCandles) {
            if (candle.high > sessionHigh) sessionHigh = candle.high
            if (candle.low < sessionLow) sessionLow = candle.low
        }

        return MarketDetailUiState.Ready(
            spec = marketState.spec,
            currentPrice = currentPriceMoney,
            priceChange = deltaMoney,
            changePercent = pct,
            isPositive = deltaMoney.minorUnits >= 0L,
            bidPrice = Candle.doublePriceToMoney(bidDouble),
            askPrice = Candle.doublePriceToMoney(askDouble),
            spreadBps = marketState.spec.liquidityTier.baseSpreadBps,
            tickIndex = marketState.tickIndex,
            highSession = Candle.doublePriceToMoney(sessionHigh),
            lowSession = Candle.doublePriceToMoney(sessionLow),
            lastVolume = marketState.baseCandles.lastOrNull()?.volume ?: 0L,
            candles = aggregatedCandles,
            selectedTimeframe = timeframe,
            chartDisplayMode = displayMode,
            simulationSpeed = speed,
            isWatchlisted = isWatchlisted,
            relatedNewsCount = relatedNewsCount,
            currentRegime = currentRegime
        )
    }

    companion object {
        const val ARG_SYMBOL = "symbol"
        private const val PERCENT_FACTOR = 100.0
    }
}
