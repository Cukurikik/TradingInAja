package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.core.common.Money
import com.tradinginaja.domain.model.Candle
import com.tradinginaja.domain.model.CandleDrillQuestion
import com.tradinginaja.domain.model.CandlePatternType
import com.tradinginaja.domain.model.CareerLevel
import com.tradinginaja.domain.model.LearnLesson
import com.tradinginaja.domain.model.LearnLessonIds
import com.tradinginaja.domain.model.RiskSizingSimulationResult
import com.tradinginaja.domain.model.TradingTerm
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Mesin Mode Belajar bebas risiko pure Kotlin:
 * - Kurikulum terstruktur 4 modul kompetensi
 * - Generator kuis pembacaan candlestick deterministik
 * - Simulator position sizing 1-2% tanpa menyentuh saldo karier
 */
object LearnPracticeEngine {

    private const val PERCENT_MULTIPLIER = 100.0
    private const val DRILL_CANDLE_COUNT = 18
    private const val BASE_PRICE = 5_000.0
    private const val BASE_VOLUME = 12_000L
    private const val DRILL_RNG_SALT = 88_120L

    fun defaultLessons(): List<LearnLesson> = listOf(
        LearnLesson(
            id = LearnLessonIds.BASICS_SPREAD_SL,
            recommendedForLevel = CareerLevel.LEVEL_1_NOVICE,
            highlightedTerms = listOf(TradingTerm.SPREAD, TradingTerm.SLIPPAGE, TradingTerm.STOP_LOSS)
        ),
        LearnLesson(
            id = LearnLessonIds.RISK_ONE_TWO_PERCENT,
            recommendedForLevel = CareerLevel.LEVEL_2_ACTIVE,
            highlightedTerms = listOf(
                TradingTerm.POSITION_SIZING,
                TradingTerm.RISK_REWARD,
                TradingTerm.OCO_BRACKET
            )
        ),
        LearnLesson(
            id = LearnLessonIds.CANDLE_AND_REGIME,
            recommendedForLevel = CareerLevel.LEVEL_2_ACTIVE,
            highlightedTerms = listOf(TradingTerm.CANDLESTICK_OHLC, TradingTerm.DRAWDOWN)
        ),
        LearnLesson(
            id = LearnLessonIds.MARGIN_ANTI_LIQUIDATION,
            recommendedForLevel = CareerLevel.LEVEL_3_PRO,
            highlightedTerms = listOf(TradingTerm.LEVERAGE, TradingTerm.MARGIN_CALL)
        )
    )

    fun generateCandleDrill(drillIndex: Int, seed: Long = GameBalance.DEFAULT_SIMULATION_SEED): CandleDrillQuestion {
        val patterns = CandlePatternType.entries
        val safeIndex = drillIndex.coerceAtLeast(0)
        val correctPattern = patterns[safeIndex % patterns.size]
        var rngState = DeterministicRng.initialState(seed = seed, salt = safeIndex.toLong() + DRILL_RNG_SALT)

        val leadCandles = mutableListOf<Candle>()
        var price = BASE_PRICE + (safeIndex % 5) * 180.0
        for (i in 0 until DRILL_CANDLE_COUNT - 1) {
            val zBody = DeterministicRng.nextStandardNormal(rngState)
            rngState = zBody.nextState
            val stepShift = when (correctPattern) {
                CandlePatternType.BULLISH_MOMENTUM -> (zBody.value * 0.008) + 0.004
                CandlePatternType.BEARISH_REJECTION_WICK -> (zBody.value * 0.008) + 0.006
                CandlePatternType.BULLISH_HAMMER_REVERSAL -> (zBody.value * 0.007) - 0.009
                CandlePatternType.VOLATILE_INDECISION -> zBody.value * 0.012
            }
            val zHigh = DeterministicRng.nextStandardNormal(rngState)
            rngState = zHigh.nextState
            val zLow = DeterministicRng.nextStandardNormal(rngState)
            rngState = zLow.nextState

            val open = price
            val close = (open * (1.0 + stepShift)).coerceAtLeast(GameBalance.MIN_ASSET_PRICE_DOUBLE)
            val high = maxOf(open, close) * (1.0 + 0.004 + abs(zHigh.value) * 0.004)
            val low = (minOf(open, close) * (1.0 - 0.004 - abs(zLow.value) * 0.004))
                .coerceAtLeast(GameBalance.MIN_ASSET_PRICE_DOUBLE)
            leadCandles.add(
                Candle(
                    index = i.toLong(),
                    startTick = i.toLong(),
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = BASE_VOLUME + (i * 150L)
                )
            )
            price = close
        }

        val finalCandle = buildSignalCandle(
            index = DRILL_CANDLE_COUNT - 1,
            anchorPrice = price,
            pattern = correctPattern
        )
        return CandleDrillQuestion(
            drillIndex = safeIndex,
            candles = leadCandles + finalCandle,
            correctPattern = correctPattern,
            options = patterns
        )
    }

