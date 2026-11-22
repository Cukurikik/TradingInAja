package com.tradinginaja.feature.portfolio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tradinginaja.core.designsystem.component.EquityCurveChart
import com.tradinginaja.core.designsystem.component.SimulationDisclaimerBanner
import com.tradinginaja.core.designsystem.theme.TradingNumberTextStyle
import com.tradinginaja.core.designsystem.theme.TradingTheme
import java.util.Locale

private val MAX_PORTFOLIO_WIDTH = 760.dp

@Composable
fun PortfolioRoute(
    onTradeAssetClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PortfolioViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PortfolioScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onTradeAssetClick = onTradeAssetClick,
        modifier = modifier
    )
}

@Composable
fun PortfolioScreen(
    uiState: PortfolioUiState,
    onEvent: (PortfolioUiEvent) -> Unit,
    onTradeAssetClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("portfolio_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        when (uiState) {
            PortfolioUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.portfolio_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is PortfolioUiState.Ready -> {
                PortfolioContentList(
                    uiState = uiState,
                    onEvent = onEvent,
                    onTradeAssetClick = onTradeAssetClick
                )

                if (uiState.showResetDialog) {
                    ResetPortfolioDialog(
                        initialCash = uiState.initialCash,
                        onConfirm = { onEvent(PortfolioUiEvent.ConfirmReset) },
                        onDismiss = { onEvent(PortfolioUiEvent.DismissReset) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PortfolioContentList(
    uiState: PortfolioUiState.Ready,
    onEvent: (PortfolioUiEvent) -> Unit,
    onTradeAssetClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = MAX_PORTFOLIO_WIDTH),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "summary_and_chart") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PortfolioSummaryCard(
                    state = uiState,
                    onRequestReset = { onEvent(PortfolioUiEvent.RequestReset) }
                )
                MarginHealthCard(
                    health = uiState.riskMetrics.marginHealth,
                    latestLiquidation = uiState.latestLiquidation
                )
                PortfolioRiskMetricsCard(metrics = uiState.riskMetrics)
                Text(
                    text = stringResource(R.string.portfolio_equity_curve_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                EquityCurveChart(
                    points = uiState.equityCurve,
                    baselineEquity = uiState.initialCash
                )
                PortfolioSectionTabBar(
                    selectedTab = uiState.selectedTab,
                    positionsCount = uiState.positions.size,
                    ordersCount = uiState.pendingOrders.size,
                    historyCount = uiState.tradeHistory.size,
                    onSelectTab = { onEvent(PortfolioUiEvent.SelectTab(it)) }
                )
            }
        }

        when (uiState.selectedTab) {
            PortfolioSectionTab.POSITIONS -> {
                if (uiState.positions.isEmpty()) {
                    item(key = "empty_positions") {
                        EmptySectionCard(text = stringResource(R.string.portfolio_empty_positions))
                    }
                } else {
                    items(uiState.positions, key = { "pos_${it.symbol}" }) { item ->
                        PositionRowCard(
                            item = item,
                            onTradeClick = { onTradeAssetClick(item.symbol) }
                        )
                    }
                }
            }
            PortfolioSectionTab.OPEN_ORDERS -> {
                if (uiState.pendingOrders.isEmpty()) {
                    item(key = "empty_orders") {
                        EmptySectionCard(text = stringResource(R.string.portfolio_empty_orders))
                    }
                } else {
                    items(uiState.pendingOrders, key = { "ord_${it.orderId}" }) { order ->
                        OpenOrderRowCard(
                            order = order,
                            onCancel = { onEvent(PortfolioUiEvent.CancelOrder(order.orderId)) }
                        )
                    }
                }
            }
            PortfolioSectionTab.HISTORY -> {
                if (uiState.postMortemSummary.totalLosingTrades > 0) {
                    item(key = "post_mortem_summary") {
                        PostMortemOverviewCard(summary = uiState.postMortemSummary)
                    }
                }
                if (uiState.tradeHistory.isEmpty()) {
                    item(key = "empty_history") {
                        EmptySectionCard(text = stringResource(R.string.portfolio_empty_history))
                    }
                } else {
                    items(uiState.tradeHistory, key = { "fill_${it.fillId}" }) { fill ->
                        TradeFillRowCard(
                            fill = fill,
                            postMortem = uiState.postMortemsByFillId[fill.fillId]
                        )
                    }
                }
            }
        }

        item(key = "disclaimer") {
            SimulationDisclaimerBanner()
        }
    }
}

@Composable
private fun PortfolioSummaryCard(state: PortfolioUiState.Ready, onRequestReset: () -> Unit) {
    val isPositive = state.totalEquity >= state.initialCash
    val trendColor = if (isPositive) TradingTheme.colors.bullish else TradingTheme.colors.bearish
    val signPrefix = if (isPositive) "+" else ""
    val pctLabel = String.format(
        Locale.US,
        stringResource(R.string.portfolio_percent_format),
        signPrefix,
        state.totalReturnPct
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("portfolio_summary_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.portfolio_total_equity),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = state.totalEquity.formatDisplay(),
                        style = TradingNumberTextStyle.copy(
                            fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("portfolio_total_equity")
                    )
                }

                OutlinedButton(
                    onClick = onRequestReset,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("portfolio_reset_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.portfolio_reset_button),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isPositive) Icons.Rounded.ArrowDropUp else Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    tint = trendColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = pctLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = trendColor
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCell(
                    label = stringResource(R.string.portfolio_cash_balance),
                    value = state.cashBalance.formatDisplay(),
                    modifier = Modifier.weight(1f)
                )
                MetricCell(
                    label = stringResource(R.string.portfolio_positions_value),
                    value = state.positionsValue.formatDisplay(),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCell(
                    label = stringResource(R.string.portfolio_unrealized_pnl),
                    value = formatSignedMoney(state.unrealizedPnl),
                    valueColor = if (state.unrealizedPnl.minorUnits >= 0L) {
                        TradingTheme.colors.bullish
                    } else {
                        TradingTheme.colors.bearish
                    },
                    modifier = Modifier.weight(1f)
                )
                MetricCell(
                    label = stringResource(R.string.portfolio_realized_pnl),
                    value = formatSignedMoney(state.realizedPnl),
                    valueColor = if (state.realizedPnl.minorUnits >= 0L) {
                        TradingTheme.colors.bullish
                    } else {
                        TradingTheme.colors.bearish
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCell(
                    label = stringResource(R.string.portfolio_total_fees),
                    value = state.totalCommissions.formatDisplay(),
                    modifier = Modifier.weight(1f)
                )
                MetricCell(
                    label = stringResource(R.string.portfolio_total_borrow_fees),
                    value = state.totalBorrowFees.formatDisplay(),
                    valueColor = if (state.totalBorrowFees.isPositive) {
                        TradingTheme.colors.warning
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            if (state.reservedCash.isPositive) {
                MetricCell(
                    label = stringResource(R.string.portfolio_reserved_cash),
                    value = state.reservedCash.formatDisplay(),
                    valueColor = TradingTheme.colors.warning
                )
            }
        }
    }
}

@Composable
private fun PortfolioSectionTabBar(
    selectedTab: PortfolioSectionTab,
    positionsCount: Int,
    ordersCount: Int,
    historyCount: Int,
    onSelectTab: (PortfolioSectionTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedTab == PortfolioSectionTab.POSITIONS,
            onClick = { onSelectTab(PortfolioSectionTab.POSITIONS) },
            label = { Text(stringResource(R.string.portfolio_tab_positions, positionsCount)) },
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("portfolio_tab_positions")
        )
        FilterChip(
            selected = selectedTab == PortfolioSectionTab.OPEN_ORDERS,
            onClick = { onSelectTab(PortfolioSectionTab.OPEN_ORDERS) },
            label = { Text(stringResource(R.string.portfolio_tab_orders, ordersCount)) },
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("portfolio_tab_orders")
        )
        FilterChip(
            selected = selectedTab == PortfolioSectionTab.HISTORY,
            onClick = { onSelectTab(PortfolioSectionTab.HISTORY) },
            label = { Text(stringResource(R.string.portfolio_tab_history, historyCount)) },
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("portfolio_tab_history")
        )
    }
}
