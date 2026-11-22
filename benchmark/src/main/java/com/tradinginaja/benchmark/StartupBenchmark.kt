package com.tradinginaja.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Id aplikasi target harus sama dengan applicationId di app/build.gradle.kts.
private const val TARGET_PACKAGE = "com.aistudio.tradinginaja.v4q8nz"

/**
 * Benchmark startup aplikasi (cold & warm).
 *
 * Target Fase 8 pada perangkat low-end: startup ke frame pertama < 1.500 ms (cold) dan
 * tidak ada frame > 16 ms pada animasi masuk.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        compilationMode = COMPILATION
    ) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.textContains("SIM")), TIMEOUT_MILLIS)
    }

    @Test
    fun warmStartup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        compilationMode = COMPILATION
    ) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.textContains("SIM")), TIMEOUT_MILLIS)
    }

    private companion object {
        const val ITERATIONS = 10
        const val TIMEOUT_MILLIS = 10_000L
        val COMPILATION = CompilationMode.Partial(warmupIterations = 3)
    }
}
