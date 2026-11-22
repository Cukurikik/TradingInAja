package com.tradinginaja.feature.trade

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.core.common.Money
import com.tradinginaja.core.common.MoneyRoundingMode
import com.tradinginaja.domain.engine.MultiAssetMarketState
import com.tradinginaja.domain.engine.OrderFormulas
import com.tradinginaja.domain.engine.ProgressionEngine
import com.tradinginaja.domain.engine.RiskEngine
import com.tradinginaja.domain.model.AssetSpec
import com.tradinginaja.domain.model.CareerLevel
import com.tradinginaja.domain.model.MarginHealthSnapshot
import com.tradinginaja.domain.model.MarketQuote
import com.tradinginaja.domain.model.Order
import com.tradinginaja.domain.model.OrderPreview
import com.tradinginaja.domain.model.OrderRejectReason
import com.tradinginaja.domain.model.OrderRequest
import com.tradinginaja.domain.model.OrderSide
import com.tradinginaja.domain.model.OrderStatus
import com.tradinginaja.domain.model.OrderSubmissionResult
import com.tradinginaja.domain.model.OrderType
import com.tradinginaja.domain.model.PortfolioState
import com.tradinginaja.domain.model.Position
import com.tradinginaja.domain.repository.MarketSimulationRepository
import com.tradinginaja.domain.repository.PortfolioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TradeAssetChip(val symbol: String, val name: String, val signedQuantity: Long)

sealed interface TradeFeedback {
    data class Executed(val side: OrderSide, val quantity: Long, val symbol: String, val price: Money) : TradeFeedback
    data class PlacedPending(val side: OrderSide, val quantity: Long, val symbol: String, val orderType: OrderType) :
        TradeFeedback
    data object DuplicateIgnored : TradeFeedback
    data class Rejected(val reason: OrderRejectReason) : TradeFeedback
}

private data class TradeDraftState(
    val selectedSymbol: String?,
    val side: OrderSide = OrderSide.BUY,
    val orderType: OrderType = OrderType.MARKET,
    val leverage: Int = 1,
    val quantityInput: String = "10",
    val limitPriceInput: String = "",
    val triggerPriceInput: String = "",
    val attachOcoBracket: Boolean = false,
    val takeProfitInput: String = "",
    val stopLossInput: String = "",
    val draftOrderId: String = UUID.randomUUID().toString(),
    val showConfirmationDialog: Boolean = false,
    val isSubmitting: Boolean = false,
    val feedback: TradeFeedback? = null
)

sealed interface TradeUiState {
    data object Loading : TradeUiState

    data class Ready(
        val assets: List<TradeAssetChip>,
        val selectedSpec: AssetSpec,
        val quote: MarketQuote,
        val tickIndex: Long,
        val careerLevel: CareerLevel,
        val marginHealth: MarginHealthSnapshot,
        val cashBalance: Money,
        val availableCash: Money,
        val reservedCash: Money,
        val selectedPosition: Position?,
        val closableQuantity: Long,
        val side: OrderSide,
        val orderType: OrderType,
        val leverage: Int,
        val quantityInput: String,
        val limitPriceInput: String,
        val triggerPriceInput: String,
        val attachOcoBracket: Boolean,
        val takeProfitInput: String,
        val stopLossInput: String,
        val draftOrderId: String,
        val preview: OrderPreview,
        val estimatedStopLossRiskPct: Double? = null,
        val pendingOrders: List<Order>,
        val showConfirmationDialog: Boolean,
        val isSubmitting: Boolean,
        val feedback: TradeFeedback?
    ) : TradeUiState
}

