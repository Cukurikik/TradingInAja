package com.tradinginaja.domain.engine

import com.tradinginaja.domain.model.Candle
import com.tradinginaja.domain.model.CandleAggregator
import com.tradinginaja.domain.model.CandleTimeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandleAggregatorTest {

    @Test
    fun `aggregate groups 15m candles into 1h buckets accurately including partial tail`() {
        val base = (0L until 6L).map { idx ->
            Candle(
                index = idx,
                startTick = idx,
                open = 100.0 + idx,
                high = 110.0 + idx,
                low = 90.0 - idx,
                close = 101.0 + idx,
                volume = 1_000L + idx * 100L
            )
        }

        val hourly = CandleAggregator.aggregate(base, CandleTimeframe.HOUR_1)
        assertEquals(2, hourly.size)

        // Bucket 0: ticks 0..3
        val firstBucket = hourly[0]
        assertEquals(0L, firstBucket.startTick)
        assertEquals(100.0, firstBucket.open, 0.0)
        assertEquals(113.0, firstBucket.high, 0.0)
        assertEquals(87.0, firstBucket.low, 0.0)
        assertEquals(104.0, firstBucket.close, 0.0)
        assertEquals(1_000L + 1_100L + 1_200L + 1_300L, firstBucket.volume)

        // Bucket 1: partial ticks 4..5
        val secondBucket = hourly[1]
        assertEquals(4L, secondBucket.startTick)
        assertEquals(104.0, secondBucket.open, 0.0)
        assertEquals(115.0, secondBucket.high, 0.0)
        assertEquals(85.0, secondBucket.low, 0.0)
        assertEquals(106.0, secondBucket.close, 0.0)
        assertEquals(1_400L + 1_500L, secondBucket.volume)
    }

    @Test
    fun `aggregate returns empty list for empty input`() {
        assertTrue(CandleAggregator.aggregate(emptyList(), CandleTimeframe.DAY_1).isEmpty())
    }

    @Test
    fun `downsampler preserves global high, low, first open, last close, and total volume`() {
        val raw = (0L until 1_000L).map { idx ->
            Candle(
                index = idx,
                startTick = idx,
                open = 500.0 + (idx % 10),
                high = if (idx == 432L) 999.0 else 520.0,
                low = if (idx == 777L) 111.0 else 480.0,
                close = 505.0 + (idx % 7),
                volume = 100L
            )
        }

        val downsampled = CandleDownsampler.downsample(raw, maxBuckets = 60)
        assertEquals(60, downsampled.size)
        assertEquals(raw.first().open, downsampled.first().open, 0.0)
        assertEquals(raw.last().close, downsampled.last().close, 0.0)
        assertEquals(999.0, downsampled.maxOf { it.high }, 0.0)
        assertEquals(111.0, downsampled.minOf { it.low }, 0.0)
        assertEquals(raw.sumOf { it.volume }, downsampled.sumOf { it.volume })
    }
}
