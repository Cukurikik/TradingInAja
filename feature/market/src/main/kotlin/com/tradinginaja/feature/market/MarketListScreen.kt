package com.tradinginaja.feature.market

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tradinginaja.core.designsystem.component.SimulationDisclaimerBanner
import com.tradinginaja.core.designsystem.component.SparklineChart
import com.tradinginaja.core.designsystem.theme.TradingNumberTextStyle
import com.tradinginaja.core.designsystem.theme.TradingTheme
import com.tradinginaja.domain.model.MarketRegime
import com.tradinginaja.domain.model.SimulationSpeed
import java.util.Locale

private val MAX_LIST_CONTENT_WIDTH = 760.dp

@Composable
fun MarketListRoute(
    onAssetClick: (String) -> Unit,
    onOpenNews: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MarketListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MarketListScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onAssetClick = onAssetClick,
        onOpenNews = onOpenNews,
        modifier = modifier
    )
}

@Composable
fun MarketListScreen(
    uiState: MarketListUiState,
    onEvent: (MarketListUiEvent) -> Unit,
    onAssetClick: (String) -> Unit,
    onOpenNews: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("market_list_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        when (uiState) {
            MarketListUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.market_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is MarketListUiState.Ready -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = MAX_LIST_CONTENT_WIDTH),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "header_controls") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            MarketSearchField(
                                query = uiState.searchQuery,
                                onQueryChanged = { onEvent(MarketListUiEvent.SearchQueryChanged(it)) }
                            )
                            MarketFilterBar(
                                selectedFilter = uiState.selectedFilter,
                                watchlistCount = uiState.watchlistCount,
                                onSelectFilter = { onEvent(MarketListUiEvent.SelectFilter(it)) }
                            )
                            CompactSimulationToolbar(
                                tickIndex = uiState.tickIndex,
                                currentSpeed = uiState.simulationSpeed,
                                onSelectSpeed = { onEvent(MarketListUiEvent.SelectSpeed(it)) },
                                onStepOnce = { onEvent(MarketListUiEvent.StepOnce) }
                            )
                            MarketNewsPulseCard(
                                uiState = uiState,
                                onOpenNews = onOpenNews
                            )
                        }
                    }

                    if (uiState.items.isEmpty()) {
                        item(key = "empty_state") {
                            EmptyMarketCard(isWatchlistFilter = uiState.selectedFilter == MarketFilter.WATCHLIST)
                        }
                    } else {
                        items(
                            items = uiState.items,
                            key = { it.spec.symbol }
                        ) { item ->
                            MarketAssetCard(
                                item = item,
                                onClick = { onAssetClick(item.spec.symbol) },
                                onToggleWatchlist = {
                                    onEvent(MarketListUiEvent.ToggleWatchlist(item.spec.symbol))
                                }
                            )
                        }
                    }

                    item(key = "disclaimer") {
                        Spacer(modifier = Modifier.height(4.dp))
                        SimulationDisclaimerBanner()
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketSearchField(query: String, onQueryChanged: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("market_search_input"),
        placeholder = { Text(text = stringResource(R.string.market_search_placeholder)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChanged("") },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("market_search_clear")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = stringResource(R.string.market_search_clear)
                    )
                }
            }
        },
        shape = MaterialTheme.shapes.medium
    )
}

@Composable
private fun MarketFilterBar(selectedFilter: MarketFilter, watchlistCount: Int, onSelectFilter: (MarketFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MarketFilter.entries.forEach { filter ->
            val labelText = if (filter == MarketFilter.WATCHLIST && watchlistCount > 0) {
                "${stringResource(filter.labelRes)} ($watchlistCount)"
            } else {
                stringResource(filter.labelRes)
            }
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onSelectFilter(filter) },
                label = { Text(labelText) },
                leadingIcon = if (filter == MarketFilter.WATCHLIST) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("market_filter_${filter.name.lowercase()}")
            )
        }
    }
}

