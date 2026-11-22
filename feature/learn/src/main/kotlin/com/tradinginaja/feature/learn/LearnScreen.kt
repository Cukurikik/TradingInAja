package com.tradinginaja.feature.learn

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
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.tradinginaja.core.designsystem.component.SimulationDisclaimerBanner
import com.tradinginaja.core.designsystem.component.TradingTermChip
import com.tradinginaja.core.designsystem.component.TradingTermDialog
import com.tradinginaja.core.designsystem.component.careerLevelTitleRes
import com.tradinginaja.core.designsystem.theme.TradingTheme
import com.tradinginaja.domain.model.TradingTerm

private val MAX_LEARN_WIDTH = 760.dp

@Composable
fun LearnRoute(
    onReplayTutorial: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LearnViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LearnScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onReplayTutorial = onReplayTutorial,
        modifier = modifier
    )
}

@Composable
fun LearnScreen(
    uiState: LearnUiState,
    onEvent: (LearnUiEvent) -> Unit,
    onReplayTutorial: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("learn_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        when (uiState) {
            LearnUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.learn_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is LearnUiState.Ready -> {
                LearnContent(
                    uiState = uiState,
                    onEvent = onEvent,
                    onReplayTutorial = onReplayTutorial
                )
                uiState.activeGlossaryTerm?.let { term ->
                    TradingTermDialog(
                        term = term,
                        onDismiss = { onEvent(LearnUiEvent.DismissGlossaryTerm) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LearnContent(uiState: LearnUiState.Ready, onEvent: (LearnUiEvent) -> Unit, onReplayTutorial: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = MAX_LEARN_WIDTH),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "career_header") {
            CareerProgressionCard(
                uiState = uiState,
                onReplayTutorial = onReplayTutorial
            )
        }

        item(key = "learn_tabs") {
            LearnTabSelector(
                selectedTab = uiState.selectedTab,
                onSelectTab = { onEvent(LearnUiEvent.SelectTab(it)) }
            )
        }

        when (uiState.selectedTab) {
            LearnSectionTab.MISSIONS -> {
                item(key = "missions_header") {
                    Text(
                        text = uiState.careerProgress.nextLevel?.let { next ->
                            stringResource(
                                R.string.learn_missions_next_level_title,
                                next.levelNumber,
                                stringResource(careerLevelTitleRes(next))
                            )
                        } ?: stringResource(R.string.learn_missions_max_level_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                if (uiState.careerProgress.missionsForNextLevel.isEmpty()) {
                    item(key = "max_level_notice") {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = TradingTheme.colors.bullishContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.learn_missions_max_level_body),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                } else {
                    items(uiState.careerProgress.missionsForNextLevel, key = { it.id.name }) { mission ->
                        MissionChecklistRow(mission = mission)
                    }
                }
                item(key = "post_mortem_digest") {
                    PostMortemLearningSummaryCard(uiState = uiState)
                }
            }

            LearnSectionTab.RISK_FREE_LAB -> {
                item(key = "sandbox_banner") {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = TradingTheme.colors.bullishContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = TradingTheme.colors.bullish,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.learn_risk_free_banner),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                item(key = "candle_drill") {
                    CandleReadingDrillCard(state = uiState, onEvent = onEvent)
                }
                item(key = "sizing_sandbox") {
                    RiskSizingSandboxCard(state = uiState, onEvent = onEvent)
                }
            }

            LearnSectionTab.LESSONS_GLOSSARY -> {
                items(uiState.lessons, key = { it.id }) { lesson ->
                    LessonModuleCard(
                        lesson = lesson,
                        isCompleted = uiState.learningProgress.completedLessonIds.contains(lesson.id),
                        onEvent = onEvent
                    )
                }
                item(key = "glossary_hub") {
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
                                text = stringResource(R.string.learn_glossary_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = stringResource(R.string.learn_glossary_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TradingTerm.entries.forEach { term ->
                                    TradingTermChip(
                                        term = term,
                                        onClick = { onEvent(LearnUiEvent.OpenGlossaryTerm(term)) }
                                    )
                                }
                            }
                        }
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
private fun CareerProgressionCard(uiState: LearnUiState.Ready, onReplayTutorial: () -> Unit) {
    val current = uiState.careerProgress.currentLevel
    val shortLabel = stringResource(
        if (current.allowsShortSelling) R.string.learn_short_unlocked else R.string.learn_short_locked
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("learn_career_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MilitaryTech,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(
                                R.string.learn_career_level_header,
                                current.levelNumber,
                                stringResource(careerLevelTitleRes(current))
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(
                                R.string.learn_career_privileges,
                                current.maxLeverage,
                                shortLabel
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = onReplayTutorial,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("learn_replay_tutorial_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.learn_replay_tutorial),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.learn_competency_principle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PostMortemLearningSummaryCard(uiState: LearnUiState.Ready) {
    val summary = uiState.postMortemSummary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("learn_post_mortem_summary"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.learn_post_mortem_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = if (summary.totalLosingTrades == 0) {
                    stringResource(R.string.learn_post_mortem_empty)
                } else {
                    stringResource(
                        R.string.learn_post_mortem_stats,
                        summary.totalLosingTrades,
                        summary.unhedgedLossCount,
                        summary.overLeveragedLossCount,
                        summary.disciplinedStopLossCount
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LearnTabSelector(selectedTab: LearnSectionTab, onSelectTab: (LearnSectionTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedTab == LearnSectionTab.MISSIONS,
            onClick = { onSelectTab(LearnSectionTab.MISSIONS) },
            label = { Text(stringResource(R.string.learn_tab_missions)) },
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("learn_tab_missions")
        )
        FilterChip(
            selected = selectedTab == LearnSectionTab.RISK_FREE_LAB,
            onClick = { onSelectTab(LearnSectionTab.RISK_FREE_LAB) },
            label = { Text(stringResource(R.string.learn_tab_risk_free)) },
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("learn_tab_risk_free")
        )
        FilterChip(
            selected = selectedTab == LearnSectionTab.LESSONS_GLOSSARY,
            onClick = { onSelectTab(LearnSectionTab.LESSONS_GLOSSARY) },
            label = { Text(stringResource(R.string.learn_tab_lessons)) },
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("learn_tab_lessons")
        )
    }
}
