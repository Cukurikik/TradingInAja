package com.tradinginaja.feature.portfolio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tradinginaja.core.common.Money
import com.tradinginaja.core.designsystem.theme.TradingNumberTextStyle
import com.tradinginaja.core.designsystem.theme.TradingTheme
import com.tradinginaja.domain.model.Order
import com.tradinginaja.domain.model.OrderSide
import com.tradinginaja.domain.model.OrderType
import com.tradinginaja.domain.model.PostMortemCause
import com.tradinginaja.domain.model.PostMortemSeverity
import com.tradinginaja.domain.model.PostMortemSummary
import com.tradinginaja.domain.model.TradeFill
import com.tradinginaja.domain.model.TradePostMortem
import java.util.Locale

@Composable
internal fun PositionRowCard(item: PortfolioPositionItem, onTradeClick: () -> Unit) {
    val isPositive = item.unrealizedPnl.minorUnits >= 0L
    val pnlColor = if (isPositive) TradingTheme.colors.bullish else TradingTheme.colors.bearish
    val signPrefix = if (isPositive) "+" else ""
    val pctLabel = String.format(
        Locale.US,
        stringResource(R.string.portfolio_percent_format),
        signPrefix,
        item.unrealizedPnlPct
    )
    val sideBadge = stringResource(
        if (item.isShort) R.string.portfolio_pos_side_short else R.string.portfolio_pos_side_long,
        item.leverage
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("portfolio_position_${item.symbol}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${item.symbol} · $sideBadge · ${stringResource(
                            R.string.portfolio_pos_units,
                            item.absoluteQuantity
                        )}",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (item.isShort) TradingTheme.colors.bearish else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onTradeClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("portfolio_trade_cta_${item.symbol}")
                ) {
                    Text(stringResource(R.string.portfolio_pos_trade_cta))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.portfolio_pos_avg_entry, item.averageEntryPrice.formatDisplay()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.portfolio_pos_mark_price, item.markPrice.formatDisplay()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = String.format(
                    Locale.US,
                    stringResource(R.string.portfolio_pos_margin_locked),
                    item.marginLocked.formatDisplay(),
                    item.exposurePctOfEquity
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            item.estLiquidationPrice?.let { liqPrice ->
                Text(
                    text = stringResource(R.string.portfolio_pos_liq_price, liqPrice.formatDisplay()),
                    style = MaterialTheme.typography.labelSmall,
                    color = TradingTheme.colors.bearish
                )
            }

            if (item.accumulatedBorrowFee.isPositive) {
                Text(
                    text = stringResource(R.string.portfolio_pos_borrow_fee, item.accumulatedBorrowFee.formatDisplay()),
                    style = MaterialTheme.typography.labelSmall,
                    color = TradingTheme.colors.warning
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.marketValue.formatDisplay(),
                    style = TradingNumberTextStyle.copy(fontWeight = FontWeight.SemiBold)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isPositive) Icons.Rounded.ArrowDropUp else Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                        tint = pnlColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${formatSignedMoney(item.unrealizedPnl)} ($pctLabel)",
                        style = MaterialTheme.typography.labelSmall,
                        color = pnlColor
                    )
                }
            }
        }
    }
}

@Composable
internal fun OpenOrderRowCard(order: Order, onCancel: () -> Unit) {
    val isBuy = order.side == OrderSide.BUY
    val sideColor = if (isBuy) TradingTheme.colors.bullish else TradingTheme.colors.bearish
    val priceLine = when (order.type) {
        OrderType.LIMIT -> "Limit: ${(order.limitPrice ?: Money.ZERO).formatDisplay()}"
        OrderType.STOP_MARKET -> "Stop: ${(order.triggerPrice ?: Money.ZERO).formatDisplay()}"
        OrderType.STOP_LIMIT ->
            "Stop ${(order.triggerPrice ?: Money.ZERO).formatDisplay()} -> " +
                "Limit ${(order.limitPrice ?: Money.ZERO).formatDisplay()}"
        OrderType.MARKET -> ""
    }
    val ocoSuffix = if (order.ocoGroupId != null) " · OCO" else ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("portfolio_open_order_${order.orderId}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${order.side.name} ${order.quantity} ${order.symbol} (${order.type.name}$ocoSuffix)",
                    style = MaterialTheme.typography.titleSmall,
                    color = sideColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = priceLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("portfolio_cancel_order_${order.orderId}")
            ) {
                Text(stringResource(R.string.portfolio_cancel_order))
            }
        }
    }
}

