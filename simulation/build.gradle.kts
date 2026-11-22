import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":domain"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.javax.inject)
}

/** Direktori laporan hasil simulasi headless (Markdown, CSV, latensi tick). */
val headlessReportDir: Provider<Directory> = layout.buildDirectory.dir("reports/headless-simulation")

/**
 * Skrip simulasi headless Fase 8 — terpisah dari task `test`.
 *
 * Contoh:
 *   ./gradlew :simulation:headlessSimulation
 *   ./gradlew :simulation:headlessSimulation -PsimRuns=1000 -PsimTicks=1000 -PsimScenario=ALL
 *
 * Parameter:
 *   -PsimRuns      jumlah run per skenario (default 1000, minimum yang divalidasi 1.000)
 *   -PsimTicks     jumlah tick simulasi per run (default 1.000 tick ≈ 38 hari bursa)
 *   -PsimScenario  ALL | BULL_RUN | BEAR_CRASH | SIDEWAYS_DRIFT | CRISIS_SHOCK | MIXED_REGIME
 *   -PsimSeed      seed dasar (default GameBalance.DEFAULT_SIMULATION_SEED)
 */
tasks.register<JavaExec>("headlessSimulation") {
    group = "verification"
    description = "Menjalankan 1.000+ simulasi headless per skenario dan menulis laporan distribusi hasil."

    mainClass.set("com.tradinginaja.simulation.HeadlessSimulationKt")
    classpath = sourceSets["main"].runtimeClasspath
    outputs.dir(headlessReportDir).withPropertyName("headlessReport")
    outputs.upToDateWhen { false }

    val catalogFile = rootProject.file("data/src/main/assets/market_catalog_v1.json")
    val eventsFile = rootProject.file("data/src/main/assets/market_events_v1.json")
    inputs.file(catalogFile).withPropertyName("catalog")
    inputs.file(eventsFile).withPropertyName("events")

    // Provider (bukan `project`) dipakai di dalam argumentProvider supaya task
    // kompatibel dengan configuration cache yang aktif di gradle.properties.
    val simRuns: Provider<String> = providers.gradleProperty("simRuns").orElse("1000")
    val simTicks: Provider<String> = providers.gradleProperty("simTicks").orElse("1000")
    val simScenario: Provider<String> = providers.gradleProperty("simScenario").orElse("ALL")
    val simSeed: Provider<String> = providers.gradleProperty("simSeed").orElse("20261121")
    val reportDirPath: Provider<String> = headlessReportDir.map { it.asFile.absolutePath }

    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "--catalog", catalogFile.absolutePath,
                "--events", eventsFile.absolutePath,
                "--runs", simRuns.get(),
                "--ticks", simTicks.get(),
                "--scenario", simScenario.get(),
                "--seed", simSeed.get(),
                "--out", reportDirPath.get()
            )
        }
    )

    // Direktori laporan dibuat oleh ReportWriter (mkdirs) supaya task tidak butuh doFirst.
}
