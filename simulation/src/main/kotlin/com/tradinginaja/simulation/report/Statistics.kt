package com.tradinginaja.simulation.report

import com.tradinginaja.simulation.engine.RunOutcome
import com.tradinginaja.simulation.engine.ScenarioOutcome
import kotlin.math.roundToLong

/** Ringkasan statistik satu strategi pada satu skenario. */
data class StrategyStats(
    val strategyId: String,
    val label: String,
    val runCount: Int,
    val medianReturnPct: Double,
    val meanReturnPct: Double,
    val p5ReturnPct: Double,
    val p25ReturnPct: Double,
    val p75ReturnPct: Double,
    val p95ReturnPct: Double,
    val profitableRunPct: Double,
    val medianDrawdownPct: Double,
    val medianTrades: Double,
    val medianFeePct: Double,
    val medianFinalEquityMinor: Long
)

/** Ringkasan satu skenario: statistik semua strategi + metrik dominasi & inflasi uang. */
data class ScenarioSummary(
    val scenarioId: String,
    val scenarioLabel: String,
    val runs: Int,
    val ticksPerRun: Int,
    val stats: List<StrategyStats>,
    val dominantStrategyId: String,
    val buyAndHoldInflationIndex: Double,
    val bestActiveMedianReturnPct: Double,
    val activeBeatsHoldRunPct: Double
)

object Statistics {

    private const val PERCENT = 100.0
    private const val BUY_AND_HOLD_ID = "buy_and_hold"
    private const val ACTIVE_EDGE_THRESHOLD_PCT = 0.0

    fun summarize(outcome: ScenarioOutcome, labels: Map<String, String>): ScenarioSummary {
        val byStrategy = outcome.outcomes.groupBy { it.strategyId }
        val stats = byStrategy.map { (strategyId, runs) ->
            val returns = runs.map { it.totalReturnPct }.sorted()
            StrategyStats(
                strategyId = strategyId,
                label = labels[strategyId] ?: strategyId,
                runCount = runs.size,
                medianReturnPct = percentile(returns, 0.50),
                meanReturnPct = returns.average(),
                p5ReturnPct = percentile(returns, 0.05),
                p25ReturnPct = percentile(returns, 0.25),
                p75ReturnPct = percentile(returns, 0.75),
                p95ReturnPct = percentile(returns, 0.95),
                profitableRunPct = returns.count { it > ACTIVE_EDGE_THRESHOLD_PCT } / returns.size.toDouble() * PERCENT,
                medianDrawdownPct = percentile(runs.map { it.maxDrawdownPct }.sorted(), 0.50),
                medianTrades = percentile(runs.map { it.trades.toDouble() }.sorted(), 0.50),
                medianFeePct = percentile(runs.map { it.feePctOfInitial }.sorted(), 0.50),
                medianFinalEquityMinor = percentile(
                    runs.map { it.finalEquityMinor.toDouble() }.sorted(),
                    0.50
                ).roundToLong()
            )
        }.sortedByDescending { it.medianReturnPct }

        val dominant = stats.first()
        val buyAndHold = stats.first { it.strategyId == BUY_AND_HOLD_ID }
        val activeStats = stats.filter { it.strategyId != BUY_AND_HOLD_ID }
        val bestActiveMedian = activeStats.maxOfOrNull { it.medianReturnPct } ?: Double.NaN

        val runsByIndex = outcome.outcomes.groupBy { it.runIndex }
        val activeBeatRuns = runsByIndex.values.count { runOutcomes ->
            val hold = runOutcomes.firstOrDefault(BUY_AND_HOLD_ID) ?: return@count false
            val bestActive = runOutcomes
                .filter { it.strategyId != BUY_AND_HOLD_ID }
                .maxOfOrNull { it.totalReturnPct } ?: return@count false
            bestActive > hold.totalReturnPct
        }

        return ScenarioSummary(
            scenarioId = outcome.scenario.name,
            scenarioLabel = outcome.scenario.label,
            runs = outcome.runs,
            ticksPerRun = outcome.ticksPerRun,
            stats = stats,
            dominantStrategyId = dominant.strategyId,
            buyAndHoldInflationIndex = buyAndHold.medianFinalEquityMinor.toDouble() /
                outcome.initialEquityMinor.toDouble(),
            bestActiveMedianReturnPct = bestActiveMedian,
            activeBeatsHoldRunPct = activeBeatRuns.toDouble() / runsByIndex.size.toDouble() * PERCENT
        )
    }

    fun percentile(sortedValues: List<Double>, fraction: Double): Double {
        if (sortedValues.isEmpty()) return Double.NaN
        val index = (fraction * (sortedValues.size - 1)).toInt().coerceIn(0, sortedValues.lastIndex)
        return sortedValues[index]
    }

    private fun List<RunOutcome>.firstOrDefault(strategyId: String): RunOutcome? =
        firstOrNull { it.strategyId == strategyId }
}
