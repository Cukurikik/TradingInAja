package com.tradinginaja.domain.model

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.core.common.Money
import com.tradinginaja.core.common.MoneyRoundingMode
import kotlin.math.max
import kotlin.math.roundToLong

enum class CandleTimeframe(val ticksPerCandle: Int, val label: String) {
    TICK_15M(ticksPerCandle = 1, label = "15m"),
    HOUR_1(ticksPerCandle = 4, label = "1h"),
    HOUR_4(ticksPerCandle = 16, label = "4h"),
    DAY_1(ticksPerCandle = GameBalance.TICKS_PER_TRADING_DAY, label = "1d")
}

/**
 * Representasi satu batang candlestick OHLC beserta volume sintetis.
 */
data class Candle(
    val index: Long,
    val startTick: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
) {
    val isBullish: Boolean get() = close >= open

    fun openAsMoney(roundingMode: MoneyRoundingMode = MoneyRoundingMode.HALF_EVEN): Money =
        doublePriceToMoney(open, roundingMode)

    fun highAsMoney(roundingMode: MoneyRoundingMode = MoneyRoundingMode.HALF_EVEN): Money =
        doublePriceToMoney(high, roundingMode)

    fun lowAsMoney(roundingMode: MoneyRoundingMode = MoneyRoundingMode.HALF_EVEN): Money =
        doublePriceToMoney(low, roundingMode)

    fun closeAsMoney(roundingMode: MoneyRoundingMode = MoneyRoundingMode.HALF_EVEN): Money =
        doublePriceToMoney(close, roundingMode)

    companion object {
        fun doublePriceToMoney(price: Double, roundingMode: MoneyRoundingMode = MoneyRoundingMode.HALF_EVEN): Money {
            val safePrice = max(price, GameBalance.MIN_ASSET_PRICE_DOUBLE)
            val converted = when (roundingMode) {
                MoneyRoundingMode.HALF_UP, MoneyRoundingMode.HALF_EVEN -> {
                    val scaled = safePrice * GameBalance.MINOR_UNITS_PER_MAJOR
                    scaled.roundToLong()
                }
                MoneyRoundingMode.DOWN -> {
                    (safePrice * GameBalance.MINOR_UNITS_PER_MAJOR).toLong()
                }
                MoneyRoundingMode.UP -> {
                    kotlin.math.ceil(safePrice * GameBalance.MINOR_UNITS_PER_MAJOR).toLong()
                }
            }
            return Money(max(converted, GameBalance.MIN_ASSET_PRICE_MINOR))
        }
    }
}

/**
 * Mengagregasikan daftar candle dasar (15m / 1 tick) menjadi timeframe lebih besar.
 */
object CandleAggregator {

    fun aggregate(baseCandles: List<Candle>, timeframe: CandleTimeframe): List<Candle> {
        if (baseCandles.isEmpty() || timeframe == CandleTimeframe.TICK_15M) {
            return baseCandles
        }

        val sorted = baseCandles.sortedBy { it.startTick }
        val bucketSize = timeframe.ticksPerCandle.toLong()
        val aggregated = ArrayList<Candle>(sorted.size / timeframe.ticksPerCandle + 1)

        var currentBucket = sorted.first().startTick / bucketSize
        var open = sorted.first().open
        var high = sorted.first().high
        var low = sorted.first().low
        var close = sorted.first().close
        var volume = sorted.first().volume

        for (idx in 1 until sorted.size) {
            val candle = sorted[idx]
            val bucket = candle.startTick / bucketSize
            if (bucket == currentBucket) {
                high = maxOf(high, candle.high)
                low = minOf(low, candle.low)
                close = candle.close
                volume += candle.volume
            } else {
                aggregated.add(
                    Candle(
                        index = currentBucket,
                        startTick = currentBucket * bucketSize,
                        open = open,
                        high = high,
                        low = low,
                        close = close,
                        volume = volume
                    )
                )
                currentBucket = bucket
                open = candle.open
                high = candle.high
                low = candle.low
                close = candle.close
                volume = candle.volume
            }
        }

        aggregated.add(
            Candle(
                index = currentBucket,
                startTick = currentBucket * bucketSize,
                open = open,
                high = high,
                low = low,
                close = close,
                volume = volume
            )
        )

        return aggregated
    }
}
