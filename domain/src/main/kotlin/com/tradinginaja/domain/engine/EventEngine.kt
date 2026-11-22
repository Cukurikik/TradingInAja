package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.domain.model.ActiveMarketEvent
import com.tradinginaja.domain.model.AssetSpec
import com.tradinginaja.domain.model.EventCatalog
import com.tradinginaja.domain.model.EventTargetScope
import com.tradinginaja.domain.model.JumpDiffusionParams
import com.tradinginaja.domain.model.MarketEventDefinition
import com.tradinginaja.domain.model.MarketRegime
import com.tradinginaja.domain.model.NewsItem
import kotlin.math.max

data class EventStepOutcome(
    val rngState: Long,
    val activeEvents: List<ActiveMarketEvent>,
    val eventCooldowns: Map<String, Long>,
    val newsHistory: List<NewsItem>
)

data class EffectiveAssetParameters(
    val annualDrift: Double,
    val annualVolatility: Double,
    val jumpParams: JumpDiffusionParams?
)

/**
 * Mesin event pasar berbasis data JSON (data-driven) dan deterministik dengan seeded PRNG.
 * Pure Kotlin tanpa import Android.
 */
object EventEngine {

    private const val MIN_VOL_MULTIPLIER = 0.25
    private const val MAX_VOL_MULTIPLIER = 4.0

    fun step(
        tickIndex: Long,
        rngState: Long,
        currentRegime: MarketRegime,
        catalog: EventCatalog,
        assets: List<AssetSpec>,
        activeEvents: List<ActiveMarketEvent>,
        eventCooldowns: Map<String, Long>,
        existingNews: List<NewsItem>
    ): EventStepOutcome {
        val survivingEvents = activeEvents.filter { it.expiresAtTick > tickIndex }
        if (catalog.events.isEmpty()) {
            return EventStepOutcome(
                rngState = rngState,
                activeEvents = survivingEvents,
                eventCooldowns = eventCooldowns,
                newsHistory = existingNews
            )
        }

        val activeIds = survivingEvents.mapTo(HashSet(survivingEvents.size)) { it.definition.eventId }
        val updatedActive = survivingEvents.toMutableList()
        val updatedCooldowns = eventCooldowns.toMutableMap()
        val generatedNews = mutableListOf<NewsItem>()
        var currentRng = rngState

        for (eventDef in catalog.events) {
            val isAlreadyActive = activeIds.contains(eventDef.eventId)
            val cooldownUntil = updatedCooldowns[eventDef.eventId] ?: Long.MIN_VALUE
            val isCoolingDown = tickIndex < cooldownUntil

            if (!isAlreadyActive && !isCoolingDown && isScheduleEligible(eventDef, tickIndex, currentRegime)) {
                val sample = DeterministicRng.nextUniform(currentRng)
                currentRng = sample.nextState
                val shouldFire = isExactScheduleMatch(eventDef, tickIndex) ||
                    sample.value < eventDef.schedule.triggerProbabilityPerTick

                if (shouldFire) {
                    val duration = max(eventDef.schedule.durationTicks, 1)
                    val expiresAt = tickIndex + duration.toLong()
                    updatedActive.add(
                        ActiveMarketEvent(
                            definition = eventDef,
                            startedAtTick = tickIndex,
                            expiresAtTick = expiresAt
                        )
                    )
                    activeIds.add(eventDef.eventId)
                    updatedCooldowns[eventDef.eventId] = expiresAt + eventDef.schedule.cooldownTicks.coerceAtLeast(0)
                    generatedNews.add(buildNewsItem(eventDef, tickIndex, assets, duration))
                }
            }
        }

        val nextNews = if (generatedNews.isEmpty()) {
            existingNews
        } else {
            (generatedNews.reversed() + existingNews).take(GameBalance.MAX_NEWS_HISTORY)
        }

        return EventStepOutcome(
            rngState = currentRng,
            activeEvents = updatedActive,
            eventCooldowns = updatedCooldowns,
            newsHistory = nextNews
        )
    }

