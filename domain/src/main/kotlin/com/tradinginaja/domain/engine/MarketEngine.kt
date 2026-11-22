package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.domain.model.ActiveMarketEvent
import com.tradinginaja.domain.model.AssetSpec
import com.tradinginaja.domain.model.Candle
import com.tradinginaja.domain.model.CandleAggregator
import com.tradinginaja.domain.model.CandleTimeframe
import com.tradinginaja.domain.model.EventCatalog
import com.tradinginaja.domain.model.MarketCatalog
import com.tradinginaja.domain.model.MarketRegime
import com.tradinginaja.domain.model.MeanReversionParams
import com.tradinginaja.domain.model.NewsItem
import com.tradinginaja.domain.model.PriceModelType
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Snapshot state simulasi pasar yang immutable dan deterministik untuk satu aset.
 */
data class MarketAssetState(
    val spec: AssetSpec,
    val seed: Long,
    val rngState: Long,
    val tickIndex: Long,
    val currentPrice: Double,
    val previousClosePrice: Double,
    val baseCandles: List<Candle>,
    val currentAnnualVolatility: Double = spec.annualVolatility,
    val lastReturnInnovation: Double = 0.0
)

/**
 * Snapshot state simulasi multi-aset dengan korelasi Cholesky, rezim Markov,
 * volatilitas GARCH(1,1), dan mesin event data-driven.
 */
data class MultiAssetMarketState(
    val seed: Long,
    val rngState: Long,
    val tickIndex: Long,
    val assetStates: List<MarketAssetState>,
    val lowerCholesky: Array<DoubleArray>,
    val currentRegime: MarketRegime = MarketRegime.SIDEWAYS,
    val ticksInRegime: Long = 0L,
    val enableRegimeSwitching: Boolean = false,
    val eventCatalog: EventCatalog = EventCatalog.EMPTY,
    val activeEvents: List<ActiveMarketEvent> = emptyList(),
    val eventCooldowns: Map<String, Long> = emptyMap(),
    val newsHistory: List<NewsItem> = emptyList(),
    val eventRngState: Long = DeterministicRng.initialState(seed, GameBalance.EVENT_ENGINE_RNG_SALT)
) {
    fun findAsset(symbol: String): MarketAssetState? = assetStates.firstOrNull { it.spec.symbol == symbol }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MultiAssetMarketState) return false
        return hasSameCoreState(other) && hasSameMacroState(other)
    }

    private fun hasSameCoreState(other: MultiAssetMarketState): Boolean = seed == other.seed &&
        rngState == other.rngState &&
        tickIndex == other.tickIndex &&
        assetStates == other.assetStates &&
        lowerCholesky.contentDeepEquals(other.lowerCholesky)

    private fun hasSameMacroState(other: MultiAssetMarketState): Boolean = currentRegime == other.currentRegime &&
        ticksInRegime == other.ticksInRegime &&
        enableRegimeSwitching == other.enableRegimeSwitching &&
        eventCatalog == other.eventCatalog &&
        activeEvents == other.activeEvents &&
        eventCooldowns == other.eventCooldowns &&
        newsHistory == other.newsHistory &&
        eventRngState == other.eventRngState

    override fun hashCode(): Int {
        var result = seed.hashCode()
        result = 31 * result + rngState.hashCode()
        result = 31 * result + tickIndex.hashCode()
        result = 31 * result + assetStates.hashCode()
        result = 31 * result + lowerCholesky.contentDeepHashCode()
        result = 31 * result + currentRegime.hashCode()
        result = 31 * result + ticksInRegime.hashCode()
        result = 31 * result + enableRegimeSwitching.hashCode()
        result = 31 * result + eventCatalog.hashCode()
        result = 31 * result + activeEvents.hashCode()
        result = 31 * result + eventCooldowns.hashCode()
        result = 31 * result + newsHistory.hashCode()
        result = 31 * result + eventRngState.hashCode()
        return result
    }
}

/**
 * Mesin simulasi pasar pure Kotlin (tanpa import Android).
 * Seluruh transisi bersifat murni: `(state) -> newState`.
 */
object MarketEngine {

