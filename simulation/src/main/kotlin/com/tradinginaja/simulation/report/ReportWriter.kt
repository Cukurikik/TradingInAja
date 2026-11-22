package com.tradinginaja.simulation.report

import com.tradinginaja.simulation.engine.RunOutcome
import com.tradinginaja.simulation.engine.TickLatencyProfile
import java.io.File

/** Parameter run yang dicatat di laporan agar hasil reproducible. */
data class SimulationParameters(
    val catalogPath: String,
    val eventsPath: String,
    val runs: Int,
    val ticks: Int,
    val baseSeed: Long,
    val scenarioFilter: String
)

/**
 * Penulis laporan: Markdown ringkas untuk audit, CSV mentah untuk analisis lanjutan,
 * dan ringkasan teks ke konsol.
 */
object ReportWriter {

    private const val DOMINANCE_GAP_PCT = 2.0
    private const val INFLATION_WARNING_INDEX = 1.15
    private const val DEFLATION_WARNING_INDEX = 0.95
    private const val LOW_END_TICK_BUDGET_MICROS = 16_000.0

    fun renderMarkdown(
        parameters: SimulationParameters,
        summaries: List<ScenarioSummary>,
        latency: TickLatencyProfile,
        outcomes: List<RunOutcome>,
        generatedAt: String
    ): String = buildString {
        appendLine("# Laporan Simulasi Headless — Fase 8")
        appendLine()
        appendLine("Dibuat: $generatedAt")
        appendLine()
        appendLine("## Parameter Run")
        appendLine()
        appendLine("| Parameter | Nilai |")
        appendLine("|---|---|")
        appendLine("| Katalog | `$CATALOG_LABEL` |")
        appendLine("| Event | `$EVENTS_LABEL` |")
        appendLine("| Run per skenario | ${parameters.runs} |")
        appendLine("| Tick per run | ${parameters.ticks} |")
        appendLine("| Seed dasar | ${parameters.baseSeed} |")
        appendLine("| Filter skenario | ${parameters.scenarioFilter} |")
        appendLine("| Total run | ${outcomes.size} |")
        appendLine("| Total tick simulasi | ${outcomes.size} × ${parameters.ticks} |")
        appendLine()
        appendLine("Strategi yang diuji: " + outcomes.map { it.strategyId }.distinct().joinToString(", ") { "`$it`" })
        appendLine()
        appendLine("## Distribusi Hasil per Skenario")
        appendLine()
        for (summary in summaries) {
            appendScenarioSection(summary)
        }
        appendLine("## Deteksi Strategi Dominan")
        appendLine()
        appendDominanceSection(summaries)
        appendLine()
        appendLine("## Inflasi Uang Virtual")
        appendLine()
        appendInflationSection(summaries)
        appendLine()
        appendLine("## Rasio Buy-and-Hold vs Strategi Aktif")
        appendLine()
        appendHoldVersusActiveSection(summaries)
        appendLine()
        appendLine("## Latensi Tick Engine (proxy JVM desktop)")
        appendLine()
        appendLatencySection(latency)
        appendLine()
    }

