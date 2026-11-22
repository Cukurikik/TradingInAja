package com.tradinginaja.feature.trade

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.core.designsystem.component.TradingTermTooltipButton
import com.tradinginaja.core.designsystem.theme.TradingNumberTextStyle
import com.tradinginaja.core.designsystem.theme.TradingTheme
import com.tradinginaja.domain.model.Order
import com.tradinginaja.domain.model.OrderPreview
import com.tradinginaja.domain.model.OrderRejectReason
import com.tradinginaja.domain.model.OrderSide
import com.tradinginaja.domain.model.OrderType
import com.tradinginaja.domain.model.TradingTerm
import java.util.Locale

private const val SHORT_ORDER_ID_PREFIX_LEN = 8

@Composable
internal fun OrderRiskPreviewCard(preview: OrderPreview, estimatedStopLossRiskPct: Double? = null) {
    val rejectMessage = preview.rejectReason?.let { reason -> rejectReasonText(reason) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("order_preview_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
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
                    text = stringResource(R.string.trade_preview_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TradingTermTooltipButton(term = TradingTerm.SLIPPAGE)
            }
            PreviewRow(
                label = stringResource(R.string.trade_preview_exec_price),
                value = preview.estimatedExecutionPrice.formatDisplay()
            )
            PreviewRow(
                label = stringResource(R.string.trade_preview_slippage, preview.estimatedSlippageBps),
                value = preview.estimatedSlippageCost.formatDisplay()
            )
            PreviewRow(
                label = stringResource(R.string.trade_preview_notional, preview.leverage),
                value = preview.estimatedNotional.formatDisplay()
            )
            if (preview.requiredMargin.isPositive) {
                PreviewRow(
                    label = stringResource(R.string.trade_preview_required_margin),
                    value = preview.requiredMargin.formatDisplay()
                )
            }
            if (preview.estimatedBorrowFeePerDay.isPositive) {
                PreviewRow(
                    label = stringResource(R.string.trade_preview_borrow_daily),
                    value = preview.estimatedBorrowFeePerDay.formatDisplay(),
                    valueColor = TradingTheme.colors.warning
                )
            }
            preview.estimatedLiquidationPrice?.let { liqPrice ->
                PreviewRow(
                    label = stringResource(R.string.trade_preview_liq_price),
                    value = liqPrice.formatDisplay(),
                    valueColor = TradingTheme.colors.bearish
                )
            }
            PreviewRow(
                label = stringResource(R.string.trade_preview_commission),
                value = preview.estimatedCommission.formatDisplay()
            )
            HorizontalDivider(color = TradingTheme.colors.cardBorder)
            PreviewRow(
                label = stringResource(R.string.trade_preview_net_impact),
                value = preview.estimatedNetCashImpact.formatDisplay(),
                bold = true
            )
            PreviewRow(
                label = stringResource(R.string.trade_preview_projected_cash),
                value = preview.projectedCashAfterOrder.formatDisplay()
            )
            PreviewRow(
                label = stringResource(R.string.trade_preview_concentration),
                value = String.format(
                    Locale.US,
                    stringResource(R.string.trade_percent_format),
                    preview.projectedAssetConcentrationPct
                )
            )
            preview.estimatedRealizedPnl?.let { pnl ->
                PreviewRow(
                    label = stringResource(R.string.trade_preview_est_pnl),
                    value = if (pnl.minorUnits >= 0L) "+${pnl.formatDisplay()}" else pnl.formatDisplay(),
                    valueColor = if (pnl.minorUnits >= 0L) {
                        TradingTheme.colors.bullish
                    } else {
                        TradingTheme.colors.bearish
                    }
                )
            }

            estimatedStopLossRiskPct?.let { slRiskPct ->
                val isWithinRule = slRiskPct <= GameBalance.MAX_DISCIPLINED_RISK_PER_TRADE_PCT
                PreviewRow(
                    label = stringResource(R.string.trade_preview_sl_risk_pct),
                    value = String.format(Locale.US, stringResource(R.string.trade_percent_format), slRiskPct),
                    valueColor = if (isWithinRule) TradingTheme.colors.bullish else TradingTheme.colors.warning
                )
            }

            PreviewWarningsSection(
                preview = preview,
                estimatedStopLossRiskPct = estimatedStopLossRiskPct,
                rejectMessage = rejectMessage
            )
        }
    }
}

