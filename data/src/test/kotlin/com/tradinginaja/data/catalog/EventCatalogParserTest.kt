package com.tradinginaja.data.catalog

import com.tradinginaja.domain.engine.MarketEngine
import com.tradinginaja.domain.model.EventTargetScope
import com.tradinginaja.domain.model.MarketRegime
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EventCatalogParserTest {

    @Test
    fun `versioned market_events_v1 json parses cleanly and references valid assets`() {
        val marketJson = requireResourceText("market_catalog_v1.json")
        val eventsJson = requireResourceText("market_events_v1.json")

        val marketCatalog = AssetCatalogParser.parse(marketJson)
        val eventCatalog = EventCatalogParser.parse(eventsJson)

        assertEquals(1, eventCatalog.schemaVersion)
        assertTrue("Expected at least 10 rich fictional events", eventCatalog.events.size >= 10)

        val validSymbols = marketCatalog.assets.map { it.symbol }.toSet()
        val validSectors = marketCatalog.assets.map { it.sector }.toSet()
        val validCategories = marketCatalog.assets.map { it.category.name }.toSet()

        for (event in eventCatalog.events) {
            when (event.targetScope) {
                EventTargetScope.ALL -> Unit
                EventTargetScope.SYMBOL -> assertTrue(
                    "Unknown symbol target ${event.targetValue} on ${event.eventId}",
                    validSymbols.contains(event.targetValue)
                )
                EventTargetScope.SECTOR -> assertTrue(
                    "Unknown sector target ${event.targetValue} on ${event.eventId}",
                    validSectors.contains(event.targetValue)
                )
                EventTargetScope.CATEGORY -> assertTrue(
                    "Unknown category target ${event.targetValue} on ${event.eventId}",
                    validCategories.contains(event.targetValue)
                )
            }
            assertTrue(event.headlineId.isNotBlank() && event.headlineEn.isNotBlank())
            assertTrue(event.bullCaseId.isNotBlank() && event.bearCaseId.isNotBlank())
        }

        // Validasi row-stochastic matriks transisi rezim
        for (regime in MarketRegime.entries) {
            val rowSum = eventCatalog.transitionMatrix.rowFor(regime).sum()
            assertEquals(1.0, rowSum, 1e-6)
        }

        val initial = MarketEngine.createMultiAssetInitialState(
            catalog = marketCatalog,
            initialCandles = 120,
            eventCatalog = eventCatalog,
            enableRegimeSwitching = true
        )
        assertTrue("Initial warm-up ticks should populate news history", initial.newsHistory.isNotEmpty())
        assertTrue(initial.assetStates.all { it.currentPrice > 0.0 })
    }

    @Test
    fun `adding a brand new event type in json works without modifying kotlin code`() {
        val marketJson = requireResourceText("market_catalog_v1.json")
        val marketCatalog = AssetCatalogParser.parse(marketJson)

        val customJson = """
            {
              "schemaVersion": 2,
              "events": [
                {
                  "eventId": "EVT_BRAND_NEW_2026",
                  "eventType": "QUANTUM_CHIP_MILESTONE",
                  "targetScope": "SYMBOL",
                  "targetValue": "NSKP",
                  "schedule": {
                    "minTick": 1,
                    "fixedTick": 1,
                    "triggerProbabilityPerTick": 0.0,
                    "durationTicks": 4,
                    "cooldownTicks": 2
                  },
                  "effect": {
                    "annualDriftDelta": 0.12,
                    "volatilityMultiplier": 1.35,
                    "jumpIntensityMultiplier": 1.2,
                    "jumpMeanLogSizeDelta": 0.004
                  },
                  "headlineId": "Prototipe Prosesor Baru Diuji Terbatas",
                  "headlineEn": "New Processor Prototype Enters Limited Trials",
                  "summaryId": "Pengujian awal berjalan sesuai jadwal, namun skala produksi masih dibahas.",
                  "summaryEn": "Initial trials are on schedule, while mass-production scale remains under review.",
                  "bullCaseId": "Keunggulan efisiensi berpotensi membuka segmen pasar baru.",
                  "bullCaseEn": "Efficiency gains could unlock new enterprise demand.",
                  "bearCaseId": "Risiko transisi manufaktur dapat menunda kontribusi laba.",
                  "bearCaseEn": "Manufacturing ramp risks may delay earnings contribution."
                }
              ]
            }
        """.trimIndent()

        val customCatalog = EventCatalogParser.parse(customJson)
        assertEquals(2, customCatalog.schemaVersion)
        assertEquals(1, customCatalog.events.size)
        assertEquals("QUANTUM_CHIP_MILESTONE", customCatalog.events.first().eventType)

        var state = MarketEngine.createMultiAssetInitialState(
            catalog = marketCatalog,
            seed = 12_345L,
            initialCandles = 0,
            eventCatalog = customCatalog,
            enableRegimeSwitching = true
        )
        state = MarketEngine.stepAll(state)

        assertEquals(1, state.activeEvents.size)
        assertEquals(1, state.newsHistory.size)
        assertEquals("EVT_BRAND_NEW_2026", state.newsHistory.first().eventId)
        assertTrue(abs(state.assetStates.first().currentPrice - marketCatalog.assets.first().initialPrice) > 0.0)
    }

    @Test
    fun `invalid transition matrix row sum throws IllegalArgumentException`() {
        val badJson = """
            {
              "schemaVersion": 1,
              "transitionMatrix": [
                [0.5, 0.5, 0.5, 0.0],
                [0.25, 0.25, 0.25, 0.25],
                [0.25, 0.25, 0.25, 0.25],
                [0.25, 0.25, 0.25, 0.25]
              ],
              "events": []
            }
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            EventCatalogParser.parse(badJson)
        }
    }

    private fun requireResourceText(filename: String): String {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream(filename)) {
            "$filename harus tersedia di test resources."
        }
        return stream.bufferedReader().use { it.readText() }
    }
}
