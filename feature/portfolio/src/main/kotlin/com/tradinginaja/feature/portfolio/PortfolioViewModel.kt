package com.tradinginaja.feature.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradinginaja.core.common.Money
import com.tradinginaja.domain.engine.MultiAssetMarketState
import com.tradinginaja.domain.engine.OrderEngine
import com.tradinginaja.domain.engine.OrderFormulas
import com.tradinginaja.domain.engine.PostMortemAnalyzer
import com.tradinginaja.domain.engine.RiskEngine
import com.tradinginaja.domain.model.EquityPoint
import com.tradinginaja.domain.model.LiquidationEvent
import com.tradinginaja.domain.model.Order
import com.tradinginaja.domain.model.OrderStatus
import com.tradinginaja.domain.model.PortfolioRiskMetrics
import com.tradinginaja.domain.model.PortfolioState
import com.tradinginaja.domain.model.PostMortemSummary
import com.tradinginaja.domain.model.TradeFill
import com.tradinginaja.domain.model.TradePostMortem
import com.tradinginaja.domain.repository.MarketSimulationRepository
import com.tradinginaja.domain.repository.PortfolioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PortfolioSectionTab {
    POSITIONS,
    OPEN_ORDERS,
    HISTORY
}

data class PortfolioPositionItem(
    val symbol: String,
    val name: String,
    val isShort: Boolean,
    val absoluteQuantity: Long,
    val leverage: Int,
    val averageEntryPrice: Money,
    val markPrice: Money,
    val marketValue: Money,
    val marginLocked: Money,
    val accumulatedBorrowFee: Money,
    val exposurePctOfEquity: Double,
    val estLiquidationPrice: Money?,
    val unrealizedPnl: Money,
    val unrealizedPnlPct: Double
) {
    val quantity: Long get() = if (isShort) -absoluteQuantity else absoluteQuantity
}

sealed interface PortfolioUiState {
    data object Loading : PortfolioUiState

    data class Ready(
        val initialCash: Money,
        val totalEquity: Money,
        val cashBalance: Money,
        val reservedCash: Money,
        val positionsValue: Money,
        val unrealizedPnl: Money,
        val realizedPnl: Money,
        val totalCommissions: Money,
        val totalBorrowFees: Money,
        val totalReturnPct: Double,
        val riskMetrics: PortfolioRiskMetrics,
        val latestLiquidation: LiquidationEvent?,
        val equityCurve: List<EquityPoint>,
        val positions: List<PortfolioPositionItem>,
        val pendingOrders: List<Order>,
        val tradeHistory: List<TradeFill>,
        val postMortemSummary: PostMortemSummary,
        val postMortemsByFillId: Map<String, TradePostMortem>,
        val selectedTab: PortfolioSectionTab,
        val showResetDialog: Boolean
    ) : PortfolioUiState
}

