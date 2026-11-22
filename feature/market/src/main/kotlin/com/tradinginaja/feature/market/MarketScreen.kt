package com.tradinginaja.feature.market

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.CandlestickChart
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tradinginaja.core.designsystem.component.CandlestickChart
import com.tradinginaja.core.designsystem.component.ChartDisplayMode
import com.tradinginaja.core.designsystem.component.SimulationDisclaimerBanner
import com.tradinginaja.core.designsystem.theme.TradingNumberTextStyle
import com.tradinginaja.core.designsystem.theme.TradingTheme
import com.tradinginaja.domain.model.CandleTimeframe
import com.tradinginaja.domain.model.SimulationSpeed
import java.util.Locale

private val MAX_CONTENT_WIDTH = 720.dp

@Composable
fun MarketDetailRoute(
    symbol: String,
    onNavigateBack: () -> Unit,
    onTradeAsset: (String) -> Unit = {},
    onOpenRelatedNews: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MarketDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(symbol) {
        viewModel.onEvent(MarketDetailUiEvent.SelectAsset(symbol))
    }
    BackHandler(onBack = onNavigateBack)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MarketDetailScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateBack = onNavigateBack,
        onTradeAsset = onTradeAsset,
        onOpenRelatedNews = onOpenRelatedNews,
        modifier = modifier
    )
}

