package com.tradinginaja.simulation

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.simulation.catalog.CatalogLoader
import com.tradinginaja.simulation.catalog.EventCatalogLoader
import com.tradinginaja.simulation.engine.Scenario
import com.tradinginaja.simulation.engine.ScenarioRunner
import com.tradinginaja.simulation.engine.TradingStrategy
import com.tradinginaja.simulation.engine.defaultStrategies
import com.tradinginaja.simulation.report.RawReportWriter
import com.tradinginaja.simulation.report.ReportWriter
import com.tradinginaja.simulation.report.SimulationParameters
import com.tradinginaja.simulation.report.Statistics
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.system.exitProcess

private const val DEFAULT_RUNS = 1_000
private const val DEFAULT_TICKS = 1_000
private const val MINIMUM_AUDIT_RUNS = 1_000
private const val EXIT_INVALID_ARGUMENTS = 2
private const val ARG_PAIRS = 2

/**
 * Entry point skrip simulasi headless Fase 8.
 *
 * Dijalankan lewat task Gradle terpisah `:simulation:headlessSimulation`.
 * Semua keluaran ditulis ke direktori `--out` (Markdown + CSV).
 */
fun main(args: Array<String>) {
    val options = try {
        parseOptions(args)
    } catch (error: IllegalArgumentException) {
        System.err.println("Argumen tidak valid: ${error.message}")
        printUsage()
        exitProcess(EXIT_INVALID_ARGUMENTS)
    }

    val catalog = CatalogLoader.loadMarketCatalog(File(options.catalogPath))
    val eventCatalog = EventCatalogLoader.load(File(options.eventsPath))
    val scenarios = resolveScenarios(options.scenarioFilter)
    val strategies: List<TradingStrategy> = defaultStrategies()
    val labels = strategies.associate { it.id to it.label }

    println(
        "Simulasi headless: ${scenarios.size} skenario × ${options.runs} run × " +
            "${options.ticks} tick, ${catalog.assets.size} aset."
    )
    if (options.runs < MINIMUM_AUDIT_RUNS) {
        System.err.println(
            "PERINGATAN: ${options.runs} run < $MINIMUM_AUDIT_RUNS; hasil belum memenuhi syarat audit Fase 8."
        )
    }

    val startedAtNanos = System.nanoTime()
    val scenarioOutcomes = scenarios.map { scenario ->
        val scenarioStarted = System.nanoTime()
        val result = ScenarioRunner.run(
            scenario = scenario,
            catalog = catalog,
            eventCatalog = eventCatalog,
            runs = options.runs,
            ticks = options.ticks,
            baseSeed = options.baseSeed
        )
        val elapsedSeconds = (System.nanoTime() - scenarioStarted) / NANOS_PER_SECOND
        println("  selesai ${scenario.label} dalam " + String.format(Locale.US, "%.1f s", elapsedSeconds))
        result
    }
    val outcomes = scenarioOutcomes.flatMap { it.outcomes }
    val summaries = scenarioOutcomes.map { outcome -> Statistics.summarize(outcome, labels) }
    val latency = ScenarioRunner.measureTickLatency(catalog, eventCatalog, options.baseSeed)
    val totalSeconds = (System.nanoTime() - startedAtNanos) / NANOS_PER_SECOND

    val parameters = SimulationParameters(
        catalogPath = options.catalogPath,
        eventsPath = options.eventsPath,
        runs = options.runs,
        ticks = options.ticks,
        baseSeed = options.baseSeed,
        scenarioFilter = options.scenarioFilter
    )
    val generatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    val files = ReportWriter.writeAll(
        outputDirectory = File(options.outputDirectory),
        parameters = parameters,
        summaries = summaries,
        latency = latency,
        outcomes = outcomes,
        generatedAt = generatedAt
    )

    println()
    println(RawReportWriter.renderConsoleSummary(summaries, latency))
    println()
    files.forEach { println("Ditulis: ${it.absolutePath}") }
    println("Total waktu simulasi: " + String.format(Locale.US, "%.1f s", totalSeconds))
}

private data class Options(
    val catalogPath: String,
    val eventsPath: String,
    val runs: Int,
    val ticks: Int,
    val scenarioFilter: String,
    val baseSeed: Long,
    val outputDirectory: String
)

private fun parseOptions(args: Array<String>): Options {
    val map = HashMap<String, String>()
    var index = 0
    while (index < args.size) {
        val key = args[index]
        require(key.startsWith("--")) { "argumen tak dikenal: $key" }
        require(index + 1 < args.size) { "nilai untuk $key tidak ditemukan" }
        map[key] = args[index + 1]
        index += ARG_PAIRS
    }
    val runs = map["--runs"]?.toIntOrNull() ?: DEFAULT_RUNS
    val ticks = map["--ticks"]?.toIntOrNull() ?: DEFAULT_TICKS
    require(runs > 0) { "--runs harus > 0" }
    require(ticks > 0) { "--ticks harus > 0" }
    return Options(
        catalogPath = map["--catalog"] ?: "data/src/main/assets/market_catalog_v1.json",
        eventsPath = map["--events"] ?: "data/src/main/assets/market_events_v1.json",
        runs = runs,
        ticks = ticks,
        scenarioFilter = map["--scenario"] ?: "ALL",
        baseSeed = map["--seed"]?.toLongOrNull() ?: GameBalance.DEFAULT_SIMULATION_SEED,
        outputDirectory = map["--out"] ?: "simulation/build/reports/headless-simulation"
    )
}

private fun resolveScenarios(filter: String): List<Scenario> = if (filter.equals("ALL", ignoreCase = true)) {
    Scenario.entries.toList()
} else {
    listOf(Scenario.valueOf(filter.uppercase()))
}

private fun printUsage() {
    println(
        """
        Penggunaan:
          --catalog <path>   katalog aset JSON
          --events <path>    katalog event JSON
          --runs <n>         run per skenario (default $DEFAULT_RUNS)
          --ticks <n>        tick per run (default $DEFAULT_TICKS)
          --scenario <name>  ALL | ${Scenario.entries.joinToString(" | ") { it.name }}
          --seed <long>      seed dasar
          --out <dir>        direktori laporan
        """.trimIndent()
    )
}

private const val NANOS_PER_SECOND = 1_000_000_000.0
