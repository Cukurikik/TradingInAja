package com.tradinginaja.domain.model

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.core.common.Money

/**
 * Tingkatan karier trader beserta batas leverage dan izin short selling sesuai DESIGN.md.
 */
enum class CareerLevel(val levelNumber: Int, val maxLeverage: Int, val allowsShortSelling: Boolean) {
    LEVEL_1_NOVICE(
        levelNumber = 1,
        maxLeverage = GameBalance.MAX_LEVERAGE_SPOT,
        allowsShortSelling = false
    ),
    LEVEL_2_ACTIVE(
        levelNumber = 2,
        maxLeverage = GameBalance.MAX_LEVERAGE_SPOT,
        allowsShortSelling = false
    ),
    LEVEL_3_PRO(
        levelNumber = 3,
        maxLeverage = GameBalance.MAX_LEVERAGE_PRO,
        allowsShortSelling = true
    ),
    LEVEL_4_FUND_MANAGER(
        levelNumber = 4,
        maxLeverage = GameBalance.MAX_LEVERAGE_FUND,
        allowsShortSelling = true
    ),
    LEVEL_5_HEDGE_FUND(
        levelNumber = 5,
        maxLeverage = GameBalance.MAX_LEVERAGE_FUND,
        allowsShortSelling = true
    );

    companion object {
        fun fromLevelNumber(level: Int): CareerLevel = entries.firstOrNull { it.levelNumber == level } ?: LEVEL_1_NOVICE
    }
}

enum class MarginStatus {
    HEALTHY,
    MARGIN_CALL,
    LIQUIDATION
}

data class LiquidationEvent(
    val eventId: String,
    val symbol: String,
    val closedSide: OrderSide,
    val closedQuantity: Long,
    val liquidationPrice: Money,
    val realizedPnl: Money,
    val marginLevelBeforePct: Double,
    val tickIndex: Long,
    val occurredAtEpochMillis: Long
)

data class AssetExposure(
    val symbol: String,
    val isShort: Boolean,
    val absoluteQuantity: Long,
    val leverage: Int,
    val notionalValue: Money,
    val marginLocked: Money,
    val exposurePctOfEquity: Double,
    val estLiquidationPrice: Money?
)

data class MarginHealthSnapshot(
    val accountEquity: Money,
    val usedLeveragedMargin: Money,
    val totalMarginLocked: Money,
    val marginLevelPct: Double?,
    val status: MarginStatus
)

data class PortfolioRiskMetrics(
    val currentDrawdownPct: Double,
    val maxDrawdownPct: Double,
    val winRatePct: Double,
    val winCount: Int,
    val lossCount: Int,
    val riskRewardRatio: Double,
    val grossExposurePct: Double,
    val netExposurePct: Double,
    val simpleSharpeRatio: Double?,
    val exposures: List<AssetExposure>,
    val marginHealth: MarginHealthSnapshot
)