    fun simulatePositionSizing(
        accountEquity: Money,
        targetRiskPct: Double,
        entryPrice: Money,
        stopLossPrice: Money
    ): RiskSizingSimulationResult {
        val safeEquityMinor = accountEquity.minorUnits.coerceAtLeast(0L)
        val clampedRiskPct = targetRiskPct.coerceIn(
            GameBalance.LEARN_SANDBOX_MIN_RISK_PCT,
            GameBalance.LEARN_SANDBOX_MAX_RISK_PCT
        )
        val maxRiskMinor = ((safeEquityMinor.toDouble() * clampedRiskPct) / PERCENT_MULTIPLIER).roundToLong()
        val riskPerUnitMinor = abs(entryPrice.minorUnits - stopLossPrice.minorUnits)
        val recommendedUnits = if (riskPerUnitMinor > 0L) {
            (maxRiskMinor / riskPerUnitMinor).coerceAtLeast(0L)
        } else {
            0L
        }
        val actualRiskMinor = riskPerUnitMinor * recommendedUnits
        val actualRiskPct = if (safeEquityMinor > 0L) {
            (actualRiskMinor.toDouble() / safeEquityMinor.toDouble()) * PERCENT_MULTIPLIER
        } else {
            0.0
        }

        return RiskSizingSimulationResult(
            accountEquity = Money(safeEquityMinor),
            targetRiskPct = clampedRiskPct,
            maxRiskBudget = Money(maxRiskMinor),
            entryPrice = entryPrice,
            stopLossPrice = stopLossPrice,
            riskPerUnit = Money(riskPerUnitMinor),
            recommendedUnits = recommendedUnits,
            actualRiskAmount = Money(actualRiskMinor),
            actualRiskPct = actualRiskPct,
            isWithinRule = actualRiskPct <= GameBalance.MAX_DISCIPLINED_RISK_PER_TRADE_PCT
        )
    }

    private fun buildSignalCandle(index: Int, anchorPrice: Double, pattern: CandlePatternType): Candle {
        val tick = index.toLong()
        return when (pattern) {
            CandlePatternType.BULLISH_MOMENTUM -> {
                val close = anchorPrice * 1.045
                Candle(
                    index = tick,
                    startTick = tick,
                    open = anchorPrice,
                    high = close * 1.004,
                    low = anchorPrice * 0.997,
                    close = close,
                    volume = BASE_VOLUME * 2L
                )
            }
            CandlePatternType.BEARISH_REJECTION_WICK -> {
                val close = anchorPrice * 0.992
                Candle(
                    index = tick,
                    startTick = tick,
                    open = anchorPrice,
                    high = anchorPrice * 1.052,
                    low = close * 0.996,
                    close = close,
                    volume = BASE_VOLUME * 2L
                )
            }
            CandlePatternType.BULLISH_HAMMER_REVERSAL -> {
                val close = anchorPrice * 1.012
                Candle(
                    index = tick,
                    startTick = tick,
                    open = anchorPrice,
                    high = close * 1.003,
                    low = anchorPrice * 0.945,
                    close = close,
                    volume = BASE_VOLUME * 2L
                )
            }
            CandlePatternType.VOLATILE_INDECISION -> {
                val close = anchorPrice * 1.002
                Candle(
                    index = tick,
                    startTick = tick,
                    open = anchorPrice,
                    high = anchorPrice * 1.040,
                    low = anchorPrice * 0.960,
                    close = close,
                    volume = (BASE_VOLUME * 3L) / 2L
                )
            }
        }
    }
}
