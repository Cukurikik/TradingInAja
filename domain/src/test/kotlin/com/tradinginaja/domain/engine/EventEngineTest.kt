package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.domain.model.AssetCategory
import com.tradinginaja.domain.model.AssetSpec
import com.tradinginaja.domain.model.EventCatalog
import com.tradinginaja.domain.model.EventEffect
import com.tradinginaja.domain.model.EventSchedule
import com.tradinginaja.domain.model.EventTargetScope
import com.tradinginaja.domain.model.GarchParams
import com.tradinginaja.domain.model.JumpDiffusionParams
import com.tradinginaja.domain.model.LiquidityTier
import com.tradinginaja.domain.model.MarketCatalog
import com.tradinginaja.domain.model.MarketEventDefinition
import com.tradinginaja.domain.model.MarketRegime
import com.tradinginaja.domain.model.RegimeTransitionMatrix
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventEngineTest {

    private val techAsset = AssetSpec(
        symbol = "NSKP",
        name = "Nusantara Komputasi Tbk",
        sector = "Teknologi",
        category = AssetCategory.STOCK,
        initialPrice = 4_500.0,
        annualVolatility = 0.38,
        annualDrift = 0.11,
        liquidityTier = LiquidityTier.HIGH,
        baseVolumePerTick = 14_500L,
        jumpParams = JumpDiffusionParams(
            jumpIntensityPerYear = 18.0,
            jumpMeanLogSize = 0.005,
            jumpStdDevLogSize = 0.032
        ),
        garchParams = GarchParams(omegaWeight = 0.08, alpha = 0.12, beta = 0.80)
    )

    private val cryptoAsset = AssetSpec(
        symbol = "GRDC",
        name = "GarudaChain",
        sector = "Layer-1 Fiktif",
        category = AssetCategory.CRYPTO,
        initialPrice = 125_000.0,
        annualVolatility = 0.78,
        annualDrift = 0.14,
        liquidityTier = LiquidityTier.HIGH,
        baseVolumePerTick = 18_500L,
        garchParams = GarchParams(omegaWeight = 0.06, alpha = 0.16, beta = 0.78)
    )

    private val safeHavenAsset = AssetSpec(
        symbol = "XVMG",
        name = "Emas Murni Virtual",
        sector = "Logam Mulia",
        category = AssetCategory.COMMODITY,
        initialPrice = 1_350_000.0,
        annualVolatility = 0.15,
        annualDrift = 0.05,
        liquidityTier = LiquidityTier.VERY_HIGH,
        baseVolumePerTick = 24_000L,
        isSafeHaven = true
    )

    private val sampleEvents = EventCatalog(
        schemaVersion = 1,
        transitionMatrix = RegimeTransitionMatrix.default(),
        events = listOf(
            MarketEventDefinition(
                eventId = "EVT_TEST_TECH",
                eventType = "EARNINGS",
                targetScope = EventTargetScope.SECTOR,
                targetValue = "Teknologi",
                schedule = EventSchedule(
                    minTick = 1L,
                    intervalTicks = 20L,
                    intervalOffsetTicks = 5L,
                    triggerProbabilityPerTick = 0.04,
                    durationTicks = 5,
                    cooldownTicks = 4
                ),
                effect = EventEffect(
                    annualDriftDelta = 0.15,
                    volatilityMultiplier = 1.4,
                    jumpIntensityMultiplier = 1.3,
                    jumpMeanLogSizeDelta = 0.005
                ),
                headlineId = "Ekspansi Pusat Data NSKP",
                headlineEn = "NSKP Data Center Expansion",
                summaryId = "Belanja modal naik bersamaan dengan pertumbuhan kontrak.",
                summaryEn = "Capex rises alongside contract growth.",
                bullCaseId = "Pendapatan berulang jangka panjang berpotensi meningkat.",
                bullCaseEn = "Long-term recurring revenue may accelerate.",
                bearCaseId = "Margin jangka pendek tertekan biaya investasi.",
                bearCaseEn = "Near-term margins face capex pressure."
            ),
            MarketEventDefinition(
                eventId = "EVT_TEST_CRYPTO",
                eventType = "REGULATORY",
                targetScope = EventTargetScope.CATEGORY,
                targetValue = "CRYPTO",
                schedule = EventSchedule(
                    minTick = 1L,
                    triggerProbabilityPerTick = 0.05,
                    durationTicks = 4,
                    cooldownTicks = 6
                ),
                effect = EventEffect(
                    annualDriftDelta = -0.20,
                    volatilityMultiplier = 1.6,
                    jumpIntensityMultiplier = 1.5,
                    jumpMeanLogSizeDelta = -0.01
                ),
                headlineId = "Wacana Aturan Likuiditas Aset Kripto Fiktif",
                headlineEn = "Draft Liquidity Rule for Fictional Crypto Assets",
                summaryId = "Pelaku pasar menanti kejelasan implementasi bertahap.",
                summaryEn = "Market participants await phased implementation details.",
                bullCaseId = "Kejelasan aturan menarik partisipasi institusional.",
                bullCaseEn = "Regulatory clarity supports institutional participation.",
                bearCaseId = "Kepatuhan tambahan menahan aktivitas perdagangan jangka pendek.",
                bearCaseEn = "Compliance overhead may curb short-term activity."
            )
        )
    )

    private val testCatalog = MarketCatalog(
        schemaVersion = 1,
        assets = listOf(techAsset, cryptoAsset, safeHavenAsset),
        correlationMatrix = CholeskyDecomposition.identityMatrix(3)
    )

    @Test
    fun `identical seed produces identical regime path garch volatility events news and prices`() {
        val seed = 77_123_456L
        var stateA = MarketEngine.createMultiAssetInitialState(
            catalog = testCatalog,
            seed = seed,
            initialCandles = 0,
            eventCatalog = sampleEvents,
            enableRegimeSwitching = true
        )
        var stateB = MarketEngine.createMultiAssetInitialState(
            catalog = testCatalog,
            seed = seed,
            initialCandles = 0,
            eventCatalog = sampleEvents,
            enableRegimeSwitching = true
        )

        val regimesA = mutableListOf<MarketRegime>()
        val regimesB = mutableListOf<MarketRegime>()

        repeat(200) {
            stateA = MarketEngine.stepAll(stateA)
            stateB = MarketEngine.stepAll(stateB)
            regimesA.add(stateA.currentRegime)
            regimesB.add(stateB.currentRegime)
        }

        assertEquals(regimesA, regimesB)
        assertEquals(stateA.activeEvents, stateB.activeEvents)
        assertEquals(stateA.newsHistory, stateB.newsHistory)
        assertEquals(stateA.eventCooldowns, stateB.eventCooldowns)
        assertEquals(stateA.assetStates.map { it.currentPrice }, stateB.assetStates.map { it.currentPrice })
        assertEquals(
            stateA.assetStates.map { it.currentAnnualVolatility },
            stateB.assetStates.map { it.currentAnnualVolatility }
        )
        assertTrue("Expected news items to be generated over 200 ticks", stateA.newsHistory.isNotEmpty())
    }

    @Test
    fun `different seeds produce distinct event and regime paths`() {
        val stateA = MarketEngine.createMultiAssetInitialState(
            catalog = testCatalog,
            seed = 10_001L,
            initialCandles = 120,
            eventCatalog = sampleEvents,
            enableRegimeSwitching = true
        )
        val stateB = MarketEngine.createMultiAssetInitialState(
            catalog = testCatalog,
            seed = 90_009L,
            initialCandles = 120,
            eventCatalog = sampleEvents,
            enableRegimeSwitching = true
        )

        assertNotEquals(stateA.assetStates.first().currentPrice, stateB.assetStates.first().currentPrice)
        assertNotEquals(stateA.newsHistory, stateB.newsHistory)
    }

    @Test
    fun `long run markov regime frequencies converge to theoretical stationary distribution`() {
        val matrix = RegimeTransitionMatrix.default()
        val theoretical = RegimeEngine.stationaryDistribution(matrix)
        val counts = LongArray(MarketRegime.entries.size)

        var current = MarketRegime.SIDEWAYS
        var rng = DeterministicRng.initialState(seed = 20_261_122L, salt = 99L)
        val totalSteps = 35_000

        repeat(totalSteps) {
            val sample = DeterministicRng.nextUniform(rng)
            rng = sample.nextState
            current = RegimeEngine.nextRegime(current, sample.value, matrix)
            counts[current.ordinal]++
        }

        for (regime in MarketRegime.entries) {
            val empiricalFreq = counts[regime.ordinal].toDouble() / totalSteps.toDouble()
            val expectedFreq = theoretical[regime.ordinal]
            val error = abs(empiricalFreq - expectedFreq)
            assertTrue(
                "Regime $regime empirical frequency ($empiricalFreq) " +
                    "should be within 0.025 of stationary probability ($expectedFreq)",
                error < 0.025
            )
        }
    }

    @Test
    fun `garch volatility clustering spikes after large shock and decays gradually while staying bounded`() {
        val baseVol = 0.60
        val params = GarchParams(omegaWeight = 0.06, alpha = 0.18, beta = 0.76)
        val dt = 1.0 / GameBalance.TICKS_PER_TRADING_YEAR
        val normalTickStd = baseVol * sqrt(dt)

        val spikedVol = GarchVolatilityModel.nextAnnualVolatility(
            baseAnnualVolatility = baseVol,
            previousAnnualVolatility = baseVol,
            previousStepInnovation = normalTickStd * 5.5,
            params = params,
            dt = dt
        )
        assertTrue(
            "GARCH volatility should increase after 5.5-sigma shock (got $spikedVol)",
            spikedVol > baseVol * 1.15
        )

        val calmNextVol = GarchVolatilityModel.nextAnnualVolatility(
            baseAnnualVolatility = baseVol,
            previousAnnualVolatility = spikedVol,
            previousStepInnovation = 0.0,
            params = params,
            dt = dt
        )
        assertTrue(
            "GARCH volatility should decay smoothly after calm tick ($calmNextVol < $spikedVol)",
            calmNextVol < spikedVol
        )
        assertTrue(
            "GARCH volatility should still remain elevated above baseline due to persistence ($calmNextVol > $baseVol)",
            calmNextVol > baseVol
        )

        var extremeVol = baseVol
        repeat(5_000) { idx ->
            val shock = if (idx % 10 == 0) normalTickStd * 15.0 else 0.0
            extremeVol = GarchVolatilityModel.nextAnnualVolatility(
                baseAnnualVolatility = baseVol,
                previousAnnualVolatility = extremeVol,
                previousStepInnovation = shock,
                params = params,
                dt = dt
            )
            assertTrue("GARCH vol must stay finite & strictly positive", extremeVol.isFinite() && extremeVol > 0.0)
            assertTrue(
                "GARCH vol must respect upper cap",
                extremeVol <= baseVol * GameBalance.GARCH_MAX_VOL_RATIO + 1e-9
            )
        }
    }

    @Test
    fun `data driven event targets matching scope only and respects duration and cooldown`() {
        val customEvent = MarketEventDefinition(
            eventId = "CUSTOM_NEW_EVENT",
            eventType = "SUPPLY_DISRUPTION_CUSTOM",
            targetScope = EventTargetScope.SYMBOL,
            targetValue = "NSKP",
            schedule = EventSchedule(
                minTick = 1L,
                fixedTick = 3L,
                triggerProbabilityPerTick = 0.0,
                durationTicks = 3,
                cooldownTicks = 5
            ),
            effect = EventEffect(
                annualDriftDelta = -0.30,
                volatilityMultiplier = 1.80,
                jumpIntensityMultiplier = 2.0,
                jumpMeanLogSizeDelta = -0.02
            ),
            headlineId = "Gangguan Pasokan Komponen Khusus NSKP",
            headlineEn = "NSKP Custom Component Supply Bottleneck",
            summaryId = "Analis berbeda pandangan mengenai durasi dampak inventaris.",
            summaryEn = "Analysts diverge on how long inventory buffers last.",
            bullCaseId = "Stok penyangga mampu meredam keterlambatan sementara.",
            bullCaseEn = "Buffer inventory absorbs temporary shipment delays.",
            bearCaseId = "Penundaan pengiriman menekan target kuartalan.",
            bearCaseEn = "Shipment delays pressure quarterly delivery targets."
        )
        val catalog = EventCatalog(schemaVersion = 1, events = listOf(customEvent))
        var state = MarketEngine.createMultiAssetInitialState(
            catalog = testCatalog,
            seed = 4242L,
            initialCandles = 0,
            eventCatalog = catalog,
            enableRegimeSwitching = false
        )

        // Tick 1 & 2: belum terpicu
        state = MarketEngine.stepAllMany(state, 2)
        assertTrue(state.activeEvents.isEmpty())
        assertTrue(state.newsHistory.isEmpty())

        // Tick 3: fixedTick terpicu, berlangsung pada tick 3, 4, 5 (expiresAtTick = 6)
        state = MarketEngine.stepAll(state)
        assertEquals(1, state.activeEvents.size)
        assertEquals(1, state.newsHistory.size)
        assertEquals(listOf("NSKP"), state.newsHistory.first().affectedSymbols)

        val techEffective = EventEngine.combineAssetParameters(
            spec = techAsset,
            regime = MarketRegime.SIDEWAYS,
            activeEvents = state.activeEvents,
            baseAnnualVolatility = techAsset.annualVolatility,
            applyRegimeModifiers = false
        )
        val cryptoEffective = EventEngine.combineAssetParameters(
            spec = cryptoAsset,
            regime = MarketRegime.SIDEWAYS,
            activeEvents = state.activeEvents,
            baseAnnualVolatility = cryptoAsset.annualVolatility,
            applyRegimeModifiers = false
        )
        assertEquals(techAsset.annualDrift - 0.30, techEffective.annualDrift, 1e-9)
        assertEquals(techAsset.annualVolatility * 1.80, techEffective.annualVolatility, 1e-9)
        assertEquals(cryptoAsset.annualDrift, cryptoEffective.annualDrift, 1e-9)
        assertEquals(cryptoAsset.annualVolatility, cryptoEffective.annualVolatility, 1e-9)

        // Tick 4 & 5: masih aktif
        state = MarketEngine.stepAllMany(state, 2)
        assertEquals(1, state.activeEvents.size)

        // Tick 6: kedaluwarsa, tetap tercatat di newsHistory
        state = MarketEngine.stepAll(state)
        assertFalse(state.activeEvents.any { it.definition.eventId == "CUSTOM_NEW_EVENT" })
        assertEquals(1, state.newsHistory.size)
    }

    @Test
    fun `safe haven modifier provides defensive drift in crisis while risk assets turn negative`() {
        val crisisSafeHaven = RegimeEngine.effectiveModifier(MarketRegime.CRISIS, safeHavenAsset)
        val crisisTech = RegimeEngine.effectiveModifier(MarketRegime.CRISIS, techAsset)

        assertTrue(crisisSafeHaven.driftDelta > 0.0)
        assertTrue(crisisTech.driftDelta < 0.0)
        assertTrue(crisisTech.volatilityMultiplier > crisisSafeHaven.volatilityMultiplier)
    }
}
