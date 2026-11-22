package com.tradinginaja.feature.market

import androidx.lifecycle.SavedStateHandle
import com.tradinginaja.domain.model.EventCatalog
import com.tradinginaja.domain.model.EventEffect
import com.tradinginaja.domain.model.EventSchedule
import com.tradinginaja.domain.model.EventTargetScope
import com.tradinginaja.domain.model.MarketEventDefinition
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val testEvents = EventCatalog(
        schemaVersion = 1,
        events = listOf(
            MarketEventDefinition(
                eventId = "EVT_NEWS_NSKP",
                eventType = "EARNINGS_PREVIEW",
                targetScope = EventTargetScope.SYMBOL,
                targetValue = "NSKP",
                schedule = EventSchedule(
                    minTick = 1L,
                    fixedTick = 2L,
                    durationTicks = 3,
                    cooldownTicks = 5
                ),
                effect = EventEffect(annualDriftDelta = 0.20, volatilityMultiplier = 1.5),
                headlineId = "Ekspansi Klaster NSKP",
                headlineEn = "NSKP Cluster Expansion",
                summaryId = "Laporan kuartalan menyoroti kenaikan belanja modal.",
                summaryEn = "Quarterly brief highlights higher capex.",
                bullCaseId = "Kapasitas baru mendukung pertumbuhan kontrak.",
                bullCaseEn = "New capacity supports contract growth.",
                bearCaseId = "Beban penyusutan menekan margin jangka pendek.",
                bearCaseEn = "Depreciation costs pressure near-term margins."
            ),
            MarketEventDefinition(
                eventId = "EVT_NEWS_CRYPTO",
                eventType = "PROTOCOL_UPDATE",
                targetScope = EventTargetScope.CATEGORY,
                targetValue = "CRYPTO",
                schedule = EventSchedule(
                    minTick = 1L,
                    fixedTick = 1L,
                    durationTicks = 1,
                    cooldownTicks = 5
                ),
                effect = EventEffect(annualDriftDelta = -0.18, volatilityMultiplier = 1.6),
                headlineId = "Pembaruan Protokol Kripto Fiktif",
                headlineEn = "Fictional Crypto Protocol Update",
                summaryId = "Node validator masih meninjau jadwal aktivasi.",
                summaryEn = "Validator nodes are reviewing activation timing.",
                bullCaseId = "Throughput transaksi meningkat.",
                bullCaseEn = "Transaction throughput improves.",
                bearCaseId = "Potensi jeda aktivasi memicu volatilitas.",
                bearCaseEn = "Potential activation delay adds volatility."
            )
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(testScope: TestScope, symbolFilter: String? = null): NewsViewModel {
        val savedState = if (symbolFilter != null) {
            SavedStateHandle(mapOf(NewsViewModel.ARG_SYMBOL_FILTER to symbolFilter))
        } else {
            SavedStateHandle()
        }
        return NewsViewModel(
            savedStateHandle = savedState,
            simulationRepository = FakeMarketSimulationRepository(
                scope = testScope.backgroundScope,
                eventCatalog = testEvents,
                enableRegimeSwitching = true,
                initialCandles = 0
            )
        )
    }

    @Test
    fun `stepping simulation triggers scheduled news and updates active vs archived filters`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(this)
            runCurrent()

            val initial = viewModel.uiState.value as NewsUiState.Ready
            assertEquals(0, initial.totalNewsCount)

            // Tick 1: EVT_NEWS_CRYPTO terpicu dengan durasi 1 (aktif pada tick 1)
            viewModel.onEvent(NewsUiEvent.StepOnce)
            runCurrent()
            val atTick1 = viewModel.uiState.value as NewsUiState.Ready
            assertEquals(1, atTick1.totalNewsCount)
            assertEquals(1, atTick1.activeNewsCount)
            assertEquals("PROTOCOL UPDATE", atTick1.articles.first().eventTypeLabel)

            // Tick 2: EVT_NEWS_CRYPTO kedaluwarsa, EVT_NEWS_NSKP terpicu
            viewModel.onEvent(NewsUiEvent.StepOnce)
            runCurrent()
            val atTick2 = viewModel.uiState.value as NewsUiState.Ready
            assertEquals(2, atTick2.totalNewsCount)
            assertEquals(1, atTick2.activeNewsCount)

            viewModel.onEvent(NewsUiEvent.SelectFilterMode(NewsFilterMode.ACTIVE_ONLY))
            runCurrent()
            val activeOnly = viewModel.uiState.value as NewsUiState.Ready
            assertEquals(1, activeOnly.articles.size)
            assertEquals("Ekspansi Klaster NSKP", activeOnly.articles.first().localizedHeadline(isIndonesian = true))
            assertTrue(activeOnly.articles.first().isActive)
        }

    @Test
    fun `symbol filter isolates matching asset articles without exposing numeric bias`() = runTest(testDispatcher) {
        val viewModel = createViewModel(this, symbolFilter = "NSKP")
        runCurrent()

        viewModel.onEvent(NewsUiEvent.StepOnce)
        viewModel.onEvent(NewsUiEvent.StepOnce)
        runCurrent()

        val state = viewModel.uiState.value as NewsUiState.Ready
        assertEquals(1, state.articles.size)
        val article = state.articles.first()
        assertEquals(listOf("NSKP"), article.affectedSymbols)
        assertFalse(article.localizedSummary(isIndonesian = false).contains("0.20"))
        assertFalse(article.localizedBullCase(isIndonesian = false).contains("+"))
    }
}
