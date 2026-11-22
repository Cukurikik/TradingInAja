package com.tradinginaja.data.catalog

import com.tradinginaja.domain.model.EventCatalog
import com.tradinginaja.domain.model.EventEffect
import com.tradinginaja.domain.model.EventSchedule
import com.tradinginaja.domain.model.EventTargetScope
import com.tradinginaja.domain.model.MarketEventDefinition
import com.tradinginaja.domain.model.MarketRegime
import com.tradinginaja.domain.model.RegimeTransitionMatrix
import kotlinx.serialization.json.Json

object EventCatalogParser {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun parse(rawJson: String): EventCatalog {
        val dto = jsonParser.decodeFromString<EventCatalogDto>(rawJson)
        require(dto.schemaVersion >= 1) { "Versi skema katalog event tidak valid: ${dto.schemaVersion}" }

        val matrix = dto.transitionMatrix?.let { rows ->
            RegimeTransitionMatrix(rows.map { it.toDoubleArray() }.toTypedArray())
        } ?: RegimeTransitionMatrix.default()

        val seenIds = HashSet<String>(dto.events.size)
        val events = dto.events.map { item ->
            require(item.eventId.isNotBlank()) { "eventId tidak boleh kosong." }
            require(seenIds.add(item.eventId)) { "eventId duplikat di katalog event: ${item.eventId}" }
            item.toDomain()
        }

        return EventCatalog(
            schemaVersion = dto.schemaVersion,
            events = events,
            transitionMatrix = matrix
        )
    }

    private fun MarketEventDefinitionDto.toDomain(): MarketEventDefinition {
        require(eventType.isNotBlank()) { "eventType pada $eventId tidak boleh kosong." }
        val scope = EventTargetScope.valueOf(targetScope)
        if (scope != EventTargetScope.ALL) {
            require(!targetValue.isNullOrBlank()) {
                "targetValue wajib diisi untuk event $eventId dengan scope $scope."
            }
        }

        require(schedule.triggerProbabilityPerTick in 0.0..1.0) {
            "Probabilitas event $eventId harus berada di [0.0, 1.0]."
        }
        require(schedule.durationTicks >= 1) {
            "Durasi event $eventId minimal 1 tick."
        }
        require(schedule.cooldownTicks >= 0) {
            "Cooldown event $eventId tidak boleh negatif."
        }
        require(effect.volatilityMultiplier > 0.0) {
            "volatilityMultiplier event $eventId harus > 0."
        }
        require(effect.jumpIntensityMultiplier >= 0.0) {
            "jumpIntensityMultiplier event $eventId tidak boleh negatif."
        }

        val allowedRegimes = if (schedule.allowedRegimes.isEmpty()) {
            MarketRegime.entries.toSet()
        } else {
            schedule.allowedRegimes.map { MarketRegime.valueOf(it) }.toSet()
        }

        return MarketEventDefinition(
            eventId = eventId,
            eventType = eventType,
            targetScope = scope,
            targetValue = targetValue,
            schedule = EventSchedule(
                minTick = schedule.minTick,
                maxTick = schedule.maxTick,
                fixedTick = schedule.fixedTick,
                intervalTicks = schedule.intervalTicks,
                intervalOffsetTicks = schedule.intervalOffsetTicks,
                triggerProbabilityPerTick = schedule.triggerProbabilityPerTick,
                durationTicks = schedule.durationTicks,
                cooldownTicks = schedule.cooldownTicks,
                allowedRegimes = allowedRegimes
            ),
            effect = EventEffect(
                annualDriftDelta = effect.annualDriftDelta,
                volatilityMultiplier = effect.volatilityMultiplier,
                jumpIntensityMultiplier = effect.jumpIntensityMultiplier,
                jumpMeanLogSizeDelta = effect.jumpMeanLogSizeDelta
            ),
            headlineId = headlineId,
            headlineEn = headlineEn,
            summaryId = summaryId,
            summaryEn = summaryEn,
            bullCaseId = bullCaseId,
            bullCaseEn = bullCaseEn,
            bearCaseId = bearCaseId,
            bearCaseEn = bearCaseEn
        )
    }
}