sealed interface PortfolioUiEvent {
    data class SelectTab(val tab: PortfolioSectionTab) : PortfolioUiEvent
    data class CancelOrder(val orderId: String) : PortfolioUiEvent
    data object RequestReset : PortfolioUiEvent
    data object DismissReset : PortfolioUiEvent
    data object ConfirmReset : PortfolioUiEvent
}

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val simulationRepository: MarketSimulationRepository,
    private val portfolioRepository: PortfolioRepository
) : ViewModel() {

    private val selectedTab = MutableStateFlow(PortfolioSectionTab.POSITIONS)
    private val showResetDialog = MutableStateFlow(false)

    val uiState: StateFlow<PortfolioUiState> = combine(
        simulationRepository.marketState,
        portfolioRepository.portfolioState,
        selectedTab,
        showResetDialog
    ) { marketState, portfolioState, tab, resetDialog ->
        if (marketState == null || portfolioState == null) {
            PortfolioUiState.Loading
        } else {
            buildReadyState(marketState, portfolioState, tab, resetDialog)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PortfolioUiState.Loading
    )

    init {
        viewModelScope.launch {
            simulationRepository.ensureInitialized()
            portfolioRepository.ensureInitialized()
        }
    }

    fun onEvent(event: PortfolioUiEvent) {
        when (event) {
            is PortfolioUiEvent.SelectTab -> selectedTab.value = event.tab
            is PortfolioUiEvent.CancelOrder -> {
                viewModelScope.launch {
                    portfolioRepository.cancelOrder(event.orderId)
                }
            }
            PortfolioUiEvent.RequestReset -> showResetDialog.value = true
            PortfolioUiEvent.DismissReset -> showResetDialog.value = false
            PortfolioUiEvent.ConfirmReset -> {
                showResetDialog.value = false
                viewModelScope.launch {
                    portfolioRepository.resetPortfolio()
                }
            }
        }
    }

    private fun buildReadyState(
        marketState: MultiAssetMarketState,
        portfolio: PortfolioState,
        tab: PortfolioSectionTab,
        resetDialog: Boolean
    ): PortfolioUiState.Ready {
        val quotesBySymbol = marketState.assetStates.associate {
            it.spec.symbol to OrderFormulas.quoteFromAssetState(it)
        }
        val namesBySymbol = marketState.assetStates.associate {
            it.spec.symbol to it.spec.name
        }

        val riskMetrics = RiskEngine.computePortfolioMetrics(portfolio, quotesBySymbol)
        val exposuresBySymbol = riskMetrics.exposures.associateBy { it.symbol }
        val positionsContribution = OrderEngine.computePositionsNetEquity(portfolio, quotesBySymbol)
        val unrealizedPnl = OrderEngine.computeTotalUnrealizedPnl(portfolio, quotesBySymbol)
        val totalEquity = portfolio.cashBalance + positionsContribution
        val totalReturnPct = if (portfolio.initialCash.minorUnits > 0L) {
            (
                (totalEquity - portfolio.initialCash).minorUnits.toDouble() /
                    portfolio.initialCash.minorUnits.toDouble()
                ) * PERCENT_FACTOR
        } else {
            0.0
        }

        val positionItems = portfolio.positions.values
            .sortedBy { it.symbol }
            .map { pos ->
                val markPrice = quotesBySymbol[pos.symbol]?.midPrice ?: pos.averageEntryPrice
                val marketValue = pos.marketValue(markPrice)
                val pnl = pos.unrealizedPnl(markPrice)
                val exposure = exposuresBySymbol[pos.symbol]
                val denom = if (pos.marginLocked.isPositive) pos.marginLocked.minorUnits else pos.costBasis.minorUnits
                val pnlPct = if (denom > 0L) {
                    (pnl.minorUnits.toDouble() / denom.toDouble()) * PERCENT_FACTOR
                } else {
                    0.0
                }
                PortfolioPositionItem(
                    symbol = pos.symbol,
                    name = namesBySymbol[pos.symbol] ?: pos.symbol,
                    isShort = pos.isShort,
                    absoluteQuantity = pos.absoluteQuantity,
                    leverage = pos.leverage,
                    averageEntryPrice = pos.averageEntryPrice,
                    markPrice = markPrice,
                    marketValue = marketValue,
                    marginLocked = pos.marginLocked,
                    accumulatedBorrowFee = pos.accumulatedBorrowFee,
                    exposurePctOfEquity = exposure?.exposurePctOfEquity ?: 0.0,
                    estLiquidationPrice = exposure?.estLiquidationPrice,
                    unrealizedPnl = pnl,
                    unrealizedPnlPct = pnlPct
                )
            }

        val pendingOrders = portfolio.orders
            .filter { it.status == OrderStatus.PENDING }
            .sortedByDescending { it.createdAtTick }

        val history = portfolio.fills.sortedWith(
            compareByDescending<TradeFill> { it.tickIndex }
                .thenByDescending { it.executedAtEpochMillis }
        )
        val postMortems = PostMortemAnalyzer.analyzePortfolio(portfolio)
        val postMortemSummary = PostMortemAnalyzer.buildSummary(postMortems)

        return PortfolioUiState.Ready(
            initialCash = portfolio.initialCash,
            totalEquity = totalEquity,
            cashBalance = portfolio.cashBalance,
            reservedCash = portfolio.reservedCashForPendingBuys,
            positionsValue = positionsContribution,
            unrealizedPnl = unrealizedPnl,
            realizedPnl = portfolio.totalRealizedPnl,
            totalCommissions = portfolio.totalCommissionsPaid,
            totalBorrowFees = portfolio.totalBorrowFeesPaid,
            totalReturnPct = totalReturnPct,
            riskMetrics = riskMetrics,
            latestLiquidation = portfolio.liquidationEvents.lastOrNull(),
            equityCurve = portfolio.equityCurve,
            positions = positionItems,
            pendingOrders = pendingOrders,
            tradeHistory = history,
            postMortemSummary = postMortemSummary,
            postMortemsByFillId = postMortems.associateBy { it.fillId },
            selectedTab = tab,
            showResetDialog = resetDialog
        )
    }

    private companion object {
        const val PERCENT_FACTOR = 100.0
    }
}
