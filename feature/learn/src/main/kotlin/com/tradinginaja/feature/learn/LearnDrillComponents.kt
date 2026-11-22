package com.tradinginaja.feature.learn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CandlestickChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.core.designsystem.component.CandlestickChart
import com.tradinginaja.core.designsystem.component.ChartDisplayMode
import com.tradinginaja.core.designsystem.component.TradingTermChip
import com.tradinginaja.core.designsystem.component.TradingTermTooltipButton
import com.tradinginaja.core.designsystem.theme.TradingNumberTextStyle
import com.tradinginaja.core.designsystem.theme.TradingTheme
import com.tradinginaja.domain.model.CandlePatternType
import com.tradinginaja.domain.model.LearnLesson
import com.tradinginaja.domain.model.LearnLessonIds
import com.tradinginaja.domain.model.MissionId
import com.tradinginaja.domain.model.MissionProgress
import com.tradinginaja.domain.model.TradingTerm
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun MissionChecklistRow(mission: MissionProgress) {
    val (title, progressLabel) = missionDisplayText(mission)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mission_row_${mission.id.name.lowercase()}"),
        shape = MaterialTheme.shapes.small,
        color = if (mission.isCompleted) {
            TradingTheme.colors.bullishContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (mission.isCompleted) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        tint = if (mission.isCompleted) {
                            TradingTheme.colors.bullish
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = progressLabel,
                    style = TradingNumberTextStyle.copy(
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                        color = if (mission.isCompleted) {
                            TradingTheme.colors.bullish
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                )
            }
            LinearProgressIndicator(
                progress = { mission.progressFraction },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun CandleReadingDrillCard(state: LearnUiState.Ready, onEvent: (LearnUiEvent) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("learn_candle_drill_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CandlestickChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.learn_drill_candle_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(
                                R.string.learn_drill_candle_score,
                                state.learningProgress.candleDrillsPassed
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = TradingTheme.colors.bullish
                        )
                    }
                }
                TradingTermTooltipButton(term = TradingTerm.CANDLESTICK_OHLC)
            }

            Text(
                text = stringResource(R.string.learn_drill_candle_prompt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            CandlestickChart(
                candles = state.candleDrill.candles,
                displayMode = ChartDisplayMode.CANDLESTICK,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
            )

            state.candleDrill.options.forEach { option ->
                val isSelected = state.selectedPatternAnswer == option
                FilterChip(
                    selected = isSelected,
                    onClick = { onEvent(LearnUiEvent.AnswerCandleDrill(option)) },
                    label = { Text(text = patternLabel(option)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("candle_option_${option.name.lowercase()}")
                )
            }

            state.isDrillCorrect?.let { correct ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (correct) {
                        TradingTheme.colors.bullishContainer.copy(alpha = 0.7f)
                    } else {
                        TradingTheme.colors.bearishContainer.copy(alpha = 0.7f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            if (correct) R.string.learn_drill_correct else R.string.learn_drill_incorrect,
                            patternExplanation(state.candleDrill.correctPattern)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            OutlinedButton(
                onClick = { onEvent(LearnUiEvent.NextCandleDrill) },
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("learn_next_candle_drill")
            ) {
                Text(stringResource(R.string.learn_drill_next))
            }
        }
    }
}

@Composable
internal fun RiskSizingSandboxCard(state: LearnUiState.Ready, onEvent: (LearnUiEvent) -> Unit) {
    val sliderSteps = ((GameBalance.LEARN_SANDBOX_MAX_RISK_PCT - GameBalance.LEARN_SANDBOX_MIN_RISK_PCT) / 0.5)
        .roundToInt() - 1
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("learn_sizing_sandbox_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Calculate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.learn_sizing_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                TradingTermTooltipButton(term = TradingTerm.POSITION_SIZING)
            }

            Text(
                text = String.format(
                    Locale.US,
                    stringResource(R.string.learn_sizing_risk_slider),
                    state.sizingRiskPct
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (state.sizingRiskPct <= GameBalance.MAX_DISCIPLINED_RISK_PER_TRADE_PCT) {
                    TradingTheme.colors.bullish
                } else {
                    TradingTheme.colors.bearish
                }
            )
            Slider(
                value = state.sizingRiskPct.toFloat(),
                onValueChange = { onEvent(LearnUiEvent.UpdateRiskPct(it.toDouble())) },
                valueRange =
                GameBalance.LEARN_SANDBOX_MIN_RISK_PCT.toFloat()..GameBalance.LEARN_SANDBOX_MAX_RISK_PCT.toFloat(),
                steps = sliderSteps,
                modifier = Modifier.testTag("learn_sizing_slider")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.sizingEntryInput,
                    onValueChange = { onEvent(LearnUiEvent.UpdateEntryPrice(it)) },
                    label = { Text(stringResource(R.string.learn_sizing_entry_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("learn_sizing_entry_input")
                )
                OutlinedTextField(
                    value = state.sizingStopLossInput,
                    onValueChange = { onEvent(LearnUiEvent.UpdateStopLossPrice(it)) },
                    label = { Text(stringResource(R.string.learn_sizing_sl_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("learn_sizing_sl_input")
                )
            }

            val sim = state.sizingResult
            if (sim != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (sim.isWithinRule) {
                        TradingTheme.colors.bullishContainer.copy(alpha = 0.4f)
                    } else {
                        TradingTheme.colors.bearishContainer.copy(alpha = 0.45f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.learn_sizing_recommended_units, sim.recommendedUnits),
                            style = TradingNumberTextStyle.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                stringResource(R.string.learn_sizing_budget_detail),
                                sim.actualRiskAmount.formatDisplay(),
                                sim.actualRiskPct
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(
                                if (sim.isWithinRule) {
                                    R.string.learn_sizing_rule_ok
                                } else {
                                    R.string.learn_sizing_rule_warning
                                }
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (sim.isWithinRule) {
                                TradingTheme.colors.bullish
                            } else {
                                TradingTheme.colors.bearish
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LessonModuleCard(lesson: LearnLesson, isCompleted: Boolean, onEvent: (LearnUiEvent) -> Unit) {
    val (title, body) = lessonText(lesson.id)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lesson_card_${lesson.id}"),
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
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.learn_lesson_level_tag, lesson.recommendedForLevel.levelNumber),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                lesson.highlightedTerms.forEach { term ->
                    TradingTermChip(
                        term = term,
                        onClick = { onEvent(LearnUiEvent.OpenGlossaryTerm(term)) }
                    )
                }
            }
            if (isCompleted) {
                Text(
                    text = stringResource(R.string.learn_lesson_completed_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = TradingTheme.colors.bullish
                )
            } else {
                Button(
                    onClick = { onEvent(LearnUiEvent.MarkLessonCompleted(lesson.id)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("complete_${lesson.id}")
                ) {
                    Text(stringResource(R.string.learn_lesson_mark_done))
                }
            }
        }
    }
}

private val noArgMissionTitles = mapOf(
    MissionId.COMPLETE_BASICS_LESSON to R.string.mission_basics_lesson,
    MissionId.COMPLETE_RISK_LESSON to R.string.mission_risk_lesson,
    MissionId.COMPLETE_MARGIN_LESSON to R.string.mission_margin_lesson,
    MissionId.ZERO_LIQUIDATIONS to R.string.mission_zero_liquidations
)

private val intArgMissionTitles = mapOf(
    MissionId.EXECUTE_MIN_TRADES to R.string.mission_min_trades,
    MissionId.DIVERSIFY_ASSETS to R.string.mission_diversify_assets,
    MissionId.USE_STOP_LOSS to R.string.mission_use_stop_loss,
    MissionId.PASS_CANDLE_DRILLS to R.string.mission_candle_drills,
    MissionId.EXECUTE_LIMIT_OR_STOP_FILLS to R.string.mission_limit_stop_fills,
    MissionId.PRACTICE_DISCIPLINED_RISK_SIZE to R.string.mission_disciplined_risk,
    MissionId.EXECUTE_SHORT_OR_HEDGE to R.string.mission_short_hedge,
    MissionId.DIVERSIFY_ASSET_CLASSES to R.string.mission_diversify_classes
)

private val doubleArgMissionTitles = mapOf(
    MissionId.KEEP_DRAWDOWN_LIMIT to R.string.mission_max_drawdown,
    MissionId.ACHIEVE_RISK_REWARD to R.string.mission_risk_reward,
    MissionId.ACHIEVE_WIN_RATE to R.string.mission_win_rate
)

@Composable
private fun missionDisplayText(mission: MissionProgress): Pair<String, String> {
    val title = noArgMissionTitles[mission.id]?.let { stringResource(it) }
        ?: intArgMissionTitles[mission.id]?.let { stringResource(it, mission.targetValue.roundToInt()) }
        ?: doubleArgMissionTitles[mission.id]?.let { stringResource(it, mission.targetValue) }
        ?: mission.id.name
    return title to missionMetricText(mission)
}

private fun missionMetricText(mission: MissionProgress): String = when (mission.id) {
    MissionId.KEEP_DRAWDOWN_LIMIT ->
        String.format(Locale.US, "%.1f%% / <=%.0f%%", mission.currentValue, mission.targetValue)
    MissionId.ACHIEVE_RISK_REWARD ->
        String.format(Locale.US, "%.2f / %.1f", mission.currentValue, mission.targetValue)
    MissionId.ACHIEVE_WIN_RATE ->
        String.format(Locale.US, "%.0f%% / %.0f%%", mission.currentValue, mission.targetValue)
    else -> formatCount(mission)
}

private fun formatCount(mission: MissionProgress): String =
    "${mission.currentValue.roundToInt()} / ${mission.targetValue.roundToInt()}"

@Composable
private fun patternLabel(pattern: CandlePatternType): String = when (pattern) {
    CandlePatternType.BULLISH_MOMENTUM -> stringResource(R.string.learn_pattern_bullish_momentum)
    CandlePatternType.BEARISH_REJECTION_WICK -> stringResource(R.string.learn_pattern_bearish_wick)
    CandlePatternType.BULLISH_HAMMER_REVERSAL -> stringResource(R.string.learn_pattern_hammer_reversal)
    CandlePatternType.VOLATILE_INDECISION -> stringResource(R.string.learn_pattern_doji_indecision)
}

@Composable
private fun patternExplanation(pattern: CandlePatternType): String = when (pattern) {
    CandlePatternType.BULLISH_MOMENTUM -> stringResource(R.string.learn_pattern_bullish_momentum_desc)
    CandlePatternType.BEARISH_REJECTION_WICK -> stringResource(R.string.learn_pattern_bearish_wick_desc)
    CandlePatternType.BULLISH_HAMMER_REVERSAL -> stringResource(R.string.learn_pattern_hammer_reversal_desc)
    CandlePatternType.VOLATILE_INDECISION -> stringResource(R.string.learn_pattern_doji_indecision_desc)
}

@Composable
private fun lessonText(lessonId: String): Pair<String, String> = when (lessonId) {
    LearnLessonIds.BASICS_SPREAD_SL ->
        stringResource(R.string.lesson_basics_title) to stringResource(R.string.lesson_basics_body)
    LearnLessonIds.RISK_ONE_TWO_PERCENT ->
        stringResource(R.string.lesson_risk_title) to stringResource(R.string.lesson_risk_body)
    LearnLessonIds.CANDLE_AND_REGIME ->
        stringResource(R.string.lesson_candle_title) to stringResource(R.string.lesson_candle_body)
    LearnLessonIds.MARGIN_ANTI_LIQUIDATION ->
        stringResource(R.string.lesson_margin_title) to stringResource(R.string.lesson_margin_body)
    else -> lessonId to ""
}