@Composable
private fun PreviewWarningsSection(preview: OrderPreview, estimatedStopLossRiskPct: Double?, rejectMessage: String?) {
    if (rejectMessage != null) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = TradingTheme.colors.bearishContainer.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = rejectMessage,
                style = MaterialTheme.typography.bodySmall,
                color = TradingTheme.colors.bearish,
                modifier = Modifier
                    .padding(10.dp)
                    .testTag("order_validation_error")
            )
        }
        return
    }
    if (estimatedStopLossRiskPct != null &&
        estimatedStopLossRiskPct > GameBalance.MAX_DISCIPLINED_RISK_PER_TRADE_PCT
    ) {
        WarningChip(text = stringResource(R.string.trade_warning_sl_risk_rule))
    }
    if (preview.isMarginLiquidationRiskWarning) {
        WarningChip(text = stringResource(R.string.trade_warning_liquidation))
    }
    if (preview.isHighSlippageWarning) {
        WarningChip(text = stringResource(R.string.trade_warning_slippage))
    }
    if (preview.isHighConcentrationWarning) {
        WarningChip(text = stringResource(R.string.trade_warning_concentration))
    }
}

@Composable
internal fun OrderConfirmationDialog(state: TradeUiState.Ready, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val sideLabel = stringResource(
        if (state.side == OrderSide.BUY) R.string.trade_side_buy else R.string.trade_side_sell
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.trade_confirm_dialog_title, sideLabel))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(
                        R.string.trade_confirm_summary,
                        state.selectedSpec.symbol,
                        state.quantityInput,
                        orderTypeLabel(state.orderType),
                        state.leverage
                    ),
                    style = MaterialTheme.typography.titleSmall
                )
                PreviewRow(
                    label = stringResource(R.string.trade_preview_exec_price),
                    value = state.preview.estimatedExecutionPrice.formatDisplay()
                )
                if (state.preview.requiredMargin.isPositive) {
                    PreviewRow(
                        label = stringResource(R.string.trade_preview_required_margin),
                        value = state.preview.requiredMargin.formatDisplay()
                    )
                }
                state.preview.estimatedLiquidationPrice?.let { liqPrice ->
                    PreviewRow(
                        label = stringResource(R.string.trade_preview_liq_price),
                        value = liqPrice.formatDisplay(),
                        valueColor = TradingTheme.colors.bearish
                    )
                }
                PreviewRow(
                    label = stringResource(R.string.trade_preview_commission),
                    value = state.preview.estimatedCommission.formatDisplay()
                )
                PreviewRow(
                    label = stringResource(R.string.trade_preview_net_impact),
                    value = state.preview.estimatedNetCashImpact.formatDisplay(),
                    bold = true
                )
                if (state.preview.isMarginLiquidationRiskWarning) {
                    WarningChip(text = stringResource(R.string.trade_warning_liquidation))
                }
                Text(
                    text = stringResource(
                        R.string.trade_confirm_dialog_subtitle,
                        state.draftOrderId.take(SHORT_ORDER_ID_PREFIX_LEN)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !state.isSubmitting,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("confirm_order_execute_button")
            ) {
                Text(text = stringResource(R.string.trade_confirm_dialog_action))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("confirm_order_cancel_button")
            ) {
                Text(text = stringResource(R.string.trade_confirm_dialog_cancel))
            }
        }
    )
}

