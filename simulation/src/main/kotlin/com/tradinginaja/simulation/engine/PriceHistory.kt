package com.tradinginaja.simulation.engine

import kotlin.math.sqrt

/**
 * Riwayat harga penutupan ring-buffer per aset untuk sinyal strategi aktif.
 * Kapasitas tetap agar biaya memori konstan selama 1.000+ tick per run.
 */
class PriceHistory(private val symbolCount: Int, private val capacity: Int) {

    private val closes = Array(symbolCount) { DoubleArray(capacity) }
    private var cursor = 0
    private var size = 0

    /** Mengisi riwayat awal (warm-up candle) sehingga deret berakhir tepat sebelum cursor. */
    fun seed(index: Int, series: List<Double>) {
        val usable = minOf(series.size, capacity)
        val offset = series.size - usable
        for (step in 0 until usable) {
            closes[index][capacity - usable + step] = series[offset + step]
        }
        if (usable > size) size = usable
    }

    fun push(prices: DoubleArray) {
        for (index in 0 until symbolCount) {
            closes[index][cursor] = prices[index]
        }
        cursor = (cursor + 1) % capacity
        if (size < capacity) size += 1
    }

    fun sampleCount(): Int = size

    fun last(index: Int): Double = closes[index][position(0)]

    fun sma(index: Int, periods: Int): Double? {
        if (size < periods) return null
        var sum = 0.0
        for (back in 0 until periods) {
            sum += closes[index][position(back)]
        }
        return sum / periods
    }

    fun stdDev(index: Int, periods: Int, mean: Double): Double? {
        if (size < periods) return null
        var sum = 0.0
        for (back in 0 until periods) {
            val delta = closes[index][position(back)] - mean
            sum += delta * delta
        }
        return sqrt(sum / periods)
    }

    private fun position(back: Int): Int {
        val raw = cursor - 1 - back
        return ((raw % capacity) + capacity) % capacity
    }
}
