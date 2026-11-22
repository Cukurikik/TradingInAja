package com.tradinginaja.domain.model

import com.tradinginaja.core.common.Money

/**
 * Status pembelajaran dan tutorial pengguna yang disimpan lokal lewat DataStore.
 */
data class LearningProgress(
    val tutorialCompleted: Boolean = false,
    val completedLessonIds: Set<String> = emptySet(),
    val candleDrillsPassed: Int = 0
)

/**
 * Identifier stabil untuk seluruh materi edukasi terstruktur di Mode Belajar.
 */
object LearnLessonIds {
    const val BASICS_SPREAD_SL = "lesson_basics_spread_sl"
    const val RISK_ONE_TWO_PERCENT = "lesson_risk_1_2_pct"
    const val CANDLE_AND_REGIME = "lesson_candle_and_regime"
    const val MARGIN_ANTI_LIQUIDATION = "lesson_margin_anti_liquidation"
}

/**
 * Daftar istilah trading yang memiliki tooltip penjelasan in-context.
 */
enum class TradingTerm {
    SPREAD,
    SLIPPAGE,
    STOP_LOSS,
    OCO_BRACKET,
    LEVERAGE,
    MARGIN_CALL,
    DRAWDOWN,
    RISK_REWARD,
    CANDLESTICK_OHLC,
    POSITION_SIZING
}

data class LearnLesson(val id: String, val recommendedForLevel: CareerLevel, val highlightedTerms: List<TradingTerm>)

/**
 * Misi kompetensi untuk membuka tingkatan karier trader berikutnya.
 */
enum class MissionId {
    COMPLETE_BASICS_LESSON,
    EXECUTE_MIN_TRADES,
    DIVERSIFY_ASSETS,
    USE_STOP_LOSS,
    KEEP_DRAWDOWN_LIMIT,
    COMPLETE_RISK_LESSON,
    PASS_CANDLE_DRILLS,
    EXECUTE_LIMIT_OR_STOP_FILLS,
    PRACTICE_DISCIPLINED_RISK_SIZE,
    ACHIEVE_RISK_REWARD,
    COMPLETE_MARGIN_LESSON,
    EXECUTE_SHORT_OR_HEDGE,
    ZERO_LIQUIDATIONS,
    DIVERSIFY_ASSET_CLASSES,
    ACHIEVE_WIN_RATE
}

data class MissionProgress(
    val id: MissionId,
    val targetLevel: CareerLevel,
    val currentValue: Double,
    val targetValue: Double,
    val isInverseThreshold: Boolean = false,
    val isCompleted: Boolean
) {
    val progressFraction: Float
        get() = when {
            isCompleted -> 1f
            isInverseThreshold -> 0f
            targetValue <= 0.0 -> 0f
            else -> (currentValue / targetValue).toFloat().coerceIn(0f, 1f)
        }
}

data class CareerProgressSnapshot(
    val currentLevel: CareerLevel,
    val nextLevel: CareerLevel?,
    val highestCompetencyUnlockedLevel: CareerLevel,
    val missionsForNextLevel: List<MissionProgress>,
    val allMissionsForNextLevelPassed: Boolean,
    val completedMissionsCount: Int,
    val totalMissionsCount: Int
)

/**
 * Akar penyebab otomatis yang terdeteksi pada setiap trade yang ditutup dalam keadaan rugi.
 */
enum class PostMortemCause {
    FORCED_LIQUIDATION,
    OVER_LEVERAGE,
    NO_STOP_LOSS,
    OVERSIZED_POSITION_RISK,
    HIGH_CONCENTRATION,
    HIGH_SLIPPAGE,
    CONTROLLED_STOP_EXIT
}

enum class PostMortemSeverity {
    CRITICAL,
    WARNING,
    CONSTRUCTIVE
}

data class TradePostMortem(
    val fillId: String,
    val symbol: String,
    val tickIndex: Long,
    val lossAmount: Money,
    val lossPctOfInitialCapital: Double,
    val leverageUsed: Int,
    val slippageBps: Long,
    val hadStopLossProtection: Boolean,
    val primaryCause: PostMortemCause,
    val contributingCauses: List<PostMortemCause>,
    val severity: PostMortemSeverity
)

data class PostMortemSummary(
    val totalLosingTrades: Int,
    val unhedgedLossCount: Int,
    val overLeveragedLossCount: Int,
    val disciplinedStopLossCount: Int,
    val latestPostMortem: TradePostMortem?
)

/**
 * Pola pembacaan candlestick untuk latihan interaktif bebas risiko.
 */
enum class CandlePatternType {
    BULLISH_MOMENTUM,
    BEARISH_REJECTION_WICK,
    BULLISH_HAMMER_REVERSAL,
    VOLATILE_INDECISION
}

data class CandleDrillQuestion(
    val drillIndex: Int,
    val candles: List<Candle>,
    val correctPattern: CandlePatternType,
    val options: List<CandlePatternType>
)

data class RiskSizingSimulationResult(
    val accountEquity: Money,
    val targetRiskPct: Double,
    val maxRiskBudget: Money,
    val entryPrice: Money,
    val stopLossPrice: Money,
    val riskPerUnit: Money,
    val recommendedUnits: Long,
    val actualRiskAmount: Money,
    val actualRiskPct: Double,
    val isWithinRule: Boolean
)
