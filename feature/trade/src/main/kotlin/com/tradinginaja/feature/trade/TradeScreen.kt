package com.tradinginaja.feature.trade

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tradinginaja.core.designsystem.component.SimulationDisclaimerBanner
import com.tradinginaja.core.designsystem.component.TradingTermTooltipButton
import com.tradinginaja.core.designsystem.theme.TradingNumberTextStyle
import com.tradinginaja.core.designsystem.theme.TradingTheme
import com.tradinginaja.domain.model.OrderSide
import com.tradinginaja.domain.model.OrderType
import com.tradinginaja.domain.model.TradingTerm

private val MAX_TRADE_CONTENT_WIDTH = 720.dp
private const val PERCENT_QUARTER = 25
private const val PERCENT_HALF = 50
private const val PERCENT_FULL = 100

@Composable
fun TradeRoute(
    initialSymbol: String? = null,
    modifier: Modifier = Modifier,
    viewModel: TradeViewModel = hiltViewModel()
) {
    LaunchedEffect(initialSymbol) {
        if (initialSymbol != null) {
            viewModel.onEvent(TradeUiEvent.SelectAsset(initialSymbol))
        }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TradeScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        modifier = modifier
    )
}

@Composable
fun TradeScreen(uiState: TradeUiState, onEvent: (TradeUiEvent) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("trade_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        when (uiState) {
            TradeUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.trade_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is TradeUiState.Ready -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = MAX_TRADE_CONTENT_WIDTH)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AccountBalanceBanner(uiState)
                    MarginCallAlertBanner(
                        marginLevelPct = uiState.marginHealth.marginLevelPct,
                        status = uiState.marginHealth.status
                    )
                    CareerRiskTierCard(
                        careerLevel = uiState.careerLevel,
                        onSelectLevel = { onEvent(TradeUiEvent.SelectCareerLevel(it)) }
                    )
                    AssetSelectorStrip(
                        assets = uiState.assets,
                        selectedSymbol = uiState.selectedSpec.symbol,
                        onSelectAsset = { onEvent(TradeUiEvent.SelectAsset(it)) }
                    )
                    QuoteSummaryCard(uiState)
                    OrderFormCard(uiState, onEvent)
                    OrderRiskPreviewCard(
                        preview = uiState.preview,
                        estimatedStopLossRiskPct = uiState.estimatedStopLossRiskPct
                    )

                    uiState.feedback?.let { feedback ->
                        FeedbackBanner(
                            feedback = feedback,
                            onDismiss = { onEvent(TradeUiEvent.DismissFeedback) }
                        )
                    }

                    Button(
                        onClick = { onEvent(TradeUiEvent.RequestReview) },
                        enabled = uiState.preview.isValid && !uiState.isSubmitting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.side == OrderSide.BUY) {
                                TradingTheme.colors.bullish
                            } else {
                                TradingTheme.colors.bearish
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("review_order_button")
                    ) {
                        Text(text = stringResource(R.string.trade_review_order_cta))
                    }

                    Text(
                        text = stringResource(R.string.trade_pending_orders_title, uiState.pendingOrders.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (uiState.pendingOrders.isEmpty()) {
                        Text(
                            text = stringResource(R.string.trade_pending_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        uiState.pendingOrders.forEach { pendingOrder ->
                            PendingOrderCard(
                                order = pendingOrder,
                                onCancel = { onEvent(TradeUiEvent.CancelPendingOrder(it)) }
                            )
                        }
                    }

                    SimulationDisclaimerBanner()
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (uiState.showConfirmationDialog) {
                    OrderConfirmationDialog(
                        state = uiState,
                        onConfirm = { onEvent(TradeUiEvent.ConfirmOrder) },
                        onDismiss = { onEvent(TradeUiEvent.DismissReview) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountBalanceBanner(state: TradeUiState.Ready) {
    val pos = state.selectedPosition
    val posText = when {
        pos == null -> stringResource(R.string.trade_flat_position)
        pos.isShort -> stringResource(R.string.trade_short_units, pos.absoluteQuantity, state.closableQuantity)
        else -> stringResource(R.string.trade_owned_units, pos.absoluteQuantity, state.closableQuantity)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.trade_available_cash),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = state.availableCash.formatDisplay(),
                    style = TradingNumberTextStyle.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.testTag("trade_available_cash")
                )
            }
            if (state.reservedCash.isPositive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.trade_reserved_cash),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = state.reservedCash.formatDisplay(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TradingTheme.colors.warning
                    )
                }
            }
            Text(
                text = posText,
                style = MaterialTheme.typography.bodySmall,
                color = if (pos?.isShort == true) TradingTheme.colors.bearish else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun AssetSelectorStrip(assets: List<TradeAssetChip>, selectedSymbol: String, onSelectAsset: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        assets.forEach { asset ->
            val label = when {
                asset.signedQuantity > 0L -> "${asset.symbol} (+${asset.signedQuantity})"
                asset.signedQuantity < 0L -> "${asset.symbol} (${asset.signedQuantity})"
                else -> asset.symbol
            }
            FilterChip(
                selected = asset.symbol == selectedSymbol,
                onClick = { onSelectAsset(asset.symbol) },
                label = { Text(label) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("trade_asset_chip_${asset.symbol}")
            )
        }
    }
}

@Composable
private fun QuoteSummaryCard(state: TradeUiState.Ready) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${state.selectedSpec.symbol} · ${state.selectedSpec.name}",
                    style = MaterialTheme.typography.titleSmall
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.trade_quote_spread, state.quote.spreadBps),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TradingTermTooltipButton(term = TradingTerm.SPREAD)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${stringResource(R.string.trade_quote_bid)}: ${state.quote.bidPrice.formatDisplay()}",
                    style = TradingNumberTextStyle.copy(
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        color = TradingTheme.colors.bullish
                    )
                )
                Text(
                    text = "${stringResource(R.string.trade_quote_ask)}: ${state.quote.askPrice.formatDisplay()}",
                    style = TradingNumberTextStyle.copy(
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        color = TradingTheme.colors.bearish
                    )
                )
            }
        }
    }
}