sealed interface TradeUiEvent {
    data class SelectAsset(val symbol: String) : TradeUiEvent
    data class SelectCareerLevel(val level: CareerLevel) : TradeUiEvent
    data class SelectSide(val side: OrderSide) : TradeUiEvent
    data class SelectOrderType(val type: OrderType) : TradeUiEvent
    data class SelectLeverage(val leverage: Int) : TradeUiEvent
    data class QuantityChanged(val value: String) : TradeUiEvent
    data class QuickQuantityPercent(val percent: Int) : TradeUiEvent
    data class LimitPriceChanged(val value: String) : TradeUiEvent
    data class ApplyLimitPreset(val offsetBps: Long) : TradeUiEvent
    data class TriggerPriceChanged(val value: String) : TradeUiEvent
    data class ApplyTriggerPreset(val offsetBps: Long) : TradeUiEvent
    data class ToggleOcoBracket(val enabled: Boolean) : TradeUiEvent
    data class TakeProfitChanged(val value: String) : TradeUiEvent
    data class StopLossChanged(val value: String) : TradeUiEvent
    data object RequestReview : TradeUiEvent
    data object DismissReview : TradeUiEvent
    data object ConfirmOrder : TradeUiEvent
    data class CancelPendingOrder(val orderId: String) : TradeUiEvent
    data object DismissFeedback : TradeUiEvent
}

