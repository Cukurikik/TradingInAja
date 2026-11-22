package com.tradinginaja.feature.trade

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.tradinginaja.core.designsystem.component.TradingTermTooltipButton
import com.tradinginaja.core.designsystem.component.careerLevelTitleRes
import com.tradinginaja.core.designsystem.theme.TradingTheme
import com.tradinginaja.domain.model.CareerLevel
import com.tradinginaja.domain.model.MarginStatus
import com.tradinginaja.domain.model.OrderType
import com.tradinginaja.domain.model.TradingTerm
import java.util.Locale

private val LEVERAGE_OPTIONS = listOf(1, 2, 3, 5)
private const val PRESET_MINUS_2_BPS = -200L
private const val PRESET_PLUS_2_BPS = 200L

@Composable
internal fun CareerRiskTierCard(careerLevel: CareerLevel, onSelectLevel: (CareerLevel) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trade_career_tier_card"),
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(
                            R.string.trade_career_tier_title_named,
                            careerLevel.levelNumber,
                            stringResource(careerLevelTitleRes(careerLevel)),
                            careerLevel.maxLeverage
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                text = stringResource(
                    if (careerLevel.allowsShortSelling) {
                        R.string.trade_career_short_unlocked
                    } else {
                        R.string.trade_career_short_locked
                    }
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (careerLevel.allowsShortSelling) {
                    TradingTheme.colors.bullish
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = stringResource(R.string.trade_career_competency_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CareerLevel.entries.forEach { level ->
                    FilterChip(
                        selected = level == careerLevel,
                        onClick = { onSelectLevel(level) },
                        label = {
                            Text(
                                text = stringResource(R.string.trade_career_level_short, level.levelNumber)
                            )
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("trade_career_level_${level.levelNumber}")
                    )
                }
            }
        }
    }
}

@Composable
internal fun MarginCallAlertBanner(marginLevelPct: Double?, status: MarginStatus) {
    if (status == MarginStatus.HEALTHY || marginLevelPct == null) return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trade_margin_call_banner"),
        shape = MaterialTheme.shapes.medium,
        color = TradingTheme.colors.bearishContainer
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = TradingTheme.colors.bearish,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = String.format(
                    Locale.US,
                    stringResource(R.string.trade_margin_call_banner),
                    marginLevelPct
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
internal fun LeverageSelectorRow(selectedLeverage: Int, careerLevel: CareerLevel, onSelectLeverage: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    R.string.trade_leverage_label,
                    careerLevel.maxLeverage,
                    careerLevel.levelNumber
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TradingTermTooltipButton(term = TradingTerm.LEVERAGE)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LEVERAGE_OPTIONS.forEach { lev ->
                FilterChip(
                    selected = selectedLeverage == lev,
                    onClick = { onSelectLeverage(lev) },
                    label = { Text(stringResource(R.string.trade_leverage_chip, lev)) },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("order_leverage_$lev")
                )
            }
        }
    }
}

@Composable
internal fun PriceInputsSection(state: TradeUiState.Ready, onEvent: (TradeUiEvent) -> Unit) {
    val showTrigger = state.orderType == OrderType.STOP_MARKET || state.orderType == OrderType.STOP_LIMIT
    val showLimit = state.orderType == OrderType.LIMIT || state.orderType == OrderType.STOP_LIMIT

    if (showTrigger) {
        OutlinedTextField(
            value = state.triggerPriceInput,
            onValueChange = { onEvent(TradeUiEvent.TriggerPriceChanged(it)) },
            label = { Text(stringResource(R.string.trade_trigger_price_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("order_trigger_price_input")
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onEvent(TradeUiEvent.ApplyTriggerPreset(PRESET_MINUS_2_BPS)) },
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
            ) {
                Text(stringResource(R.string.trade_limit_preset_minus_2))
            }
            OutlinedButton(
                onClick = { onEvent(TradeUiEvent.ApplyTriggerPreset(0L)) },
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
            ) {
                Text(stringResource(R.string.trade_limit_preset_mid))
            }
            OutlinedButton(
                onClick = { onEvent(TradeUiEvent.ApplyTriggerPreset(PRESET_PLUS_2_BPS)) },
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
            ) {
                Text(stringResource(R.string.trade_limit_preset_plus_2))
            }
        }
    }

    if (showLimit) {
        OutlinedTextField(
            value = state.limitPriceInput,
            onValueChange = { onEvent(TradeUiEvent.LimitPriceChanged(it)) },
            label = { Text(stringResource(R.string.trade_limit_price_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("order_limit_price_input")
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onEvent(TradeUiEvent.ApplyLimitPreset(PRESET_MINUS_2_BPS)) },
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
            ) {
                Text(stringResource(R.string.trade_limit_preset_minus_2))
            }
            OutlinedButton(
                onClick = { onEvent(TradeUiEvent.ApplyLimitPreset(0L)) },
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
            ) {
                Text(stringResource(R.string.trade_limit_preset_mid))
            }
            OutlinedButton(
                onClick = { onEvent(TradeUiEvent.ApplyLimitPreset(PRESET_PLUS_2_BPS)) },
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
            ) {
                Text(stringResource(R.string.trade_limit_preset_plus_2))
            }
        }
    }
}

@Composable
internal fun OcoBracketSection(state: TradeUiState.Ready, onEvent: (TradeUiEvent) -> Unit) {
    val supportsOco = state.orderType == OrderType.MARKET || state.orderType == OrderType.LIMIT
    if (!supportsOco) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.trade_oco_toggle),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                TradingTermTooltipButton(term = TradingTerm.OCO_BRACKET)
            }
            Switch(
                checked = state.attachOcoBracket,
                onCheckedChange = { onEvent(TradeUiEvent.ToggleOcoBracket(it)) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("order_oco_switch")
            )
        }
        if (state.attachOcoBracket) {
            Text(
                text = stringResource(R.string.trade_oco_helper),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = state.takeProfitInput,
                onValueChange = { onEvent(TradeUiEvent.TakeProfitChanged(it)) },
                label = { Text(stringResource(R.string.trade_oco_take_profit_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("order_tp_input")
            )
            OutlinedTextField(
                value = state.stopLossInput,
                onValueChange = { onEvent(TradeUiEvent.StopLossChanged(it)) },
                label = { Text(stringResource(R.string.trade_oco_stop_loss_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("order_sl_input")
            )
        }
    }
}
