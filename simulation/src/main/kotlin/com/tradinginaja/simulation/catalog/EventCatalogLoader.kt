package com.tradinginaja.simulation.catalog

import com.tradinginaja.domain.model.EventCatalog
import com.tradinginaja.domain.model.EventEffect
import com.tradinginaja.domain.model.EventSchedule
import com.tradinginaja.domain.model.EventTargetScope
import com.tradinginaja.domain.model.MarketEventDefinition
import com.tradinginaja.domain.model.MarketRegime
import com.tradinginaja.domain.model.RegimeTransitionMatrix
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pemuat katalog event JSON untuk skrip simulasi headless.
 */
object EventCatalogLoader {

    private val json = Json { ignoreUnknownKeys = true }

    fun load(file: File): EventCatalog {
        require(file.isFile) { "Berkas event tidak ditemukan: ${file.absolutePath}" }
        val root = json.parseToJsonElement(file.readText()).jsonObject
        val matrix = root.array("transitionMatrix").map { row ->
            row.jsonArray.map { it.jsonPrimitive.content.toDouble() }.toDoubleArray()
        }.toTypedArray()
        val events = root.array("events").map { it.jsonObject.toEventDefinition() }
        return EventCatalog(
            schemaVersion = root.int("schemaVersion"),
            events = events,
            transitionMatrix = RegimeTransitionMatrix(matrix)
        )
    }

    private fun JsonObject.toEventDefinition(): MarketEventDefinition {
        val scheduleObject = obj("schedule")
        val effectObject = obj("effect")
        val allowedRegimes = scheduleObject.optionalArray("allowedRegimes")
            ?.map { MarketRegime.valueOf(it.jsonPrimitive.content) }
            ?.toSet()
            ?: MarketRegime.entries.toSet()

        return MarketEventDefinition(
            eventId = str("eventId"),
            eventType = str("eventType"),
            targetScope = EventTargetScope.valueOf(str("targetScope")),
            targetValue = optionalString("targetValue"),
            schedule = EventSchedule(
                minTick = scheduleObject.longOr("minTick", 0L),
                maxTick = scheduleObject.optionalLong("maxTick"),
                fixedTick = scheduleObject.optionalLong("fixedTick"),
                intervalTicks = scheduleObject.optionalLong("intervalTicks"),
                intervalOffsetTicks = scheduleObject.longOr("intervalOffsetTicks", 0L),
                triggerProbabilityPerTick = scheduleObject.doubleOr("triggerProbabilityPerTick", 0.0),
                durationTicks = scheduleObject.intOr("durationTicks", 1),
                cooldownTicks = scheduleObject.intOr("cooldownTicks", 0),
                allowedRegimes = allowedRegimes
            ),
            effect = EventEffect(
                annualDriftDelta = effectObject.doubleOr("annualDriftDelta", 0.0),
                volatilityMultiplier = effectObject.doubleOr("volatilityMultiplier", 1.0),
                jumpIntensityMultiplier = effectObject.doubleOr("jumpIntensityMultiplier", 1.0),
                jumpMeanLogSizeDelta = effectObject.doubleOr("jumpMeanLogSizeDelta", 0.0)
            ),
            headlineId = str("headlineId"),
            headlineEn = str("headlineEn"),
            summaryId = str("summaryId"),
            summaryEn = str("summaryEn"),
            bullCaseId = str("bullCaseId"),
            bullCaseEn = str("bullCaseEn"),
            bearCaseId = str("bearCaseId"),
            bearCaseEn = str("bearCaseEn")
        )
    }
}
