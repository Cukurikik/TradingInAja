package com.tradinginaja.simulation.engine

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.domain.engine.MarketEngine
import com.tradinginaja.domain.engine.MultiAssetMarketState
import com.tradinginaja.domain.model.EventCatalog
import com.tradinginaja.domain.model.MarketCatalog
import com.tradinginaja.domain.model.MarketRegime

/**
 * Skenario pasar yang diuji pada Fase 8.
 * Empat skenario pertama memakai satu rezim konstan agar efek rezim terisolasi;
 * [MIXED_REGIME] menyalakan transisi Markov penuh seperti di produksi.
 */
enum class Scenario(val label: String, val initialRegime: MarketRegime, val enableRegimeSwitching: Boolean) {
    BULL_RUN("Bull Run", MarketRegime.BULL, false),
    BEAR_CRASH("Bear Crash", MarketRegime.BEAR, false),
    SIDEWAYS_DRIFT("Sideways Drift", MarketRegime.SIDEWAYS, false),
    CRISIS_SHOCK("Crisis Shock", MarketRegime.CRISIS, false),
    MIXED_REGIME("Mixed Regime (Markov aktif)", MarketRegime.SIDEWAYS, true)
}

/** Hasil satu strategi pada satu run. */
data class RunOutcome(
    val scenario: Scenario,
    val strategyId: String,
    val runIndex: Int,
    val seed: Long,
    val totalReturnPct: Double,
    val maxDrawdownPct: Double,
    val trades: Int,
    val feePctOfInitial: Double,
    val finalEquityMinor: Long
)

data class ScenarioOutcome(
    val scenario: Scenario,
    val runs: Int,
    val ticksPerRun: Int,
    val initialEquityMinor: Long,
    val outcomes: List<RunOutcome>
)

/** Latensi transisi pasar per tick (proxy JVM desktop, bukan pengukuran perangkat low-end). */
data class TickLatencyProfile(
    val samples: Int,
    val p50Micros: Double,
    val p95Micros: Double,
    val p99Micros: Double,
    val maxMicros: Double,
    val meanMicros: Double
)

object ScenarioRunner {

    const val WARMUP_CANDLES: Int = 120
    private const val HISTORY_CAPACITY = 80
    private const val SEED_STRIDE = 7919L
    private const val LATENCY_TICKS = 1_500
    private const val LATENCY_WARMUP_TICKS = 200
    private const val LATENCY_REPETITIONS = 4
    private const val NANOS_PER_MICRO = 1_000.0
    private const val PERCENT = 100.0
    private const val P50 = 0.50
    private const val P95 = 0.95
    private const val P99 = 0.99

    fun run(
        scenario: Scenario,
        catalog: MarketCatalog,
        eventCatalog: EventCatalog,
        runs: Int,
        ticks: Int,
        baseSeed: Long
    ): ScenarioOutcome {
        val initialEquity = GameBalance.INITIAL_VIRTUAL_CASH_MINOR
        val outcomes = ArrayList<RunOutcome>(runs * defaultStrategies().size)
        for (runIndex in 0 until runs) {
            outcomes += runOnce(
                scenario = scenario,
                catalog = catalog,
                eventCatalog = eventCatalog,
                ticks = ticks,
                seed = baseSeed + runIndex * SEED_STRIDE,
                runIndex = runIndex,
                initialEquityMinor = initialEquity
            )
        }
        return ScenarioOutcome(
            scenario = scenario,
            runs = runs,
            ticksPerRun = ticks,
            initialEquityMinor = initialEquity,
            outcomes = outcomes
        )
    }

