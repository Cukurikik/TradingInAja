package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.domain.model.Candle

/**
 * Meringkas daftar candle besar menjadi maksimal [maxBuckets] batang OHLC
 * sambil mempertahankan nilai high tertinggi, low terendah, open awal, dan close akhir tiap bucket.
 */
object CandleDownsampler {

    fun downsample(candles: List<Candle>, maxBuckets: Int = GameBalance.DEFAULT_CHART_MAX_RENDER_POINTS): List<Candle> {
        if (maxBuckets <= 0 || candles.size <= maxBuckets) {
            return candles
        }

        val result = ArrayList<Candle>(maxBuckets)
        val bucketWidth = candles.size.toDouble() / maxBuckets.toDouble()

        for (bucketIndex in 0 until maxBuckets) {
            val startIdx = (bucketIndex * bucketWidth).toInt().coerceIn(0, candles.lastIndex)
            val endExclusive = if (bucketIndex == maxBuckets - 1) {
                candles.size
            } else {
                ((bucketIndex + 1) * bucketWidth).toInt().coerceIn(startIdx + 1, candles.size)
            }

            val first = candles[startIdx]
            val last = candles[endExclusive - 1]
            var high = first.high
            var low = first.low
            var totalVolume = 0L

            for (idx in startIdx until endExclusive) {
                val item = candles[idx]
                if (item.high > high) high = item.high
                if (item.low < low) low = item.low
                totalVolume += item.volume
            }

            result.add(
                Candle(
                    index = first.index,
                    startTick = first.startTick,
                    open = first.open,
                    high = high,
                    low = low,
                    close = last.close,
                    volume = totalVolume
                )
            )
        }

        return result
    }
}