    fun combineAssetParameters(
        spec: AssetSpec,
        regime: MarketRegime,
        activeEvents: List<ActiveMarketEvent>,
        baseAnnualVolatility: Double,
        applyRegimeModifiers: Boolean = true
    ): EffectiveAssetParameters {
        val regimeMod = if (applyRegimeModifiers) {
            RegimeEngine.effectiveModifier(regime, spec)
        } else {
            RegimeAssetModifier(driftDelta = 0.0, volatilityMultiplier = 1.0, jumpIntensityMultiplier = 1.0)
        }

        var drift = spec.annualDrift + regimeMod.driftDelta
        var volMultiplier = regimeMod.volatilityMultiplier
        var jumpIntensityMult = regimeMod.jumpIntensityMultiplier
        var jumpMeanDelta = 0.0

        for (active in activeEvents) {
            if (matchesAsset(active.definition, spec)) {
                drift += active.definition.effect.annualDriftDelta
                volMultiplier *= active.definition.effect.volatilityMultiplier.coerceAtLeast(MIN_VOL_MULTIPLIER)
                jumpIntensityMult *= active.definition.effect.jumpIntensityMultiplier.coerceAtLeast(0.0)
                jumpMeanDelta += active.definition.effect.jumpMeanLogSizeDelta
            }
        }

        val effectiveVol = (baseAnnualVolatility * volMultiplier.coerceIn(MIN_VOL_MULTIPLIER, MAX_VOL_MULTIPLIER))
            .coerceIn(GameBalance.MIN_EFFECTIVE_VOLATILITY, GameBalance.MAX_EFFECTIVE_VOLATILITY)

        val effectiveJump = spec.jumpParams?.let { jp ->
            jp.copy(
                jumpIntensityPerYear = max(jp.jumpIntensityPerYear * jumpIntensityMult, 0.0),
                jumpMeanLogSize = jp.jumpMeanLogSize + jumpMeanDelta
            )
        }

        return EffectiveAssetParameters(
            annualDrift = drift,
            annualVolatility = effectiveVol,
            jumpParams = effectiveJump
        )
    }

    fun matchesAsset(definition: MarketEventDefinition, spec: AssetSpec): Boolean {
        val target = definition.targetValue.orEmpty()
        return when (definition.targetScope) {
            EventTargetScope.ALL -> true
            EventTargetScope.CATEGORY -> spec.category.name.equals(target, ignoreCase = true)
            EventTargetScope.SECTOR -> spec.sector.equals(target, ignoreCase = true)
            EventTargetScope.SYMBOL -> spec.symbol.equals(target, ignoreCase = true)
        }
    }

    private fun isScheduleEligible(
        definition: MarketEventDefinition,
        tickIndex: Long,
        currentRegime: MarketRegime
    ): Boolean {
        val schedule = definition.schedule
        val afterMin = tickIndex >= schedule.minTick
        val beforeMax = schedule.maxTick == null || tickIndex <= schedule.maxTick
        val regimeMatches = schedule.allowedRegimes.isEmpty() || schedule.allowedRegimes.contains(currentRegime)
        val hasValidSchedule = isExactScheduleMatch(definition, tickIndex) || schedule.triggerProbabilityPerTick > 0.0
        return afterMin && beforeMax && regimeMatches && hasValidSchedule
    }

    private fun isExactScheduleMatch(definition: MarketEventDefinition, tickIndex: Long): Boolean {
        val schedule = definition.schedule
        val fixedMatch = schedule.fixedTick != null && tickIndex == schedule.fixedTick
        val interval = schedule.intervalTicks
        val intervalMatch = interval != null &&
            interval > 0L &&
            tickIndex >= schedule.minTick &&
            ((tickIndex - schedule.intervalOffsetTicks) % interval == 0L)
        return fixedMatch || intervalMatch
    }

    private fun buildNewsItem(
        definition: MarketEventDefinition,
        tickIndex: Long,
        assets: List<AssetSpec>,
        durationTicks: Int
    ): NewsItem {
        val affected = assets.filter { matchesAsset(definition, it) }.map { it.symbol }
        return NewsItem(
            newsId = "${definition.eventId}_$tickIndex",
            eventId = definition.eventId,
            tickIndex = tickIndex,
            eventType = definition.eventType,
            targetScope = definition.targetScope,
            targetValue = definition.targetValue,
            affectedSymbols = affected,
            headlineId = definition.headlineId,
            headlineEn = definition.headlineEn,
            summaryId = definition.summaryId,
            summaryEn = definition.summaryEn,
            bullCaseId = definition.bullCaseId,
            bullCaseEn = definition.bullCaseEn,
            bearCaseId = definition.bearCaseId,
            bearCaseEn = definition.bearCaseEn,
            durationTicks = durationTicks
        )
    }
}