    private const val DEFAULT_OU_SPEED = 2.0

    fun createInitialState(
        spec: AssetSpec,
        seed: Long = GameBalance.DEFAULT_SIMULATION_SEED,
        initialCandles: Int = GameBalance.DEFAULT_INITIAL_HISTORY_CANDLES
    ): MarketAssetState {
        val initialRngState = DeterministicRng.initialState(
            seed = seed,
            salt = spec.symbol.hashCode().toLong()
        )
        val baseState = MarketAssetState(
            spec = spec,
            seed = seed,
            rngState = initialRngState,
            tickIndex = 0L,
            currentPrice = spec.initialPrice,
            previousClosePrice = spec.initialPrice,
            baseCandles = emptyList(),
            currentAnnualVolatility = spec.annualVolatility,
            lastReturnInnovation = 0.0
        )
        return if (initialCandles > 0) {
            stepMany(baseState, initialCandles)
        } else {
            baseState
        }
    }

    fun createMultiAssetInitialState(
        catalog: MarketCatalog,
        seed: Long = GameBalance.DEFAULT_SIMULATION_SEED,
        initialCandles: Int = GameBalance.DEFAULT_INITIAL_HISTORY_CANDLES,
        eventCatalog: EventCatalog = EventCatalog.EMPTY,
        enableRegimeSwitching: Boolean = false,
        initialRegime: MarketRegime = MarketRegime.SIDEWAYS
    ): MultiAssetMarketState {
        require(catalog.assets.isNotEmpty()) { "Katalog aset tidak boleh kosong." }
        val lowerCholesky = CholeskyDecomposition.decompose(catalog.correlationMatrix)
        val initialAssets = catalog.assets.map { spec ->
            MarketAssetState(
                spec = spec,
                seed = seed,
                rngState = DeterministicRng.initialState(seed, spec.symbol.hashCode().toLong()),
                tickIndex = 0L,
                currentPrice = spec.initialPrice,
                previousClosePrice = spec.initialPrice,
                baseCandles = emptyList(),
                currentAnnualVolatility = spec.annualVolatility,
                lastReturnInnovation = 0.0
            )
        }
        val baseState = MultiAssetMarketState(
            seed = seed,
            rngState = DeterministicRng.initialState(seed, salt = catalog.schemaVersion.toLong()),
            tickIndex = 0L,
            assetStates = initialAssets,
            lowerCholesky = lowerCholesky,
            currentRegime = initialRegime,
            ticksInRegime = 0L,
            enableRegimeSwitching = enableRegimeSwitching,
            eventCatalog = eventCatalog,
            eventRngState = DeterministicRng.initialState(
                seed = seed,
                salt = GameBalance.EVENT_ENGINE_RNG_SALT + eventCatalog.schemaVersion.toLong()
            )
        )
        return if (initialCandles > 0) {
            stepAllMany(baseState, initialCandles)
        } else {
            baseState
        }
    }

    /**
     * Memajukan simulasi satu aset tepat 1 tick (15 menit waktu pasar virtual).
     */
    fun step(state: MarketAssetState): MarketAssetState {
        val normalShock = DeterministicRng.nextStandardNormal(state.rngState)
        val garchVol = nextGarchBaseVol(state)
        val params = EffectiveAssetParameters(
            annualDrift = state.spec.annualDrift,
            annualVolatility = garchVol,
            jumpParams = state.spec.jumpParams
        )
        return stepWithShock(
            state = state.copy(rngState = normalShock.nextState),
            diffusionNormalShock = normalShock.value,
            garchBaseAnnualVol = garchVol,
            effectiveParams = params
        )
    }