@Composable
fun MarketDetailScreen(
    uiState: MarketDetailUiState,
    onEvent: (MarketDetailUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    onTradeAsset: (String) -> Unit = {},
    onOpenRelatedNews: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("market_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        when (uiState) {
            MarketDetailUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.market_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is MarketDetailUiState.Ready -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = MAX_CONTENT_WIDTH),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MarketDetailTopRow(
                        uiState = uiState,
                        onNavigateBack = onNavigateBack,
                        onToggleWatchlist = { onEvent(MarketDetailUiEvent.ToggleWatchlist) }
                    )
                    AssetPriceHeaderCard(uiState = uiState)
                    MarketStatsCard(uiState = uiState)
                    ChartControlsBar(
                        selectedTimeframe = uiState.selectedTimeframe,
                        chartDisplayMode = uiState.chartDisplayMode,
                        onSelectTimeframe = { onEvent(MarketDetailUiEvent.SelectTimeframe(it)) },
                        onSelectChartMode = { onEvent(MarketDetailUiEvent.SelectChartMode(it)) }
                    )
                    key(uiState.spec.symbol) {
                        CandlestickChart(
                            candles = uiState.candles,
                            displayMode = uiState.chartDisplayMode
                        )
                    }
                    SimulationControlPanel(
                        currentSpeed = uiState.simulationSpeed,
                        onSelectSpeed = { onEvent(MarketDetailUiEvent.SelectSpeed(it)) },
                        onStepOnce = { onEvent(MarketDetailUiEvent.StepOnce) },
                        onReset = { onEvent(MarketDetailUiEvent.ResetSimulation) }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onTradeAsset(uiState.spec.symbol) },
                            modifier = Modifier
                                .weight(1.4f)
                                .minimumInteractiveComponentSize()
                                .testTag("market_detail_trade_cta")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.market_action_trade_asset, uiState.spec.symbol),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        OutlinedButton(
                            onClick = { onOpenRelatedNews(uiState.spec.symbol) },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("market_detail_news_cta")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Newspaper,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.market_detail_related_news, uiState.relatedNewsCount),
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                    SimulationDisclaimerBanner()
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun MarketDetailTopRow(
    uiState: MarketDetailUiState.Ready,
    onNavigateBack: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("market_detail_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.market_back_to_list)
                )
            }
            Text(
                text = stringResource(R.string.market_detail_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = stringResource(uiState.spec.priceModelLabelRes()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        IconButton(
            onClick = onToggleWatchlist,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("market_detail_watchlist_toggle")
        ) {
            Icon(
                imageVector = if (uiState.isWatchlisted) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = stringResource(
                    if (uiState.isWatchlisted) {
                        R.string.market_watchlist_remove
                    } else {
                        R.string.market_watchlist_add
                    },
                    uiState.spec.symbol
                ),
                tint = if (uiState.isWatchlisted) {
                    TradingTheme.colors.warning
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun AssetPriceHeaderCard(uiState: MarketDetailUiState.Ready) {
    val trendColor = if (uiState.isPositive) TradingTheme.colors.bullish else TradingTheme.colors.bearish
    val trendBg = if (uiState.isPositive) {
        TradingTheme.colors.bullishContainer
    } else {
        TradingTheme.colors.bearishContainer
    }
    val signPrefix = if (uiState.isPositive) "+" else ""
    val formattedPct = String.format(Locale.US, "%s%.2f%%", signPrefix, uiState.changePercent)

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = uiState.spec.symbol,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Column {
                        Text(
                            text = uiState.spec.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = uiState.spec.sector,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = stringResource(R.string.market_tick_counter, uiState.tickIndex),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("market_tick_counter")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = uiState.currentPrice.formatDisplay(),
                    style = TradingNumberTextStyle.copy(
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                        lineHeight = MaterialTheme.typography.headlineMedium.lineHeight,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.testTag("market_current_price")
                )

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = trendBg.copy(alpha = 0.65f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (uiState.isPositive) {
                                Icons.Rounded.ArrowDropUp
                            } else {
                                Icons.Rounded.ArrowDropDown
                            },
                            contentDescription = null,
                            tint = trendColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "$signPrefix${uiState.priceChange.formatDisplay()} ($formattedPct)",
                            style = MaterialTheme.typography.labelSmall,
                            color = trendColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketStatsCard(uiState: MarketDetailUiState.Ready) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatCell(
                    label = stringResource(R.string.market_stat_bid),
                    value = uiState.bidPrice.formatDisplay(),
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = stringResource(R.string.market_stat_ask),
                    value = uiState.askPrice.formatDisplay(),
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = stringResource(R.string.market_stat_spread),
                    value = stringResource(R.string.market_spread_bps_format, uiState.spreadBps),
                    modifier = Modifier.weight(0.75f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatCell(
                    label = stringResource(R.string.market_stat_high),
                    value = uiState.highSession.formatDisplay(),
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = stringResource(R.string.market_stat_low),
                    value = uiState.lowSession.formatDisplay(),
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = stringResource(R.string.market_stat_volume),
                    value = uiState.lastVolume.toString(),
                    modifier = Modifier.weight(0.75f)
                )
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChartControlsBar(
    selectedTimeframe: CandleTimeframe,
    chartDisplayMode: ChartDisplayMode,
    onSelectTimeframe: (CandleTimeframe) -> Unit,
    onSelectChartMode: (ChartDisplayMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CandleTimeframe.entries.forEach { timeframe ->
            FilterChip(
                selected = selectedTimeframe == timeframe,
                onClick = { onSelectTimeframe(timeframe) },
                label = { Text(timeframe.label) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("timeframe_${timeframe.label}")
            )
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))

        FilterChip(
            selected = chartDisplayMode == ChartDisplayMode.CANDLESTICK,
            onClick = { onSelectChartMode(ChartDisplayMode.CANDLESTICK) },
            label = { Text(stringResource(R.string.market_chart_mode_candles)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.CandlestickChart,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("chart_mode_candlestick")
        )

        FilterChip(
            selected = chartDisplayMode == ChartDisplayMode.LINE,
            onClick = { onSelectChartMode(ChartDisplayMode.LINE) },
            label = { Text(stringResource(R.string.market_chart_mode_line)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ShowChart,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("chart_mode_line")
        )
    }
}

@Composable
private fun SimulationControlPanel(
    currentSpeed: SimulationSpeed,
    onSelectSpeed: (SimulationSpeed) -> Unit,
    onStepOnce: () -> Unit,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.market_sim_controls_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpeedChip(
                    label = stringResource(R.string.market_speed_paused),
                    selected = currentSpeed == SimulationSpeed.PAUSED,
                    isPaused = true,
                    onClick = { onSelectSpeed(SimulationSpeed.PAUSED) },
                    testTag = "sim_speed_pause"
                )
                SpeedChip(
                    label = stringResource(R.string.market_speed_1x),
                    selected = currentSpeed == SimulationSpeed.SPEED_1X,
                    isPaused = false,
                    onClick = { onSelectSpeed(SimulationSpeed.SPEED_1X) },
                    testTag = "sim_speed_1x"
                )
                SpeedChip(
                    label = stringResource(R.string.market_speed_2x),
                    selected = currentSpeed == SimulationSpeed.SPEED_2X,
                    isPaused = false,
                    onClick = { onSelectSpeed(SimulationSpeed.SPEED_2X) },
                    testTag = "sim_speed_2x"
                )
                SpeedChip(
                    label = stringResource(R.string.market_speed_5x),
                    selected = currentSpeed == SimulationSpeed.SPEED_5X,
                    isPaused = false,
                    onClick = { onSelectSpeed(SimulationSpeed.SPEED_5X) },
                    testTag = "sim_speed_5x"
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onStepOnce,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("sim_step_once")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.market_action_step_once),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("sim_reset")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.market_action_reset),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedChip(label: String, selected: Boolean, isPaused: Boolean, onClick: () -> Unit, testTag: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label.trim()) },
        leadingIcon = {
            Icon(
                imageVector = if (isPaused) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    )
}
