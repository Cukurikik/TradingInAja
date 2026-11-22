package com.tradinginaja.data.catalog

import kotlinx.serialization.Serializable

@Serializable
data class EventCatalogDto(
    val schemaVersion: Int,
    val transitionMatrix: List<List<Double>>? = null,
    val events: List<MarketEventDefinitionDto> = emptyList()
)

@Serializable
data class MarketEventDefinitionDto(
    val eventId: String,
    val eventType: String,
    val targetScope: String,
    val targetValue: String? = null,
    val schedule: EventScheduleDto,
    val effect: EventEffectDto,
    val headlineId: String,
    val headlineEn: String,
    val summaryId: String,
    val summaryEn: String,
    val bullCaseId: String,
    val bullCaseEn: String,
    val bearCaseId: String,
    val bearCaseEn: String
)

@Serializable
data class EventScheduleDto(
    val minTick: Long = 0L,
    val maxTick: Long? = null,
    val fixedTick: Long? = null,
    val intervalTicks: Long? = null,
    val intervalOffsetTicks: Long = 0L,
    val triggerProbabilityPerTick: Double = 0.0,
    val durationTicks: Int = 1,
    val cooldownTicks: Int = 0,
    val allowedRegimes: List<String> = emptyList()
)

@Serializable
data class EventEffectDto(
    val annualDriftDelta: Double = 0.0,
    val volatilityMultiplier: Double = 1.0,
    val jumpIntensityMultiplier: Double = 1.0,
    val jumpMeanLogSizeDelta: Double = 0.0
)