@Composable
internal fun PostMortemOverviewCard(summary: PostMortemSummary) {
    if (summary.totalLosingTrades == 0) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("portfolio_post_mortem_summary"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Psychology,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.portfolio_post_mortem_banner_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(
                        R.string.portfolio_post_mortem_banner_body,
                        summary.totalLosingTrades,
                        summary.unhedgedLossCount,
                        summary.overLeveragedLossCount,
                        summary.disciplinedStopLossCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun TradeFillRowCard(fill: TradeFill, postMortem: TradePostMortem? = null) {
    val isBuy = fill.side == OrderSide.BUY
    val sideColor = if (isBuy) TradingTheme.colors.bullish else TradingTheme.colors.bearish
    val pnlPositive = fill.realizedPnl.minorUnits >= 0L
    val pnlColor = if (pnlPositive) TradingTheme.colors.bullish else TradingTheme.colors.bearish
    val liqTag = if (fill.isLiquidation) " [${stringResource(R.string.portfolio_fill_liquidation_badge)}]" else ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("portfolio_fill_${fill.fillId}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${fill.side.name} ${fill.quantity} ${fill.symbol}$liqTag",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (fill.isLiquidation) TradingTheme.colors.bearish else sideColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatSignedMoney(fill.realizedPnl),
                    style = MaterialTheme.typography.labelMedium,
                    color = pnlColor
                )
            }
            Text(
                text = stringResource(
                    R.string.portfolio_fill_details,
                    fill.executionPrice.formatDisplay(),
                    fill.commission.formatDisplay(),
                    fill.slippageBps
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.portfolio_fill_tick, fill.tickIndex, fill.orderType.name),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            postMortem?.let { diagnosis ->
                PostMortemDiagnosisBox(postMortem = diagnosis)
            }
        }
    }
}

@Composable
private fun PostMortemDiagnosisBox(postMortem: TradePostMortem) {
    val boxColor = when (postMortem.severity) {
        PostMortemSeverity.CRITICAL -> TradingTheme.colors.bearishContainer.copy(alpha = 0.7f)
        PostMortemSeverity.WARNING -> TradingTheme.colors.warning.copy(alpha = 0.14f)
        PostMortemSeverity.CONSTRUCTIVE -> TradingTheme.colors.bullishContainer.copy(alpha = 0.45f)
    }
    val accentColor = when (postMortem.severity) {
        PostMortemSeverity.CRITICAL -> TradingTheme.colors.bearish
        PostMortemSeverity.WARNING -> TradingTheme.colors.warning
        PostMortemSeverity.CONSTRUCTIVE -> TradingTheme.colors.bullish
    }
    val (causeTitle, causeCoach) = postMortemCauseStrings(postMortem.primaryCause)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("post_mortem_card_${postMortem.fillId}"),
        shape = MaterialTheme.shapes.small,
        color = boxColor
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = stringResource(R.string.portfolio_post_mortem_tag, causeTitle),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Text(
                text = causeCoach,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = String.format(
                    Locale.US,
                    stringResource(R.string.portfolio_post_mortem_meta),
                    postMortem.lossPctOfInitialCapital,
                    postMortem.leverageUsed,
                    stringResource(
                        if (postMortem.hadStopLossProtection) {
                            R.string.portfolio_post_mortem_sl_yes
                        } else {
                            R.string.portfolio_post_mortem_sl_no
                        }
                    )
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun postMortemCauseStrings(cause: PostMortemCause): Pair<String, String> = when (cause) {
    PostMortemCause.FORCED_LIQUIDATION ->
        stringResource(R.string.pm_cause_liq_title) to stringResource(R.string.pm_cause_liq_coach)
    PostMortemCause.OVER_LEVERAGE ->
        stringResource(R.string.pm_cause_leverage_title) to stringResource(R.string.pm_cause_leverage_coach)
    PostMortemCause.NO_STOP_LOSS ->
        stringResource(R.string.pm_cause_no_sl_title) to stringResource(R.string.pm_cause_no_sl_coach)
    PostMortemCause.OVERSIZED_POSITION_RISK ->
        stringResource(R.string.pm_cause_oversized_title) to stringResource(R.string.pm_cause_oversized_coach)
    PostMortemCause.HIGH_CONCENTRATION ->
        stringResource(R.string.pm_cause_concentration_title) to stringResource(R.string.pm_cause_concentration_coach)
    PostMortemCause.HIGH_SLIPPAGE ->
        stringResource(R.string.pm_cause_slippage_title) to stringResource(R.string.pm_cause_slippage_coach)
    PostMortemCause.CONTROLLED_STOP_EXIT ->
        stringResource(R.string.pm_cause_controlled_title) to stringResource(R.string.pm_cause_controlled_coach)
}

@Composable
internal fun MetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = TradingNumberTextStyle.copy(
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                fontWeight = FontWeight.SemiBold,
                color = valueColor
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun EmptySectionCard(text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("portfolio_empty_section"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(20.dp)
        )
    }
}

@Composable
internal fun ResetPortfolioDialog(initialCash: Money, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.portfolio_reset_dialog_title)) },
        text = { Text(stringResource(R.string.portfolio_reset_dialog_body, initialCash.formatDisplay())) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("confirm_reset_portfolio")
            ) {
                Text(stringResource(R.string.portfolio_reset_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(stringResource(R.string.portfolio_reset_cancel))
            }
        }
    )
}

internal fun formatSignedMoney(money: Money): String =
    if (money.minorUnits > 0L) "+${money.formatDisplay()}" else money.formatDisplay()
