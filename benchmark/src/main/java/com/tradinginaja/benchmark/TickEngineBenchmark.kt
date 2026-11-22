package com.tradinginaja.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "com.aistudio.tradinginaja.v4q8nz"
private const val STARTUP_TIMEOUT_MILLIS = 15_000L

/**
 * Benchmark tick engine: setiap tick memajukan 10 aset (GBM/OU + GARCH + korelasi + event)
 * dan merender ulang chart.
 *
 * Target Fase 8: P95 frameDurationCpuMs < 16 ms per tick pada perangkat low-end (RAM 3 GB).
 * Batas bawah lintas-platform diukur oleh `:simulation:headlessSimulation` (latensi stepAll di JVM).
 */
@RunWith(AndroidJUnit4::class)
class TickEngineBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun stepTicksOnDetailScreen() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        compilationMode = CompilationMode.Partial(warmupIterations = 3),
        setupBlock = {
            pressHome()
            startActivityAndWait()
            val firstAsset = device.wait(Until.findObject(By.text("NSKP")), STARTUP_TIMEOUT_MILLIS)
                ?: error("Daftar aset tidak tampil.")
            firstAsset.click()
            device.wait(Until.hasObject(By.res("sim_step_once")), STARTUP_TIMEOUT_MILLIS)
        }
    ) {
        val stepButton = device.wait(Until.findObject(By.res("sim_step_once")), STARTUP_TIMEOUT_MILLIS)
            ?: error("Tombol step once tidak ditemukan.")
        repeat(TICK_STEPS) {
            stepButton.click()
        }
        device.waitForIdle()
    }

    private companion object {
        const val ITERATIONS = 10
        const val TICK_STEPS = 40
    }
}
