package com.tradinginaja.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "com.aistudio.tradinginaja.v4q8nz"
private const val STARTUP_TIMEOUT_MILLIS = 15_000L

/**
 * Benchmark scroll & pan chart candlestick.
 *
 * Target Fase 8: P95 frameDurationCpuMs < 16 ms pada perangkat low-end saat pan/zoom chart
 * dengan 120 candle hasil downsampling.
 */
@RunWith(AndroidJUnit4::class)
class ChartScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun panAndZoomChart() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        compilationMode = CompilationMode.Partial(warmupIterations = 3),
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.openFirstAssetDetail()
        }
    ) {
        val chart = device.wait(Until.findObject(By.res("candlestick_chart")), STARTUP_TIMEOUT_MILLIS)
            ?: error("Chart candlestick tidak ditemukan pada detail aset.")
        repeat(SWIPE_COUNT) { index ->
            chart.swipe(Direction.RIGHT, SWIPE_RATIO, SWIPE_STEPS)
            chart.swipe(Direction.LEFT, SWIPE_RATIO, SWIPE_STEPS)
            if (index == SWIPE_COUNT - 1) chart.click()
        }
    }

    private fun UiDevice.openFirstAssetDetail() {
        val firstAsset = wait(Until.findObject(By.text("NSKP")), STARTUP_TIMEOUT_MILLIS)
            ?: error("Daftar aset tidak tampil.")
        firstAsset.click()
        wait(Until.hasObject(By.res("candlestick_chart")), STARTUP_TIMEOUT_MILLIS)
    }

    private companion object {
        const val ITERATIONS = 10
        const val SWIPE_COUNT = 4
        const val SWIPE_RATIO = 0.6f
        const val SWIPE_STEPS = 12
    }
}