@Composable
private fun OrderFormCard(state: TradeUiState.Ready, onEvent: (TradeUiEvent) -> Unit) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.side == OrderSide.BUY,
                    onClick = { onEvent(TradeUiEvent.SelectSide(OrderSide.BUY)) },
                    label = { Text(stringResource(R.string.trade_side_buy)) },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("order_side_buy")
                )
                FilterChip(
                    selected = state.side == OrderSide.SELL,
                    onClick = { onEvent(TradeUiEvent.SelectSide(OrderSide.SELL)) },
                    label = { Text(stringResource(R.string.trade_side_sell)) },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("order_side_sell")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OrderTypeChip(
                    OrderType.MARKET,
                    state.orderType,
                    R.string.trade_type_market,
                    "order_type_market",
                    onEvent
                )
                OrderTypeChip(OrderType.LIMIT, state.orderType, R.string.trade_type_limit, "order_type_limit", onEvent)
                OrderTypeChip(
                    OrderType.STOP_MARKET,
                    state.orderType,
                    R.string.trade_type_stop,
                    "order_type_stop",
                    onEvent
                )
                OrderTypeChip(
                    OrderType.STOP_LIMIT,
                    state.orderType,
                    R.string.trade_type_stop_limit,
                    "order_type_stop_limit",
                    onEvent
                )
            }

            LeverageSelectorRow(
                selectedLeverage = state.leverage,
                careerLevel = state.careerLevel,
                onSelectLeverage = { onEvent(TradeUiEvent.SelectLeverage(it)) }
            )

            OutlinedTextField(
                value = state.quantityInput,
                onValueChange = { onEvent(TradeUiEvent.QuantityChanged(it)) },
                label = { Text(stringResource(R.string.trade_quantity_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("order_quantity_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onEvent(TradeUiEvent.QuickQuantityPercent(PERCENT_QUARTER)) },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.trade_preset_25_pct))
                }
                OutlinedButton(
                    onClick = { onEvent(TradeUiEvent.QuickQuantityPercent(PERCENT_HALF)) },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.trade_preset_50_pct))
                }
                OutlinedButton(
                    onClick = { onEvent(TradeUiEvent.QuickQuantityPercent(PERCENT_FULL)) },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.trade_preset_max))
                }
            }

            PriceInputsSection(state = state, onEvent = onEvent)
            OcoBracketSection(state = state, onEvent = onEvent)
        }
    }
}

@Composable
private fun OrderTypeChip(
    type: OrderType,
    selectedType: OrderType,
    labelRes: Int,
    tag: String,
    onEvent: (TradeUiEvent) -> Unit
) {
    FilterChip(
        selected = selectedType == type,
        onClick = { onEvent(TradeUiEvent.SelectOrderType(type)) },
        label = { Text(stringResource(labelRes)) },
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag(tag)
    )
}

@Composable
private fun FeedbackBanner(feedback: TradeFeedback, onDismiss: () -> Unit) {
    val message = when (feedback) {
        is TradeFeedback.Executed -> stringResource(
            R.string.trade_feedback_executed,
            orderSideLabel(feedback.side),
            feedback.quantity,
            feedback.symbol,
            feedback.price.formatDisplay()
        )
        is TradeFeedback.PlacedPending -> stringResource(
            R.string.trade_feedback_pending,
            orderSideLabel(feedback.side),
            feedback.quantity,
            feedback.symbol,
            orderTypeLabel(feedback.orderType)
        )
        TradeFeedback.DuplicateIgnored -> stringResource(R.string.trade_feedback_duplicate)
        is TradeFeedback.Rejected -> stringResource(
            R.string.trade_feedback_rejected,
            rejectReasonText(feedback.reason)
        )
    }

    val containerColor = if (feedback is TradeFeedback.Rejected) {
        TradingTheme.colors.bearishContainer
    } else {
        TradingTheme.colors.bullishContainer
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trade_feedback_banner"),
        shape = MaterialTheme.shapes.small,
        color = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(text = stringResource(R.string.trade_feedback_ack))
            }
        }
    }
}