    /**
     * Memajukan seluruh aset, transisi rezim Markov, event data-driven, dan GARCH tepat 1 tick.
     */
    fun stepAll(state: MultiAssetMarketState): MultiAssetMarketState {
        val nextTickIndex = state.tickIndex + 1L
        var nextEventRng = state.eventRngState

        val nextRegime = if (state.enableRegimeSwitching) {
            val sample = DeterministicRng.nextUniform(nextEventRng)
            nextEventRng = sample.nextState
            RegimeEngine.nextRegime(
                current = state.currentRegime,
                uniformSample = sample.value,
                matrix = state.eventCatalog.transitionMatrix
            )
        } else {
            state.currentRegime
        }
        val nextTicksInRegime = if (nextRegime == state.currentRegime) state.ticksInRegime + 1L else 1L

        val eventOutcome = EventEngine.step(
            tickIndex = nextTickIndex,
            rngState = nextEventRng,
            currentRegime = nextRegime,
            catalog = state.eventCatalog,
            assets = state.assetStates.map { it.spec },
            activeEvents = state.activeEvents,
            eventCooldowns = state.eventCooldowns,
            existingNews = state.newsHistory
        )
        nextEventRng = eventOutcome.rngState

        val assetCount = state.assetStates.size
        val independentNormals = DoubleArray(assetCount)
        var currentRng = state.rngState
        for (i in 0 until assetCount) {
            val sample = DeterministicRng.nextStandardNormal(currentRng)
            independentNormals[i] = sample.value
            currentRng = sample.nextState
        }

        val correlatedShocks = CholeskyDecomposition.correlate(state.lowerCholesky, independentNormals)
        val updatedAssets = state.assetStates.mapIndexed { index, assetState ->
            val garchVol = nextGarchBaseVol(assetState)
            // Modifier rezim aktif harus selalu berlaku: `enableRegimeSwitching` hanya mengatur
            // transisi Markov, bukan apakah rezim saat ini memengaruhi drift/volatilitas aset.
            val effectiveParams = EventEngine.combineAssetParameters(
                spec = assetState.spec,
                regime = nextRegime,
                activeEvents = eventOutcome.activeEvents,
                baseAnnualVolatility = garchVol,
                applyRegimeModifiers = true
            )
            stepWithShock(
                state = assetState,
                diffusionNormalShock = correlatedShocks[index],
                garchBaseAnnualVol = garchVol,
                effectiveParams = effectiveParams
            )
        }

        return state.copy(
            rngState = currentRng,
            tickIndex = nextTickIndex,
            assetStates = updatedAssets,
            currentRegime = nextRegime,
            ticksInRegime = nextTicksInRegime,
            activeEvents = eventOutcome.activeEvents,
            eventCooldowns = eventOutcome.eventCooldowns,
            newsHistory = eventOutcome.newsHistory,
            eventRngState = nextEventRng
        )
    }

    fun stepAllMany(state: MultiAssetMarketState, ticks: Int): MultiAssetMarketState {
        require(ticks >= 0) { "Jumlah tick simulasi tidak boleh negatif." }
        var current = state
        repeat(ticks) {
            current = stepAll(current)
        }
        return current
    }

    fun stepMany(state: MarketAssetState, ticks: Int): MarketAssetState {
        require(ticks >= 0) { "Jumlah tick simulasi tidak boleh negatif." }
        var current = state
        repeat(ticks) {
            current = step(current)
        }
        return current
    }

    fun candlesForTimeframe(state: MarketAssetState, timeframe: CandleTimeframe): List<Candle> =
        CandleAggregator.aggregate(state.baseCandles, timeframe)

    fun bidAskFor(state: MarketAssetState): Pair<Double, Double> =
        MarketFormulas.computeBidAsk(state.currentPrice, state.spec.liquidityTier)

    private fun nextGarchBaseVol(state: MarketAssetState): Double {
        val garch = state.spec.garchParams ?: return state.spec.annualVolatility
        return if (state.tickIndex == 0L) {
            state.spec.annualVolatility
        } else {
            GarchVolatilityModel.nextAnnualVolatility(
                baseAnnualVolatility = state.spec.annualVolatility,
                previousAnnualVolatility = state.currentAnnualVolatility,
                previousStepInnovation = state.lastReturnInnovation,
                params = garch
            )
        }
    }

