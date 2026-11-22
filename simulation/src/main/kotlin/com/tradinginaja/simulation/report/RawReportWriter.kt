package com.tradinginaja.simulation.report

import com.tradinginaja.simulation.engine.RunOutcome
import com.tradinginaja.simulation.engine.TickLatencyProfile
import java.util.Locale

/**
 * Penulis keluaran mentah: CSV per run dan ringkasan konsol.
 */
object RawReportWriter {

    fun renderCsv(outcomes: List<RunOutcome>): String = buildString {
        appendLine("scenario,strategy,run,seed,total_return_pct,max_drawdown_pct,trades,fee_pct,final_equity_minor")
        for (outcome in outcomes) {
            appendLine(
                listOf(
                    outcome.scenario.name,
                    outcome.strategyId,
                    outcome.runIndex,
                    outcome.seed,
                    number(outcome.totalReturnPct),
                    number(outcome.maxDrawdownPct),
                    outcome.trades,
                    number(outcome.feePctOfInitial),
                    outcome.finalEquityMinor
                ).joinToString(",")
            )
        }
    }

    fun renderConsoleSummary(summaries: List<ScenarioSummary>, latency: TickLatencyProfile): String = buildString {
        appendLine("=== RINGKASAN SIMULASI HEADLESS FASE 8 ===")
        for (summary in summaries) {
            appendLine(
                "${summary.scenarioLabel} (${summary.runs} run): " +
                    summary.stats.joinToString(" | ") {
                        "${it.strategyId} median ${pct(it.medianReturnPct)} profit ${pct(it.profitableRunPct)}"
                    }
            )
            appendLine(
                "  pemenang=${summary.dominantStrategyId} inflasi=${index(summary.buyAndHoldInflationIndex)} " +
                    "aktif>B&H=${pct(summary.activeBeatsHoldRunPct)} run"
            )
        }
        appendLine(
            "Tick latency: p50=${micros(latency.p50Micros)} p95=${micros(latency.p95Micros)} " +
                "p99=${micros(latency.p99Micros)} max=${micros(latency.maxMicros)}"
        )
    }

    fun pct(value: Double): String = String.format(Locale.US, "%.2f%%", value)

    fun index(value: Double): String = if (value.isNaN()) "n/a" else String.format(Locale.US, "%.3f×", value)

    fun micros(value: Double): String = String.format(Locale.US, "%.1f µs", value)

    private fun number(value: Double): String = String.format(Locale.US, "%.4f", value)
}
