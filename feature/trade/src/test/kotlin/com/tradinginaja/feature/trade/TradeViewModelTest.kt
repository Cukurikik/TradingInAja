package com.tradinginaja.feature.trade

import androidx.lifecycle.SavedStateHandle
import com.tradinginaja.core.common.Money
import com.tradinginaja.domain.model.OrderRejectReason
import com.tradinginaja.domain.model.OrderSide
import com.tradinginaja.domain.model.OrderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TradeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(testScope: TestScope, symbol: String = "NSKP"): TradeViewModel {
        val simRepo = FakeMarketSimulationRepository(testScope.backgroundScope)
        val portfolioRepo = FakePortfolioRepository(simRepo)
        return TradeViewModel(
            savedStateHandle = SavedStateHandle(mapOf(TradeViewModel.ARG_SYMBOL to symbol)),
            simulationRepository = simRepo,
            portfolioRepository = portfolioRepo
        )
    }

    @Test
    fun `preview exposes slippage commission and net impact before confirmation`() = runTest(testDispatcher) {
        val viewModel = createViewModel(this, symbol = "NSKP")
        runCurrent()

        viewModel.onEvent(TradeUiEvent.QuantityChanged("25"))
        runCurrent()

        val ready = viewModel.uiState.value as TradeUiState.Ready
        assertTrue(ready.preview.isValid)
        assertTrue(ready.preview.estimatedSlippageBps > 0L)
        assertTrue(ready.preview.estimatedCommission > Money.ZERO)
        assertTrue(ready.preview.estimatedNetCashImpact < Money.ZERO)
        assertFalse(ready.showConfirmationDialog)

        viewModel.onEvent(TradeUiEvent.RequestReview)
        runCurrent()

        val reviewing = viewModel.uiState.value as TradeUiState.Ready
        assertTrue(reviewing.showConfirmationDialog)
    }

    @Test
    fun `confirming market buy creates position deducts cash and rotates draft orderId`() = runTest(testDispatcher) {
        val viewModel = createViewModel(this, symbol = "NSKP")
        runCurrent()

        val initial = viewModel.uiState.value as TradeUiState.Ready
        val firstDraftId = initial.draftOrderId
        viewModel.onEvent(TradeUiEvent.QuantityChanged("20"))
        viewModel.onEvent(TradeUiEvent.RequestReview)
        viewModel.onEvent(TradeUiEvent.ConfirmOrder)
        runCurrent()

        val afterBuy = viewModel.uiState.value as TradeUiState.Ready
        assertFalse(afterBuy.showConfirmationDialog)
        assertEquals(20L, afterBuy.selectedPosition?.absoluteQuantity)
        assertTrue(afterBuy.cashBalance < initial.cashBalance)
        assertTrue(afterBuy.feedback is TradeFeedback.Executed)
        assertNotEquals(firstDraftId, afterBuy.draftOrderId)
    }

    @Test
    fun `limit buy below ask creates pending order reserves cash and cancel releases cash`() = runTest(testDispatcher) {
        val viewModel = createViewModel(this, symbol = "NSKP")
        runCurrent()

        viewModel.onEvent(TradeUiEvent.SelectOrderType(OrderType.LIMIT))
        viewModel.onEvent(TradeUiEvent.QuantityChanged("10"))
        viewModel.onEvent(TradeUiEvent.ApplyLimitPreset(-500L))
        runCurrent()

        viewModel.onEvent(TradeUiEvent.RequestReview)
        viewModel.onEvent(TradeUiEvent.ConfirmOrder)
        runCurrent()

        val withPending = viewModel.uiState.value as TradeUiState.Ready
        assertTrue(withPending.feedback is TradeFeedback.PlacedPending)
        assertEquals(1, withPending.pendingOrders.size)
        assertTrue(withPending.reservedCash > Money.ZERO)
        assertEquals(withPending.cashBalance - withPending.reservedCash, withPending.availableCash)

        val orderId = withPending.pendingOrders.first().orderId
        viewModel.onEvent(TradeUiEvent.CancelPendingOrder(orderId))
        runCurrent()

        val afterCancel = viewModel.uiState.value as TradeUiState.Ready
        assertTrue(afterCancel.pendingOrders.isEmpty())
        assertEquals(Money.ZERO, afterCancel.reservedCash)
        assertEquals(afterCancel.cashBalance, afterCancel.availableCash)
    }

    @Test
    fun `invalid quantity or unauthorized short on level 1 prevents confirmation dialog`() = runTest(testDispatcher) {
        val viewModel = createViewModel(this, symbol = "NSKP")
        runCurrent()

        viewModel.onEvent(TradeUiEvent.QuantityChanged("0"))
        viewModel.onEvent(TradeUiEvent.RequestReview)
        runCurrent()

        val invalidQty = viewModel.uiState.value as TradeUiState.Ready
        assertFalse(invalidQty.preview.isValid)
        assertEquals(OrderRejectReason.INVALID_QUANTITY, invalidQty.preview.rejectReason)
        assertFalse(invalidQty.showConfirmationDialog)

        viewModel.onEvent(TradeUiEvent.SelectSide(OrderSide.SELL))
        viewModel.onEvent(TradeUiEvent.QuantityChanged("5"))
        viewModel.onEvent(TradeUiEvent.RequestReview)
        runCurrent()

        val shortBlocked = viewModel.uiState.value as TradeUiState.Ready
        assertEquals(OrderRejectReason.SHORT_NOT_ALLOWED_FOR_LEVEL, shortBlocked.preview.rejectReason)
        assertFalse(shortBlocked.showConfirmationDialog)
        assertNull(shortBlocked.feedback)
    }

    @Test
    fun `level 3 unlocks 3x short selling and oco bracket spawns tp and sl orders`() = runTest(testDispatcher) {
        val viewModel = createViewModel(this, symbol = "NSKP")
        runCurrent()

        viewModel.onEvent(TradeUiEvent.SelectCareerLevel(com.tradinginaja.domain.model.CareerLevel.LEVEL_3_PRO))
        viewModel.onEvent(TradeUiEvent.SelectSide(OrderSide.SELL))
        viewModel.onEvent(TradeUiEvent.SelectLeverage(3))
        viewModel.onEvent(TradeUiEvent.QuantityChanged("10"))
        viewModel.onEvent(TradeUiEvent.ToggleOcoBracket(true))
        runCurrent()

        val ready = viewModel.uiState.value as TradeUiState.Ready
        assertTrue(ready.preview.isValid)
        assertTrue(ready.preview.isShortOpening)
        assertTrue(ready.preview.estimatedBorrowFeePerDay > Money.ZERO)
        assertTrue(ready.preview.estimatedLiquidationPrice != null)

        viewModel.onEvent(TradeUiEvent.RequestReview)
        viewModel.onEvent(TradeUiEvent.ConfirmOrder)
        runCurrent()

        val afterShort = viewModel.uiState.value as TradeUiState.Ready
        assertTrue(afterShort.selectedPosition?.isShort == true)
        assertEquals(3, afterShort.selectedPosition?.leverage)
        assertEquals(2, afterShort.pendingOrders.size)
        assertTrue(afterShort.pendingOrders.all { it.ocoGroupId != null })
    }
}