    private fun stepWithShock(
        state: MarketAssetState,
        diffusionNormalShock: Double,
        garchBaseAnnualVol: Double,
        effectiveParams: EffectiveAssetParameters
    ): MarketAssetState {
        val openPrice = state.currentPrice
        val upperWickSample = DeterministicRng.nextUniform(state.rngState)
        val lowerWickSample = DeterministicRng.nextUniform(upperWickSample.nextState)
        val volumeNoiseSample = DeterministicRng.nextUniform(lowerWickSample.nextState)
        var nextRng = volumeNoiseSample.nextState

        val baseClose = when (state.spec.priceModel) {
            PriceModelType.GBM -> MarketFormulas.nextGbmClosePrice(
                previousPrice = openPrice,
                annualDrift = effectiveParams.annualDrift,
                annualVolatility = effectiveParams.annualVolatility,
                standardNormalShock = diffusionNormalShock
            )
            PriceModelType.MEAN_REVERSION_OU -> {
                val ouParams = state.spec.meanReversionParams ?: MeanReversionParams(
                    reversionSpeedPerYear = DEFAULT_OU_SPEED,
                    longTermMeanPrice = state.spec.initialPrice
                )
                MarketFormulas.nextOrnsteinUhlenbeckClosePrice(
                    previousPrice = openPrice,
                    annualVolatility = effectiveParams.annualVolatility,
                    params = ouParams,
                    standardNormalShock = diffusionNormalShock
                )
            }
        }

        val jumpParams = effectiveParams.jumpParams
        val closePrice = if (jumpParams != null) {
            val jumpTrigger = DeterministicRng.nextUniform(nextRng)
            val jumpSize = DeterministicRng.nextStandardNormal(jumpTrigger.nextState)
            nextRng = jumpSize.nextState
            MarketFormulas.applyJumpDiffusion(
                previousPrice = openPrice,
                baseClosePrice = baseClose,
                jumpParams = jumpParams,
                jumpTriggerUniform = jumpTrigger.value,
                jumpSizeNormal = jumpSize.value
            )
        } else {
            baseClose
        }

        val (highPrice, lowPrice) = MarketFormulas.computeIntrabarHighLow(
            open = openPrice,
            close = closePrice,
            annualVolatility = effectiveParams.annualVolatility,
            upperUniform = upperWickSample.value,
            lowerUniform = lowerWickSample.value
        )

        val volume = MarketFormulas.computeSyntheticVolume(
            baseVolume = state.spec.baseVolumePerTick,
            liquidityTier = state.spec.liquidityTier,
            open = openPrice,
            close = closePrice,
            annualVolatility = effectiveParams.annualVolatility,
            uniformNoise = volumeNoiseSample.value
        )

        val newCandle = Candle(
            index = state.tickIndex,
            startTick = state.tickIndex,
            open = openPrice,
            high = highPrice,
            low = lowPrice,
            close = closePrice,
            volume = volume
        )

        val dt = 1.0 / GameBalance.TICKS_PER_TRADING_YEAR
        val realizedLogReturn = ln(max(closePrice, GameBalance.MIN_ASSET_PRICE_DOUBLE) / openPrice)
        val stepInnovation = realizedLogReturn - effectiveParams.annualDrift * dt
        val fallbackDiffusionInnovation = garchBaseAnnualVol * sqrt(dt) * diffusionNormalShock
        val recordedInnovation = if (stepInnovation.isFinite()) stepInnovation else fallbackDiffusionInnovation

        val updatedCandles = appendWithCap(
            existing = state.baseCandles,
            newCandle = newCandle,
            maxSize = GameBalance.MAX_STORED_HISTORY_CANDLES
        )

        return state.copy(
            rngState = nextRng,
            tickIndex = state.tickIndex + 1L,
            currentPrice = closePrice,
            previousClosePrice = openPrice,
            baseCandles = updatedCandles,
            currentAnnualVolatility = garchBaseAnnualVol,
            lastReturnInnovation = recordedInnovation
        )
    }

    private fun appendWithCap(existing: List<Candle>, newCandle: Candle, maxSize: Int): List<Candle> {
        val next = ArrayList<Candle>(minOf(existing.size + 1, maxSize))
        val dropCount = (existing.size + 1 - maxSize).coerceAtLeast(0)
        for (idx in dropCount until existing.size) {
            next.add(existing[idx])
        }
        next.add(newCandle)
        return next
    }
}