@Composable
private fun CompactSimulationToolbar(
    tickIndex: Long,
    currentSpeed: SimulationSpeed,
    onSelectSpeed: (SimulationSpeed) -> Unit,
    onStepOnce: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = stringResource(R.string.market_tick_counter, tickIndex),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("market_list_tick_counter")
                )
            }

            SimulationSpeed.entries.forEach { speed ->
                val isPaused = speed == SimulationSpeed.PAUSED
                val labelRes = when (speed) {
                    SimulationSpeed.PAUSED -> R.string.market_speed_paused
                    SimulationSpeed.SPEED_1X -> R.string.market_speed_1x
                    SimulationSpeed.SPEED_2X -> R.string.market_speed_2x
                    SimulationSpeed.SPEED_5X -> R.string.market_speed_5x
                }
                FilterChip(
                    selected = currentSpeed == speed,
                    onClick = { onSelectSpeed(speed) },
                    label = { Text(stringResource(labelRes)) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isPaused) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("list_speed_${speed.name.lowercase()}")
                )
            }

            OutlinedButton(
                onClick = onStepOnce,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("list_step_once")
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.market_action_step_once),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MarketAssetCard(item: MarketAssetRowItem, onClick: () -> Unit, onToggleWatchlist: () -> Unit) {
    val trendColor = if (item.isPositive) TradingTheme.colors.bullish else TradingTheme.colors.bearish
    val trendBg = if (item.isPositive) {
        TradingTheme.colors.bullishContainer
    } else {
        TradingTheme.colors.bearishContainer
    }
    val signPrefix = if (item.isPositive) "+" else ""
    val formattedPct = String.format(Locale.US, "%s%.2f%%", signPrefix, item.changePercent)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("market_asset_card_${item.spec.symbol}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = onToggleWatchlist,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("watchlist_toggle_${item.spec.symbol}")
            ) {
                Icon(
                    imageVector = if (item.isWatchlisted) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = stringResource(
                        if (item.isWatchlisted) {
                            R.string.market_watchlist_remove
                        } else {
                            R.string.market_watchlist_add
                        },
                        item.spec.symbol
                    ),
                    tint = if (item.isWatchlisted) {
                        TradingTheme.colors.warning
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.spec.symbol,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = stringResource(item.spec.priceModelLabelRes()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = item.spec.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.spec.sector,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            SparklineChart(
                prices = item.sparklinePrices,
                isPositive = item.isPositive
            )

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.currentPrice.formatDisplay(),
                    style = TradingNumberTextStyle.copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = trendBg.copy(alpha = 0.65f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (item.isPositive) {
                                Icons.Rounded.ArrowDropUp
                            } else {
                                Icons.Rounded.ArrowDropDown
                            },
                            contentDescription = null,
                            tint = trendColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = formattedPct,
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
private fun EmptyMarketCard(isWatchlistFilter: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("market_empty_state"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.market_empty_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(
                    if (isWatchlistFilter) {
                        R.string.market_empty_watchlist
                    } else {
                        R.string.market_empty_subtitle
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MarketNewsPulseCard(uiState: MarketListUiState.Ready, onOpenNews: () -> Unit) {
    val language = LocalConfiguration.current.locales.get(0)?.language.orEmpty().lowercase()
    val isIndonesian = language == "in" || language == "id"
    val previewHeadline = if (isIndonesian) uiState.latestNewsHeadlineId else uiState.latestNewsHeadlineEn
    val regimeLabelRes = when (uiState.currentRegime) {
        MarketRegime.BULL -> R.string.news_regime_bull_label
        MarketRegime.BEAR -> R.string.news_regime_bear_label
        MarketRegime.SIDEWAYS -> R.string.news_regime_sideways_label
        MarketRegime.CRISIS -> R.string.news_regime_crisis_label
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onOpenNews)
            .testTag("market_open_news_button"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Newspaper,
                contentDescription = stringResource(R.string.market_news_banner_title),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.market_news_banner_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = stringResource(regimeLabelRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    text = previewHeadline ?: stringResource(R.string.market_news_banner_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (uiState.activeNewsCount > 0) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.market_news_active_badge, uiState.activeNewsCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
