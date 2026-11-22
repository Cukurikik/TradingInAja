package com.tradinginaja.feature.portfolio

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.core.common.Money
import com.tradinginaja.core.common.MoneyRoundingMode
import com.tradinginaja.domain.engine.OrderFormulas
import com.tradinginaja.domain.model.OrderRequest
import com.tradinginaja.domain.model.OrderSide
import com.tradinginaja.domain.model.OrderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PortfolioViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `portfolio reflects open positions mark to market updates and trade history`() = runTest(testDispatcher) {
        val simRepo = FakeMarketSimulationRepository()
        val portfolioRepo = FakePortfolioRepository(simRepo)
        val viewModel = PortfolioViewModel(simRepo, portfolioRepo)
        runCurrent()

        portfolioRepo.submitOrder(
            OrderRequest(
                orderId = "buy-nskp-1",
                symbol = "NSKP",
                side = OrderSide.BUY,
                type = OrderType.MARKET,
                quantity = 50L
            )
        )
        runCurrent()

        val afterBuy = viewModel.uiState.value as PortfolioUiState.Ready
        assertEquals(1, afterBuy.positions.size)
        assertEquals(50L, afterBuy.positions.first().quantity)
        assertEquals(1, afterBuy.tradeHistory.size)
        assertTrue(afterBuy.totalCommissions > Money.ZERO)

        val priceBeforeStep = afterBuy.positions.first().markPrice
        simRepo.stepOnce()
        runCurrent()

        val afterStep = viewModel.uiState.value as PortfolioUiState.Ready
        val priceAfterStep = afterStep.positions.first().markPrice
        if (priceAfterStep != priceBeforeStep) {
            assertNotEquals(afterBuy.totalEquity, afterStep.totalEquity)
        }
    }

    @Test
    fun `pending limit order can be cancelled and reset restores initial cash`() = runTest(testDispatcher) {
        val simRepo = FakeMarketSimulationRepository()
        val portfolioRepo = FakePortfolioRepository(simRepo)
        val viewModel = PortfolioViewModel(simRepo, portfolioRepo)
        runCurrent()

        val asset = simRepo.marketState.value!!.findAsset("NSKP")!!
        val quote = OrderFormulas.quoteFromAssetState(asset)
        val belowAsk = quote.midPrice - quote.midPrice.multiplyByBps(500L, MoneyRoundingMode.UP)

        portfolioRepo.submitOrder(
            OrderRequest(
                orderId = "limit-nskp-1",
                symbol = "NSKP",
                side = OrderSide.BUY,
                type = OrderType.LIMIT,
                quantity = 10L,
                limitPrice = belowAsk
            )
        )
        runCurrent()

        viewModel.onEvent(PortfolioUiEvent.SelectTab(PortfolioSectionTab.OPEN_ORDERS))
        runCurrent()

        val withOpenOrder = viewModel.uiState.value as PortfolioUiState.Ready
        assertEquals(PortfolioSectionTab.OPEN_ORDERS, withOpenOrder.selectedTab)
        assertEquals(1, withOpenOrder.pendingOrders.size)
        assertTrue(withOpenOrder.reservedCash > Money.ZERO)

        viewModel.onEvent(PortfolioUiEvent.CancelOrder("limit-nskp-1"))
        runCurrent()

        val afterCancel = viewModel.uiState.value as PortfolioUiState.Ready
        assertTrue(afterCancel.pendingOrders.isEmpty())
        assertEquals(Money.ZERO, afterCancel.reservedCash)

        viewModel.onEvent(PortfolioUiEvent.RequestReset)
        runCurrent()
        assertTrue((viewModel.uiState.value as PortfolioUiState.Ready).showResetDialog)

        viewModel.onEvent(PortfolioUiEvent.ConfirmReset)
        runCurrent()

        val afterReset = viewModel.uiState.value as PortfolioUiState.Ready
        assertFalse(afterReset.showResetDialog)
        assertEquals(Money(GameBalance.INITIAL_VIRTUAL_CASH_MINOR), afterReset.cashBalance)
        assertTrue(afterReset.positions.isEmpty())
        assertTrue(afterReset.tradeHistory.isEmpty())
    }

    @Test
    fun `portfolio exposes risk metrics margin health and leveraged short exposure`() = runTest(testDispatcher) {
        val simRepo = FakeMarketSimulationRepository()
        val portfolioRepo = FakePortfolioRepository(simRepo)
        val viewModel = PortfolioViewModel(simRepo, portfolioRepo)
        runCurrent()

        portfolioRepo.setCareerLevel(com.tradinginaja.domain.model.CareerLevel.LEVEL_3_PRO)
        portfolioRepo.submitOrder(
            OrderRequest(
                orderId = "short-nskp-3x",
                symbol = "NSKP",
                side = OrderSide.SELL,
                type = OrderType.MARKET,
                quantity = 20L,
                leverage = 3
            )
        )
        runCurrent()

        val state = viewModel.uiState.value as PortfolioUiState.Ready
        assertEquals(1, state.positions.size)
        val pos = state.positions.first()
        assertTrue(pos.isShort)
        assertEquals(3, pos.leverage)
        assertTrue(pos.estLiquidationPrice != null)
        assertTrue(state.riskMetrics.grossExposurePct > 0.0)
        assertTrue(state.riskMetrics.netExposurePct < 0.0)
        assertTrue(state.riskMetrics.marginHealth.marginLevelPct != null)
    }
}