    private fun StringBuilder.appendScenarioSection(summary: ScenarioSummary) {
        appendLine("### ${summary.scenarioLabel} (`${summary.scenarioId}`)")
        appendLine()
        appendLine("${summary.runs} run × ${summary.ticksPerRun} tick.")
        appendLine()
        appendLine(
            "| Strategi | Median | Mean | P5 | P25 | P75 | P95 | Run profit | Max DD median | " +
                "Trade median | Fee median |"
        )
        appendLine("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
        for (stats in summary.stats) {
            appendLine(
                "| ${stats.label} | ${pct(stats.medianReturnPct)} | ${pct(stats.meanReturnPct)} | " +
                    "${pct(stats.p5ReturnPct)} | ${pct(stats.p25ReturnPct)} | ${pct(stats.p75ReturnPct)} | " +
                    "${pct(stats.p95ReturnPct)} | ${pct(stats.profitableRunPct)} | " +
                    "${pct(stats.medianDrawdownPct)} | ${stats.medianTrades.toInt()} | ${pct(stats.medianFeePct)} |"
            )
        }
        appendLine()
    }

    private fun StringBuilder.appendDominanceSection(summaries: List<ScenarioSummary>) {
        appendLine("| Skenario | Strategi median tertinggi | Median | Selisih vs Buy & Hold |")
        appendLine("|---|---|---:|---:|")
        var holdWins = 0
        for (summary in summaries) {
            val winner = summary.stats.first()
            val hold = summary.stats.first { it.strategyId == "buy_and_hold" }
            if (winner.strategyId == "buy_and_hold") holdWins += 1
            appendLine(
                "| ${summary.scenarioLabel} | ${winner.label} | ${pct(winner.medianReturnPct)} | " +
                    "${pct(winner.medianReturnPct - hold.medianReturnPct)} |"
            )
        }
        appendLine()
        val verdict = when {
            holdWins == summaries.size ->
                "**TEMUAN: buy-and-hold mendominasi SEMUA skenario.** Strategi aktif tidak memberi edge."
            holdWins * 2 >= summaries.size ->
                "**TEMUAN: buy-and-hold dominan di $holdWins/${summaries.size} skenario.** " +
                    "Perlu penyesuaian balancing agar strategi aktif kompetitif."
            holdWins == 0 ->
                "Buy-and-hold tidak menang di skenario mana pun; strategi aktif kompetitif."
            else ->
                "Buy-and-hold menang di $holdWins/${summaries.size} skenario; dominasi parsial."
        }
        appendLine(verdict)
    }

    private fun StringBuilder.appendInflationSection(summaries: List<ScenarioSummary>) {
        appendLine("Inflasi uang = median ekuitas akhir buy-and-hold ÷ modal awal.")
        appendLine()
        appendLine("| Skenario | Indeks inflasi | Median return B&H | Status |")
        appendLine("|---|---:|---:|---|")
        for (summary in summaries) {
            val hold = summary.stats.first { it.strategyId == "buy_and_hold" }
            val status = when {
                summary.buyAndHoldInflationIndex >= INFLATION_WARNING_INDEX ->
                    "Inflasi tinggi — modal tumbuh tanpa keterampilan"
                summary.buyAndHoldInflationIndex < DEFLATION_WARNING_INDEX ->
                    "Deflasi — median pemain kehilangan modal"
                else -> "Wajar"
            }
            appendLine(
                "| ${summary.scenarioLabel} | ${index(summary.buyAndHoldInflationIndex)} | " +
                    "${pct(hold.medianReturnPct)} | $status |"
            )
        }
        appendLine()
    }

    private fun StringBuilder.appendHoldVersusActiveSection(summaries: List<ScenarioSummary>) {
        appendLine("| Skenario | Median B&H | Median aktif terbaik | Rasio aktif/B&H | Run di mana aktif > B&H |")
        appendLine("|---|---:|---:|---:|---:|")
        for (summary in summaries) {
            val hold = summary.stats.first { it.strategyId == "buy_and_hold" }
            val ratio = if (hold.medianReturnPct <= 0.0 || summary.bestActiveMedianReturnPct <= 0.0) {
                Double.NaN
            } else {
                summary.bestActiveMedianReturnPct / hold.medianReturnPct
            }
            appendLine(
                "| ${summary.scenarioLabel} | ${pct(hold.medianReturnPct)} | " +
                    "${pct(summary.bestActiveMedianReturnPct)} | ${index(ratio)} | " +
                    "${pct(summary.activeBeatsHoldRunPct)} |"
            )
        }
        appendLine()
        val worstGap = summaries.minOf { summary ->
            val hold = summary.stats.first { it.strategyId == "buy_and_hold" }
            summary.stats.first().medianReturnPct - hold.medianReturnPct
        }
        if (worstGap < -DOMINANCE_GAP_PCT) {
            appendLine(
                "Selisih median terburuk antara pemenang skenario dan buy-and-hold: ${pct(worstGap)}."
            )
        }
    }

    private fun StringBuilder.appendLatencySection(latency: TickLatencyProfile) {
        appendLine("| Metrik | Nilai |")
        appendLine("|---|---:|")
        appendLine("| Sampel | ${latency.samples} |")
        appendLine("| P50 | ${micros(latency.p50Micros)} |")
        appendLine("| P95 | ${micros(latency.p95Micros)} |")
        appendLine("| P99 | ${micros(latency.p99Micros)} |")
        appendLine("| Max | ${micros(latency.maxMicros)} |")
        appendLine("| Mean | ${micros(latency.meanMicros)} |")
        appendLine()
        val status = if (latency.p99Micros < LOW_END_TICK_BUDGET_MICROS) {
            "P99 di bawah anggaran 16 ms/tick."
        } else {
            "P99 MELEWATI anggaran 16 ms/tick."
        }
        appendLine(
            status + " Angka ini dari JVM desktop (x86_64, JIT hangat) sehingga hanya batas bawah; " +
                "verifikasi perangkat low-end wajib lewat macrobenchmark."
        )
    }

    fun writeAll(
        outputDirectory: File,
        parameters: SimulationParameters,
        summaries: List<ScenarioSummary>,
        latency: TickLatencyProfile,
        outcomes: List<RunOutcome>,
        generatedAt: String
    ): List<File> {
        outputDirectory.mkdirs()
        val markdown = File(outputDirectory, "headless-simulation-report.md")
        val csv = File(outputDirectory, "headless-simulation-runs.csv")
        markdown.writeText(renderMarkdown(parameters, summaries, latency, outcomes, generatedAt))
        csv.writeText(RawReportWriter.renderCsv(outcomes))
        return listOf(markdown, csv)
    }

    private fun pct(value: Double): String = RawReportWriter.pct(value)

    private fun index(value: Double): String = RawReportWriter.index(value)

    private fun micros(value: Double): String = RawReportWriter.micros(value)

    private const val CATALOG_LABEL = "data/src/main/assets/market_catalog_v1.json"
    private const val EVENTS_LABEL = "data/src/main/assets/market_events_v1.json"
}
