package com.tradinginaja.feature.market

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tradinginaja.core.designsystem.component.SimulationDisclaimerBanner
import com.tradinginaja.core.designsystem.theme.TradingTheme
import com.tradinginaja.domain.model.EventTargetScope
import com.tradinginaja.domain.model.MarketRegime
import com.tradinginaja.domain.model.SimulationSpeed

private val MAX_NEWS_CONTENT_WIDTH = 760.dp
private const val INDONESIAN_LANG_CODE = "in"
private const val INDONESIAN_ISO_CODE = "id"

@Composable
fun NewsRoute(
    symbolFilter: String?,
    onNavigateBack: () -> Unit,
    onAssetClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewsViewModel = hiltViewModel()
) {
    LaunchedEffect(symbolFilter) {
        viewModel.onEvent(NewsUiEvent.SelectSymbolFilter(symbolFilter))
    }
    BackHandler(onBack = onNavigateBack)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NewsScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateBack = onNavigateBack,
        onAssetClick = onAssetClick,
        modifier = modifier
    )
}

@Composable
fun NewsScreen(
    uiState: NewsUiState,
    onEvent: (NewsUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    onAssetClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val currentLocale = configuration.locales.get(0)?.language.orEmpty().lowercase()
    val isIndonesian = currentLocale == INDONESIAN_LANG_CODE || currentLocale == INDONESIAN_ISO_CODE

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("news_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        when (uiState) {
            NewsUiState.Loading -> {
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

            is NewsUiState.Ready -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = MAX_NEWS_CONTENT_WIDTH),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "news_header") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            NewsTopRow(
                                tickIndex = uiState.tickIndex,
                                onNavigateBack = onNavigateBack
                            )
                            RegimeClimateCard(
                                regime = uiState.currentRegime,
                                ticksInRegime = uiState.ticksInRegime
                            )
                            NewsSimulationToolbar(
                                currentSpeed = uiState.simulationSpeed,
                                onSelectSpeed = { onEvent(NewsUiEvent.SelectSpeed(it)) },
                                onStepOnce = { onEvent(NewsUiEvent.StepOnce) }
                            )
                            NewsFilterBar(
                                uiState = uiState,
                                onSelectMode = { onEvent(NewsUiEvent.SelectFilterMode(it)) },
                                onSelectSymbol = { onEvent(NewsUiEvent.SelectSymbolFilter(it)) }
                            )
                        }
                    }

                    if (uiState.articles.isEmpty()) {
                        item(key = "news_empty") {
                            EmptyNewsCard()
                        }
                    } else {
                        items(
                            items = uiState.articles,
                            key = { it.newsId }
                        ) { article ->
                            NewsArticleCard(
                                article = article,
                                isIndonesian = isIndonesian,
                                onAssetClick = onAssetClick
                            )
                        }
                    }

                    item(key = "news_disclaimer") {
                        Spacer(modifier = Modifier.height(4.dp))
                        SimulationDisclaimerBanner()
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsTopRow(tickIndex: Long, onNavigateBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("news_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.news_back_to_market)
                )
            }
            Text(
                text = stringResource(R.string.news_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

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
                    .testTag("news_tick_counter")
            )
        }
    }
}

@Composable
private fun RegimeClimateCard(regime: MarketRegime, ticksInRegime: Long) {
    val labelRes = when (regime) {
        MarketRegime.BULL -> R.string.news_regime_bull_label
        MarketRegime.BEAR -> R.string.news_regime_bear_label
        MarketRegime.SIDEWAYS -> R.string.news_regime_sideways_label
        MarketRegime.CRISIS -> R.string.news_regime_crisis_label
    }
    val descRes = when (regime) {
        MarketRegime.BULL -> R.string.news_regime_bull_desc
        MarketRegime.BEAR -> R.string.news_regime_bear_desc
        MarketRegime.SIDEWAYS -> R.string.news_regime_sideways_desc
        MarketRegime.CRISIS -> R.string.news_regime_crisis_desc
    }
    val containerColor = when (regime) {
        MarketRegime.CRISIS -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        MarketRegime.BULL -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        MarketRegime.BEAR, MarketRegime.SIDEWAYS -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("news_regime_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.news_regime_card_title, stringResource(labelRes)),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = stringResource(R.string.news_regime_duration_ticks, ticksInRegime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(descRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NewsSimulationToolbar(
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
                        .testTag("news_speed_${speed.name.lowercase()}")
                )
            }

            OutlinedButton(
                onClick = onStepOnce,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("news_step_once")
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
private fun NewsFilterBar(
    uiState: NewsUiState.Ready,
    onSelectMode: (NewsFilterMode) -> Unit,
    onSelectSymbol: (String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.filterMode == NewsFilterMode.ALL,
                onClick = { onSelectMode(NewsFilterMode.ALL) },
                label = { Text(stringResource(R.string.news_filter_all, uiState.totalNewsCount)) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("news_filter_all")
            )
            FilterChip(
                selected = uiState.filterMode == NewsFilterMode.ACTIVE_ONLY,
                onClick = { onSelectMode(NewsFilterMode.ACTIVE_ONLY) },
                label = { Text(stringResource(R.string.news_filter_active, uiState.activeNewsCount)) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("news_filter_active")
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = uiState.selectedSymbolFilter == null,
                onClick = { onSelectSymbol(null) },
                label = { Text(stringResource(R.string.news_filter_all_symbols)) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("news_symbol_all")
            )
            uiState.availableSymbols.forEach { symbol ->
                FilterChip(
                    selected = uiState.selectedSymbolFilter == symbol,
                    onClick = {
                        val next = if (uiState.selectedSymbolFilter == symbol) null else symbol
                        onSelectSymbol(next)
                    },
                    label = { Text(symbol) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("news_symbol_$symbol")
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewsArticleCard(article: NewsArticleUiItem, isIndonesian: Boolean, onAssetClick: (String) -> Unit) {
    val scopeLabel = when (article.targetScope) {
        EventTargetScope.ALL -> stringResource(R.string.news_scope_all)
        EventTargetScope.CATEGORY -> stringResource(R.string.news_scope_category, article.targetValue.orEmpty())
        EventTargetScope.SECTOR -> stringResource(R.string.news_scope_sector, article.targetValue.orEmpty())
        EventTargetScope.SYMBOL -> stringResource(R.string.news_scope_symbol, article.targetValue.orEmpty())
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("news_card_${article.newsId}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = article.eventTypeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = scopeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.news_tick_published, article.tickIndex),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (article.isActive) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ) {
                        Text(
                            text = stringResource(
                                if (article.isActive) R.string.news_status_active else R.string.news_status_archived
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (article.isActive) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = article.localizedHeadline(isIndonesian),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = article.localizedSummary(isIndonesian),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Balance,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.news_perspectives_header),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "${stringResource(R.string.news_perspective_optimistic)}: " +
                            article.localizedBullCase(isIndonesian),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${stringResource(R.string.news_perspective_cautious)}: " +
                            article.localizedBearCase(isIndonesian),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.news_interpretation_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (article.affectedSymbols.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.news_affected_assets),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    article.affectedSymbols.forEach { symbol ->
                        AssistChip(
                            onClick = { onAssetClick(symbol) },
                            label = { Text(symbol) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("news_asset_chip_${article.newsId}_$symbol")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNewsCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("news_empty_state"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Newspaper,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.news_empty_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.news_empty_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