    private fun runOnce(
        scenario: Scenario,
        catalog: MarketCatalog,
        eventCatalog: EventCatalog,
        ticks: Int,
        seed: Long,
        runIndex: Int,
        initialEquityMinor: Long
    ): List<RunOutcome> {
        var state = MarketEngine.createMultiAssetInitialState(
            catalog = catalog,
            seed = seed,
            initialCandles = WARMUP_CANDLES,
            eventCatalog = eventCatalog,
            enableRegimeSwitching = scenario.enableRegimeSwitching,
            initialRegime = scenario.initialRegime
        )
        val specs = state.assetStates.map { it.spec }
        val history = PriceHistory(specs.size, HISTORY_CAPACITY)
        seedHistory(history, state)
        val strategies = defaultStrategies()
        val portfolios = strategies.map { PaperPortfolio(specs, initialEquityMinor) }
        val mids = LongArray(specs.size)

        for (tick in 0 until ticks) {
            state = MarketEngine.stepAll(state)
            fillPrices(state, history, mids)
            for (strategyIndex in strategies.indices) {
                val portfolio = portfolios[strategyIndex]
                strategies[strategyIndex].onTick(TickContext(tick, specs, history, mids, portfolio))
                portfolio.markToMarket(mids)
            }
        }

        return strategies.indices.map { strategyIndex ->
            val portfolio = portfolios[strategyIndex]
            val finalEquity = portfolio.equityMinor(mids)
            RunOutcome(
                scenario = scenario,
                strategyId = strategies[strategyIndex].id,
                runIndex = runIndex,
                seed = seed,
                totalReturnPct = (finalEquity - initialEquityMinor).toDouble() / initialEquityMinor * PERCENT,
                maxDrawdownPct = portfolio.maxDrawdownPct,
                trades = portfolio.tradeCount,
                feePctOfInitial = portfolio.feesPaidMinor.toDouble() / initialEquityMinor * PERCENT,
                finalEquityMinor = finalEquity
            )
        }
    }

    private fun seedHistory(history: PriceHistory, state: MultiAssetMarketState) {
        state.assetStates.forEachIndexed { index, assetState ->
            history.seed(index, assetState.baseCandles.map { it.close })
        }
    }

    private fun fillPrices(state: MultiAssetMarketState, history: PriceHistory, mids: LongArray) {
        val prices = DoubleArray(state.assetStates.size)
        state.assetStates.forEachIndexed { index, assetState ->
            prices[index] = assetState.currentPrice
            mids[index] = PaperPortfolio.priceToMinor(assetState.currentPrice)
        }
        history.push(prices)
    }

    /**
     * Mengukur biaya `MarketEngine.stepAll` (10 aset, event, GARCH, Cholesky) pada JVM desktop.
     * Angka ini adalah batas bawah optimistis untuk target < 16 ms/tick perangkat low-end.
     */
    fun measureTickLatency(catalog: MarketCatalog, eventCatalog: EventCatalog, baseSeed: Long): TickLatencyProfile {
        var state = MarketEngine.createMultiAssetInitialState(
            catalog = catalog,
            seed = baseSeed,
            initialCandles = WARMUP_CANDLES,
            eventCatalog = eventCatalog,
            enableRegimeSwitching = true
        )
        val samples = LongArray(LATENCY_TICKS * LATENCY_REPETITIONS)
        var sampleIndex = 0
        repeat(LATENCY_REPETITIONS) {
            repeat(LATENCY_WARMUP_TICKS) { state = MarketEngine.stepAll(state) }
            repeat(LATENCY_TICKS) {
                val started = System.nanoTime()
                state = MarketEngine.stepAll(state)
                samples[sampleIndex] = System.nanoTime() - started
                sampleIndex += 1
            }
        }
        samples.sort()
        return TickLatencyProfile(
            samples = samples.size,
            p50Micros = samples[percentileIndex(samples.size, P50)] / NANOS_PER_MICRO,
            p95Micros = samples[percentileIndex(samples.size, P95)] / NANOS_PER_MICRO,
            p99Micros = samples[percentileIndex(samples.size, P99)] / NANOS_PER_MICRO,
            maxMicros = samples.last() / NANOS_PER_MICRO,
            meanMicros = samples.average() / NANOS_PER_MICRO
        )
    }

    private fun percentileIndex(size: Int, percentile: Double): Int =
        (percentile * (size - 1)).toInt().coerceIn(0, size - 1)
}