@Composable
internal fun PendingOrderCard(order: Order, onCancel: (String) -> Unit) {
    val isBuy = order.side == OrderSide.BUY
    val sideColor = if (isBuy) TradingTheme.colors.bullish else TradingTheme.colors.bearish
    val priceSummary = when (order.type) {
        OrderType.LIMIT -> stringResource(
            R.string.trade_pending_price_limit,
            (order.limitPrice ?: order.reservedCash).formatDisplay()
        )
        OrderType.STOP_MARKET -> stringResource(
            R.string.trade_pending_price_stop,
            (order.triggerPrice ?: order.reservedCash).formatDisplay()
        )
        OrderType.STOP_LIMIT -> stringResource(
            R.string.trade_pending_price_stop_limit,
            (order.triggerPrice ?: order.reservedCash).formatDisplay(),
            (order.limitPrice ?: order.reservedCash).formatDisplay()
        )
        OrderType.MARKET -> ""
    }
    val ocoSuffix = if (order.ocoGroupId != null) {
        " · ${stringResource(R.string.trade_pending_oco_badge)}"
    } else {
        ""
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pending_order_${order.orderId}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.trade_pending_title,
                        orderSideLabel(order.side),
                        order.quantity.toString(),
                        order.symbol,
                        orderTypeLabel(order.type) + ocoSuffix
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = sideColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = priceSummary,
                    style = TradingNumberTextStyle.copy(
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            OutlinedButton(
                onClick = { onCancel(order.orderId) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("cancel_pending_${order.orderId}")
            ) {
                Text(text = stringResource(R.string.trade_cancel_order))
            }
        }
    }
}

@Composable
private fun PreviewRow(
    label: String,
    value: String,
    bold: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = TradingNumberTextStyle.copy(
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
                color = valueColor
            )
        )
    }
}

@Composable
private fun WarningChip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = TradingTheme.colors.warning.copy(alpha = 0.14f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = TradingTheme.colors.warning,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
internal fun rejectReasonText(reason: OrderRejectReason): String = when (reason) {
    OrderRejectReason.INVALID_QUANTITY -> stringResource(R.string.trade_error_invalid_quantity)
    OrderRejectReason.INVALID_LIMIT_PRICE -> stringResource(R.string.trade_error_invalid_limit)
    OrderRejectReason.INVALID_TRIGGER_PRICE -> stringResource(R.string.trade_error_invalid_trigger)
    OrderRejectReason.INVALID_LEVERAGE -> stringResource(R.string.trade_error_invalid_leverage)
    OrderRejectReason.LEVERAGE_EXCEEDS_CAREER_LIMIT -> stringResource(R.string.trade_error_leverage_limit)
    OrderRejectReason.SHORT_NOT_ALLOWED_FOR_LEVEL -> stringResource(R.string.trade_error_short_locked)
    OrderRejectReason.INVALID_OCO_BRACKET -> stringResource(R.string.trade_error_invalid_oco)
    OrderRejectReason.INSUFFICIENT_CASH -> stringResource(R.string.trade_error_insufficient_cash)
    OrderRejectReason.INSUFFICIENT_POSITION -> stringResource(R.string.trade_error_insufficient_position)
    OrderRejectReason.UNKNOWN_ASSET -> stringResource(R.string.trade_error_unknown_asset)
}

/** Label lokal untuk [OrderSide]; nama enum mentah tidak boleh tampil di UI. */
@Composable
internal fun orderSideLabel(side: OrderSide): String = stringResource(
    if (side == OrderSide.BUY) R.string.trade_side_buy else R.string.trade_side_sell
)

/** Label lokal untuk [OrderType]; nama enum mentah tidak boleh tampil di UI. */
@Composable
internal fun orderTypeLabel(type: OrderType): String = when (type) {
    OrderType.MARKET -> stringResource(R.string.trade_type_market)
    OrderType.LIMIT -> stringResource(R.string.trade_type_limit)
    OrderType.STOP_MARKET -> stringResource(R.string.trade_type_stop)
    OrderType.STOP_LIMIT -> stringResource(R.string.trade_type_stop_limit)
}
