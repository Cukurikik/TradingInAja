package com.tradinginaja.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradinginaja.core.designsystem.R
import com.tradinginaja.core.designsystem.theme.TradingTheme
import com.tradinginaja.domain.model.CareerLevel
import com.tradinginaja.domain.model.TradingTerm

@Composable
fun TradingTermTooltipButton(term: TradingTerm, modifier: Modifier = Modifier) {
    var showDialog by rememberSaveable(term) { mutableStateOf(false) }
    val title = stringResource(termTitleRes(term))

    IconButton(
        onClick = { showDialog = true },
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("tooltip_${term.name.lowercase()}")
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = stringResource(R.string.term_tooltip_cd, title),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }

    if (showDialog) {
        TradingTermDialog(
            term = term,
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun TradingTermChip(term: TradingTerm, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("glossary_chip_${term.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(termTitleRes(term)),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun TradingTermDialog(term: TradingTerm, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(termTitleRes(term)),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(termDescriptionRes(term)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ) {
                    Text(
                        text = stringResource(termTipRes(term)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("tooltip_close_button")
            ) {
                Text(text = stringResource(R.string.term_tooltip_close))
            }
        }
    )
}

@StringRes
fun careerLevelTitleRes(level: CareerLevel): Int = when (level) {
    CareerLevel.LEVEL_1_NOVICE -> R.string.career_level_1_name
    CareerLevel.LEVEL_2_ACTIVE -> R.string.career_level_2_name
    CareerLevel.LEVEL_3_PRO -> R.string.career_level_3_name
    CareerLevel.LEVEL_4_FUND_MANAGER -> R.string.career_level_4_name
    CareerLevel.LEVEL_5_HEDGE_FUND -> R.string.career_level_5_name
}

@StringRes
fun termTitleRes(term: TradingTerm): Int = when (term) {
    TradingTerm.SPREAD -> R.string.term_spread_title
    TradingTerm.SLIPPAGE -> R.string.term_slippage_title
    TradingTerm.STOP_LOSS -> R.string.term_stop_loss_title
    TradingTerm.OCO_BRACKET -> R.string.term_oco_title
    TradingTerm.LEVERAGE -> R.string.term_leverage_title
    TradingTerm.MARGIN_CALL -> R.string.term_margin_call_title
    TradingTerm.DRAWDOWN -> R.string.term_drawdown_title
    TradingTerm.RISK_REWARD -> R.string.term_risk_reward_title
    TradingTerm.CANDLESTICK_OHLC -> R.string.term_candle_title
    TradingTerm.POSITION_SIZING -> R.string.term_sizing_title
}

@StringRes
fun termDescriptionRes(term: TradingTerm): Int = when (term) {
    TradingTerm.SPREAD -> R.string.term_spread_desc
    TradingTerm.SLIPPAGE -> R.string.term_slippage_desc
    TradingTerm.STOP_LOSS -> R.string.term_stop_loss_desc
    TradingTerm.OCO_BRACKET -> R.string.term_oco_desc
    TradingTerm.LEVERAGE -> R.string.term_leverage_desc
    TradingTerm.MARGIN_CALL -> R.string.term_margin_call_desc
    TradingTerm.DRAWDOWN -> R.string.term_drawdown_desc
    TradingTerm.RISK_REWARD -> R.string.term_risk_reward_desc
    TradingTerm.CANDLESTICK_OHLC -> R.string.term_candle_desc
    TradingTerm.POSITION_SIZING -> R.string.term_sizing_desc
}

@StringRes
fun termTipRes(term: TradingTerm): Int = when (term) {
    TradingTerm.SPREAD -> R.string.term_spread_tip
    TradingTerm.SLIPPAGE -> R.string.term_slippage_tip
    TradingTerm.STOP_LOSS -> R.string.term_stop_loss_tip
    TradingTerm.OCO_BRACKET -> R.string.term_oco_tip
    TradingTerm.LEVERAGE -> R.string.term_leverage_tip
    TradingTerm.MARGIN_CALL -> R.string.term_margin_call_tip
    TradingTerm.DRAWDOWN -> R.string.term_drawdown_tip
    TradingTerm.RISK_REWARD -> R.string.term_risk_reward_tip
    TradingTerm.CANDLESTICK_OHLC -> R.string.term_candle_tip
    TradingTerm.POSITION_SIZING -> R.string.term_sizing_tip
}