@HiltViewModel
class TradeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val simulationRepository: MarketSimulationRepository,
    private val portfolioRepository: PortfolioRepository
) : ViewModel() {

    private val draft = MutableStateFlow(
        TradeDraftState(selectedSymbol = savedStateHandle.get<String>(ARG_SYMBOL))
    )

    val uiState: StateFlow<TradeUiState> = combine(
        simulationRepository.marketState,
        portfolioRepository.portfolioState,
        draft
    ) { marketState, portfolioState, currentDraft ->
        if (marketState == null || portfolioState == null) {
            TradeUiState.Loading
        } else {
            TradeDraftHelper.buildReadyState(marketState, portfolioState, currentDraft)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TradeUiState.Loading
    )

    init {
        viewModelScope.launch {
            simulationRepository.ensureInitialized()
            portfolioRepository.ensureInitialized()
        }
    }

    fun onEvent(event: TradeUiEvent) {
        if (!handleFormEvent(event)) {
            handleActionEvent(event)
        }
    }

    private fun handleFormEvent(event: TradeUiEvent): Boolean =
        handleSelectionEvent(event) || handlePriceAndQuantityEvent(event)

    private fun handleSelectionEvent(event: TradeUiEvent): Boolean = when (event) {
        is TradeUiEvent.SelectAsset -> {
            val quote = simulationRepository.marketState.value
                ?.findAsset(event.symbol)
                ?.let { OrderFormulas.quoteFromAssetState(it) }
            draft.value = TradeDraftHelper.onSelectAsset(draft.value, event.symbol, quote)
            true
        }
        is TradeUiEvent.SelectSide -> {
            draft.value = draft.value.copy(side = event.side, feedback = null)
            true
        }
        is TradeUiEvent.SelectOrderType -> {
            draft.value = TradeDraftHelper.onSelectOrderType(draft.value, event.type, currentReadySnapshot())
            true
        }
        is TradeUiEvent.SelectLeverage -> {
            draft.value = draft.value.copy(leverage = event.leverage, feedback = null)
            true
        }
        is TradeUiEvent.ToggleOcoBracket -> {
            draft.value = TradeDraftHelper.onToggleOco(draft.value, event.enabled, currentReadySnapshot())
            true
        }
        else -> false
    }

    private fun handlePriceAndQuantityEvent(event: TradeUiEvent): Boolean = when (event) {
        is TradeUiEvent.QuantityChanged -> {
            val filtered = event.value.filter { it.isDigit() }.take(MAX_QTY_DIGITS)
            draft.value = draft.value.copy(quantityInput = filtered, feedback = null)
            true
        }
        is TradeUiEvent.QuickQuantityPercent -> {
            currentReadySnapshot()?.let { ready ->
                draft.value = draft.value.copy(
                    quantityInput = TradeDraftHelper.computeQuickPercentQuantity(ready, event.percent).toString(),
                    feedback = null
                )
            }
            true
        }
        is TradeUiEvent.LimitPriceChanged -> {
            draft.value = draft.value.copy(
                limitPriceInput = TradeInputFormatter.sanitizeDecimalInput(event.value),
                feedback = null
            )
            true
        }
        is TradeUiEvent.TriggerPriceChanged -> {
            draft.value = draft.value.copy(
                triggerPriceInput = TradeInputFormatter.sanitizeDecimalInput(event.value),
                feedback = null
            )
            true
        }
        is TradeUiEvent.ApplyLimitPreset -> applyPresetPrice(event.offsetBps, isTrigger = false)
        is TradeUiEvent.ApplyTriggerPreset -> applyPresetPrice(event.offsetBps, isTrigger = true)
        is TradeUiEvent.TakeProfitChanged -> {
            draft.value = draft.value.copy(
                takeProfitInput = TradeInputFormatter.sanitizeDecimalInput(event.value),
                feedback = null
            )
            true
        }
        is TradeUiEvent.StopLossChanged -> {
            draft.value = draft.value.copy(
                stopLossInput = TradeInputFormatter.sanitizeDecimalInput(event.value),
                feedback = null
            )
            true
        }
        else -> false
    }

    private fun applyPresetPrice(offsetBps: Long, isTrigger: Boolean): Boolean {
        currentReadySnapshot()?.let { ready ->
            val formatted = TradeDraftHelper.computePresetPriceInput(ready.quote.midPrice, offsetBps)
            draft.value = if (isTrigger) {
                draft.value.copy(triggerPriceInput = formatted, feedback = null)
            } else {
                draft.value.copy(limitPriceInput = formatted, feedback = null)
            }
        }
        return true
    }

    private fun handleActionEvent(event: TradeUiEvent) {
        when (event) {
            is TradeUiEvent.SelectCareerLevel -> viewModelScope.launch {
                portfolioRepository.setCareerLevel(event.level)
            }
            TradeUiEvent.RequestReview -> {
                val ready = currentReadySnapshot() ?: return
                if (ready.preview.isValid) {
                    draft.value = draft.value.copy(showConfirmationDialog = true)
                }
            }
            TradeUiEvent.DismissReview -> draft.value = draft.value.copy(showConfirmationDialog = false)
            TradeUiEvent.ConfirmOrder -> submitDraftOrder()
            is TradeUiEvent.CancelPendingOrder -> viewModelScope.launch {
                portfolioRepository.cancelOrder(event.orderId)
            }
            TradeUiEvent.DismissFeedback -> draft.value = draft.value.copy(feedback = null)
            else -> Unit
        }
    }

    private fun currentReadySnapshot(): TradeUiState.Ready? {
        val marketState = simulationRepository.marketState.value
        val portfolio = portfolioRepository.portfolioState.value
        return if (marketState != null && portfolio != null) {
            TradeDraftHelper.buildReadyState(marketState, portfolio, draft.value)
        } else {
            null
        }
    }

    private fun submitDraftOrder() {
        val ready = currentReadySnapshot() ?: return
        if (ready.isSubmitting) return
        val request = TradeDraftHelper.buildOrderRequest(ready)

        draft.value = draft.value.copy(isSubmitting = true)
        viewModelScope.launch {
            val result = portfolioRepository.submitOrder(request)
            val feedback = TradeDraftHelper.mapFeedback(result)
            val nextOrderId = if (result is OrderSubmissionResult.Rejected) {
                ready.draftOrderId
            } else {
                UUID.randomUUID().toString()
            }
            draft.value = draft.value.copy(
                isSubmitting = false,
                showConfirmationDialog = false,
                draftOrderId = nextOrderId,
                feedback = feedback
            )
        }
    }

    companion object {
        const val ARG_SYMBOL = "symbol"
        private const val MAX_QTY_DIGITS = 8
    }
}

private object TradeDraftHelper {
    private const val HUNDRED = 100L
    private const val QUICK_BUFFER_BPS = 30L
    private const val DEFAULT_OCO_OFFSET_BPS = 400L

    fun onSelectAsset(current: TradeDraftState, symbol: String, quote: MarketQuote?): TradeDraftState {
        val defaultMid = quote?.midPrice?.let { TradeInputFormatter.formatLimitInput(it) }.orEmpty()
        return current.copy(
            selectedSymbol = symbol,
            limitPriceInput = if (current.orderType == OrderType.LIMIT || current.orderType == OrderType.STOP_LIMIT) {
                defaultMid
            } else {
                ""
            },
            triggerPriceInput = if (current.orderType == OrderType.STOP_MARKET ||
                current.orderType == OrderType.STOP_LIMIT
            ) {
                defaultMid
            } else {
                ""
            },
            attachOcoBracket = false,
            takeProfitInput = "",
            stopLossInput = "",
            draftOrderId = UUID.randomUUID().toString(),
            showConfirmationDialog = false,
            feedback = null
        )
    }

    fun onSelectOrderType(current: TradeDraftState, type: OrderType, ready: TradeUiState.Ready?): TradeDraftState {
        val midText = ready?.quote?.midPrice?.let { TradeInputFormatter.formatLimitInput(it) }.orEmpty()
        val needsLimit = type == OrderType.LIMIT || type == OrderType.STOP_LIMIT
        val needsTrigger = type == OrderType.STOP_MARKET || type == OrderType.STOP_LIMIT
        val nextLimit = if (needsLimit && current.limitPriceInput.isBlank()) midText else current.limitPriceInput
        val nextTrigger = if (needsTrigger &&
            current.triggerPriceInput.isBlank()
        ) {
            midText
        } else {
            current.triggerPriceInput
        }
        val supportsOco = type == OrderType.MARKET || type == OrderType.LIMIT
        return current.copy(
            orderType = type,
            limitPriceInput = nextLimit,
            triggerPriceInput = nextTrigger,
            attachOcoBracket = if (supportsOco) current.attachOcoBracket else false,
            feedback = null
        )
    }

    fun onToggleOco(current: TradeDraftState, enabled: Boolean, ready: TradeUiState.Ready?): TradeDraftState {
        if (!enabled || ready == null) {
            return current.copy(attachOcoBracket = enabled, feedback = null)
        }
        val base = ready.quote.midPrice
        val tpOffset = if (current.side == OrderSide.BUY) DEFAULT_OCO_OFFSET_BPS else -DEFAULT_OCO_OFFSET_BPS
        val slOffset = -tpOffset
        val nextTp = current.takeProfitInput.ifBlank { computePresetPriceInput(base, tpOffset) }
        val nextSl = current.stopLossInput.ifBlank { computePresetPriceInput(base, slOffset) }
        return current.copy(
            attachOcoBracket = true,
            takeProfitInput = nextTp,
            stopLossInput = nextSl,
            feedback = null
        )
    }

    fun computePresetPriceInput(base: Money, offsetBps: Long): String {
        val adjusted = base + base.multiplyByBps(offsetBps, MoneyRoundingMode.HALF_EVEN)
        val safePrice = if (adjusted.minorUnits >= GameBalance.MIN_ASSET_PRICE_MINOR) {
            adjusted
        } else {
            Money(GameBalance.MIN_ASSET_PRICE_MINOR)
        }
        return TradeInputFormatter.formatLimitInput(safePrice)
    }

    fun computeQuickPercentQuantity(ready: TradeUiState.Ready, percent: Int): Long {
        val pos = ready.selectedPosition
        val isClosing = (pos?.isLong == true && ready.side == OrderSide.SELL) ||
            (pos?.isShort == true && ready.side == OrderSide.BUY)
        return if (isClosing) {
            val raw = (ready.closableQuantity * percent) / HUNDRED
            if (percent > 0 && ready.closableQuantity > 0L && raw == 0L) 1L else raw
        } else {
            computeOpeningQuickQuantity(ready, percent)
        }
    }

    private fun computeOpeningQuickQuantity(ready: TradeUiState.Ready, percent: Int): Long {
        val refPrice = when (ready.orderType) {
            OrderType.LIMIT, OrderType.STOP_LIMIT ->
                TradeInputFormatter.parseMoneyOrNull(ready.limitPriceInput) ?: ready.quote.midPrice
            OrderType.STOP_MARKET ->
                TradeInputFormatter.parseMoneyOrNull(ready.triggerPriceInput) ?: ready.quote.midPrice
            OrderType.MARKET -> if (ready.side == OrderSide.BUY) ready.quote.askPrice else ready.quote.bidPrice
        }
        val safeLev = ready.leverage.coerceAtLeast(1).toLong()
        val marginPerUnit = (refPrice.minorUnits + safeLev - 1L) / safeLev
        val feeAndBufferPerUnit = OrderFormulas.calculateCommission(refPrice).minorUnits +
            refPrice.multiplyByBps(QUICK_BUFFER_BPS, MoneyRoundingMode.UP).minorUnits
        val unitOutflow = marginPerUnit + feeAndBufferPerUnit
        val budget = ready.availableCash.minorUnits * percent / HUNDRED
        return if (unitOutflow > 0L) (budget / unitOutflow).coerceAtLeast(0L) else 0L
    }

    fun buildReadyState(
        marketState: MultiAssetMarketState,
        portfolio: PortfolioState,
        currentDraft: TradeDraftState
    ): TradeUiState.Ready {
        val quotesBySymbol = marketState.assetStates.associate {
            it.spec.symbol to OrderFormulas.quoteFromAssetState(it)
        }
        val selectedAsset = currentDraft.selectedSymbol
            ?.let { sym -> marketState.findAsset(sym) }
            ?: marketState.assetStates.first()

        val symbol = selectedAsset.spec.symbol
        val quote = quotesBySymbol.getValue(symbol)
        val request = buildOrderRequest(
            symbol = symbol,
            draft = currentDraft
        )
        val preview = OrderFormulas.previewOrder(
            portfolio = portfolio,
            request = request,
            quote = quote,
            quotesBySymbol = quotesBySymbol
        )
        val marginHealth = RiskEngine.evaluateMarginHealth(portfolio, quotesBySymbol)
        val selectedPos = portfolio.positions[symbol]
        val closable = if (selectedPos != null) {
            val closeSide = if (selectedPos.isLong) OrderSide.SELL else OrderSide.BUY
            (selectedPos.absoluteQuantity - portfolio.reservedClosingQuantity(symbol, closeSide)).coerceAtLeast(0L)
        } else {
            0L
        }
        val pendingOrders = portfolio.orders
            .filter { it.status == OrderStatus.PENDING }
            .sortedByDescending { it.createdAtTick }

        val chips = marketState.assetStates.map { state ->
            TradeAssetChip(
                symbol = state.spec.symbol,
                name = state.spec.name,
                signedQuantity = portfolio.positions[state.spec.symbol]?.signedQuantity ?: 0L
            )
        }

        val slRiskPct = ProgressionEngine.estimateStopLossRiskPct(
            entryPrice = preview.estimatedExecutionPrice,
            stopLossPrice = request.stopLossPrice,
            quantity = request.quantity,
            accountEquity = marginHealth.accountEquity
        )

        return TradeUiState.Ready(
            assets = chips,
            selectedSpec = selectedAsset.spec,
            quote = quote,
            tickIndex = marketState.tickIndex,
            careerLevel = portfolio.careerLevel,
            marginHealth = marginHealth,
            cashBalance = portfolio.cashBalance,
            availableCash = portfolio.availableCash,
            reservedCash = portfolio.reservedCashForPendingBuys,
            selectedPosition = selectedPos,
            closableQuantity = closable,
            side = currentDraft.side,
            orderType = currentDraft.orderType,
            leverage = currentDraft.leverage,
            quantityInput = currentDraft.quantityInput,
            limitPriceInput = currentDraft.limitPriceInput,
            triggerPriceInput = currentDraft.triggerPriceInput,
            attachOcoBracket = currentDraft.attachOcoBracket,
            takeProfitInput = currentDraft.takeProfitInput,
            stopLossInput = currentDraft.stopLossInput,
            draftOrderId = currentDraft.draftOrderId,
            preview = preview,
            estimatedStopLossRiskPct = slRiskPct,
            pendingOrders = pendingOrders,
            showConfirmationDialog = currentDraft.showConfirmationDialog,
            isSubmitting = currentDraft.isSubmitting,
            feedback = currentDraft.feedback
        )
    }

    fun buildOrderRequest(ready: TradeUiState.Ready): OrderRequest {
        val supportsLimit = ready.orderType == OrderType.LIMIT || ready.orderType == OrderType.STOP_LIMIT
        val supportsTrigger = ready.orderType == OrderType.STOP_MARKET || ready.orderType == OrderType.STOP_LIMIT
        val supportsOco = ready.attachOcoBracket &&
            (ready.orderType == OrderType.MARKET || ready.orderType == OrderType.LIMIT)
        return OrderRequest(
            orderId = ready.draftOrderId,
            symbol = ready.selectedSpec.symbol,
            side = ready.side,
            type = ready.orderType,
            quantity = ready.quantityInput.toLongOrNull() ?: 0L,
            limitPrice = if (supportsLimit) TradeInputFormatter.parseMoneyOrNull(ready.limitPriceInput) else null,
            triggerPrice = if (supportsTrigger) TradeInputFormatter.parseMoneyOrNull(ready.triggerPriceInput) else null,
            leverage = ready.leverage,
            takeProfitPrice = if (supportsOco) TradeInputFormatter.parseMoneyOrNull(ready.takeProfitInput) else null,
            stopLossPrice = if (supportsOco) TradeInputFormatter.parseMoneyOrNull(ready.stopLossInput) else null
        )
    }

    private fun buildOrderRequest(symbol: String, draft: TradeDraftState): OrderRequest {
        val supportsLimit = draft.orderType == OrderType.LIMIT || draft.orderType == OrderType.STOP_LIMIT
        val supportsTrigger = draft.orderType == OrderType.STOP_MARKET || draft.orderType == OrderType.STOP_LIMIT
        val supportsOco = draft.attachOcoBracket &&
            (draft.orderType == OrderType.MARKET || draft.orderType == OrderType.LIMIT)
        return OrderRequest(
            orderId = draft.draftOrderId,
            symbol = symbol,
            side = draft.side,
            type = draft.orderType,
            quantity = draft.quantityInput.toLongOrNull() ?: 0L,
            limitPrice = if (supportsLimit) TradeInputFormatter.parseMoneyOrNull(draft.limitPriceInput) else null,
            triggerPrice = if (supportsTrigger) TradeInputFormatter.parseMoneyOrNull(draft.triggerPriceInput) else null,
            leverage = draft.leverage,
            takeProfitPrice = if (supportsOco) TradeInputFormatter.parseMoneyOrNull(draft.takeProfitInput) else null,
            stopLossPrice = if (supportsOco) TradeInputFormatter.parseMoneyOrNull(draft.stopLossInput) else null
        )
    }

    fun mapFeedback(result: OrderSubmissionResult): TradeFeedback = when (result) {
        is OrderSubmissionResult.Executed -> TradeFeedback.Executed(
            side = result.fill.side,
            quantity = result.fill.quantity,
            symbol = result.fill.symbol,
            price = result.fill.executionPrice
        )
        is OrderSubmissionResult.PlacedPending -> TradeFeedback.PlacedPending(
            side = result.order.side,
            quantity = result.order.quantity,
            symbol = result.order.symbol,
            orderType = result.order.type
        )
        is OrderSubmissionResult.DuplicateIgnored -> TradeFeedback.DuplicateIgnored
        is OrderSubmissionResult.Rejected -> TradeFeedback.Rejected(result.reason)
    }
}

private object TradeInputFormatter {
    private const val MAX_PRICE_CHARS = 14

    fun parseMoneyOrNull(raw: String): Money? {
        val normalized = raw.trim().replace(',', '.')
        if (normalized.isEmpty()) return null
        return runCatching {
            Money.fromDecimalString(normalized, MoneyRoundingMode.HALF_EVEN)
        }.getOrNull()
    }

    fun formatLimitInput(money: Money): String = BigDecimal.valueOf(money.minorUnits)
        .movePointLeft(GameBalance.CURRENCY_SCALE_DIGITS)
        .stripTrailingZeros()
        .toPlainString()

    fun sanitizeDecimalInput(raw: String): String {
        val normalized = raw.replace(',', '.')
        val builder = StringBuilder()
        var hasDot = false
        for (ch in normalized) {
            if (ch.isDigit()) {
                builder.append(ch)
            } else if (ch == '.' && !hasDot && builder.isNotEmpty()) {
                hasDot = true
                builder.append(ch)
            }
        }
        return builder.toString().take(MAX_PRICE_CHARS)
    }
}
