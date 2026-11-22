package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.core.common.Money
import com.tradinginaja.domain.model.AssetCategory
import com.tradinginaja.domain.model.CareerLevel
import com.tradinginaja.domain.model.CareerProgressSnapshot
import com.tradinginaja.domain.model.LearnLessonIds
import com.tradinginaja.domain.model.LearningProgress
import com.tradinginaja.domain.model.MissionId
import com.tradinginaja.domain.model.MissionProgress
import com.tradinginaja.domain.model.Order
import com.tradinginaja.domain.model.OrderSide
import com.tradinginaja.domain.model.OrderStatus
import com.tradinginaja.domain.model.OrderType
import com.tradinginaja.domain.model.PortfolioRiskMetrics
import com.tradinginaja.domain.model.PortfolioState
import kotlin.math.abs

/**
 * Evaluator deterministik pure Kotlin untuk progres 5 level karier berbasis kompetensi risiko.
 */
object ProgressionEngine {

    private const val PERCENT_MULTIPLIER = 100.0

    fun evaluateCareerProgress(
        portfolio: PortfolioState,
        categoriesBySymbol: Map<String, AssetCategory>,
        learningProgress: LearningProgress,
        metrics: PortfolioRiskMetrics
    ): CareerProgressSnapshot {
        val stats = collectActivityStats(portfolio, categoriesBySymbol, learningProgress, metrics)
        val missionsByLevel = mapOf(
            CareerLevel.LEVEL_2_ACTIVE to buildLevel2Missions(stats),
            CareerLevel.LEVEL_3_PRO to buildLevel3Missions(stats),
            CareerLevel.LEVEL_4_FUND_MANAGER to buildLevel4Missions(stats),
            CareerLevel.LEVEL_5_HEDGE_FUND to buildLevel5Missions(stats)
        )
        val highestUnlocked = computeHighestUnlockedLevel(missionsByLevel)
        val currentLevel = portfolio.careerLevel
        val nextLevel = CareerLevel.entries.firstOrNull { it.levelNumber == currentLevel.levelNumber + 1 }
        val activeMissions = nextLevel?.let { missionsByLevel[it].orEmpty() }.orEmpty()
        val completedCount = activeMissions.count { it.isCompleted }

        return CareerProgressSnapshot(
            currentLevel = currentLevel,
            nextLevel = nextLevel,
            highestCompetencyUnlockedLevel = highestUnlocked,
            missionsForNextLevel = activeMissions,
            allMissionsForNextLevelPassed = nextLevel != null &&
                activeMissions.isNotEmpty() &&
                completedCount == activeMissions.size,
            completedMissionsCount = completedCount,
            totalMissionsCount = activeMissions.size
        )
    }

    /**
     * Menghitung estimasi % risiko Stop-Loss terhadap ekuitas akun sebelum order dikonfirmasi.
     */
    fun estimateStopLossRiskPct(
        entryPrice: Money,
        stopLossPrice: Money?,
        quantity: Long,
        accountEquity: Money
    ): Double? {
        if (stopLossPrice == null || quantity <= 0L || accountEquity.minorUnits <= 0L) return null
        val distanceMinor = abs(entryPrice.minorUnits - stopLossPrice.minorUnits)
        val totalRiskMinor = distanceMinor.toDouble() * quantity.toDouble()
        return (totalRiskMinor / accountEquity.minorUnits.toDouble()) * PERCENT_MULTIPLIER
    }

    private fun computeHighestUnlockedLevel(missionsByLevel: Map<CareerLevel, List<MissionProgress>>): CareerLevel {
        var unlocked = CareerLevel.LEVEL_1_NOVICE
        for (target in listOf(
            CareerLevel.LEVEL_2_ACTIVE,
            CareerLevel.LEVEL_3_PRO,
            CareerLevel.LEVEL_4_FUND_MANAGER,
            CareerLevel.LEVEL_5_HEDGE_FUND
        )) {
            val required = missionsByLevel[target].orEmpty()
            if (required.isNotEmpty() && required.all { it.isCompleted }) {
                unlocked = target
            } else {
                break
            }
        }
        return unlocked
    }

    private fun buildLevel2Missions(stats: CompetencyStats): List<MissionProgress> = listOf(
        lessonMission(
            id = MissionId.COMPLETE_BASICS_LESSON,
            targetLevel = CareerLevel.LEVEL_2_ACTIVE,
            completed = stats.completedLessons.contains(LearnLessonIds.BASICS_SPREAD_SL)
        ),
        countMission(
            id = MissionId.EXECUTE_MIN_TRADES,
            targetLevel = CareerLevel.LEVEL_2_ACTIVE,
            current = stats.totalFills,
            target = GameBalance.LV1_TARGET_TRADES
        ),
        countMission(
            id = MissionId.DIVERSIFY_ASSETS,
            targetLevel = CareerLevel.LEVEL_2_ACTIVE,
            current = stats.distinctAssetsTraded,
            target = GameBalance.LV1_TARGET_DISTINCT_ASSETS
        ),
        countMission(
            id = MissionId.USE_STOP_LOSS,
            targetLevel = CareerLevel.LEVEL_2_ACTIVE,
            current = stats.stopLossOrdersPlaced,
            target = GameBalance.LV1_TARGET_STOP_LOSSES
        ),
        inverseThresholdMission(
            id = MissionId.KEEP_DRAWDOWN_LIMIT,
            targetLevel = CareerLevel.LEVEL_2_ACTIVE,
            current = stats.maxDrawdownPct,
            maxAllowed = GameBalance.LV1_MAX_DRAWDOWN_PCT
        )
    )

    private fun buildLevel3Missions(stats: CompetencyStats): List<MissionProgress> = listOf(
        lessonMission(
            id = MissionId.COMPLETE_RISK_LESSON,
            targetLevel = CareerLevel.LEVEL_3_PRO,
            completed = stats.completedLessons.contains(LearnLessonIds.RISK_ONE_TWO_PERCENT)
        ),
        countMission(
            id = MissionId.PASS_CANDLE_DRILLS,
            targetLevel = CareerLevel.LEVEL_3_PRO,
            current = stats.candleDrillsPassed,
            target = GameBalance.LV2_TARGET_CANDLE_DRILLS
        ),
        countMission(
            id = MissionId.EXECUTE_MIN_TRADES,
            targetLevel = CareerLevel.LEVEL_3_PRO,
            current = stats.totalFills,
            target = GameBalance.LV2_TARGET_TRADES
        ),
        countMission(
            id = MissionId.EXECUTE_LIMIT_OR_STOP_FILLS,
            targetLevel = CareerLevel.LEVEL_3_PRO,
            current = stats.limitOrStopFills,
            target = GameBalance.LV2_TARGET_LIMIT_STOP_FILLS
        ),
        countMission(
            id = MissionId.PRACTICE_DISCIPLINED_RISK_SIZE,
            targetLevel = CareerLevel.LEVEL_3_PRO,
            current = stats.disciplinedRiskOrders,
            target = GameBalance.LV2_TARGET_DISCIPLINED_RISK_ORDERS
        ),
        MissionProgress(
            id = MissionId.ACHIEVE_RISK_REWARD,
            targetLevel = CareerLevel.LEVEL_3_PRO,
            currentValue = stats.riskRewardRatio,
            targetValue = GameBalance.LV2_MIN_RISK_REWARD,
            isCompleted = stats.hasClosedTrades && stats.riskRewardRatio >= GameBalance.LV2_MIN_RISK_REWARD
        )
    )

    private fun buildLevel4Missions(stats: CompetencyStats): List<MissionProgress> = listOf(
        lessonMission(
            id = MissionId.COMPLETE_MARGIN_LESSON,
            targetLevel = CareerLevel.LEVEL_4_FUND_MANAGER,
            completed = stats.completedLessons.contains(LearnLessonIds.MARGIN_ANTI_LIQUIDATION)
        ),
        countMission(
            id = MissionId.EXECUTE_MIN_TRADES,
            targetLevel = CareerLevel.LEVEL_4_FUND_MANAGER,
            current = stats.totalFills,
            target = GameBalance.LV3_TARGET_TRADES
        ),
        countMission(
            id = MissionId.EXECUTE_SHORT_OR_HEDGE,
            targetLevel = CareerLevel.LEVEL_4_FUND_MANAGER,
            current = stats.shortOrHedgeFills,
            target = GameBalance.LV3_TARGET_SHORT_OR_HEDGE_FILLS
        ),
        inverseThresholdMission(
            id = MissionId.ZERO_LIQUIDATIONS,
            targetLevel = CareerLevel.LEVEL_4_FUND_MANAGER,
            current = stats.liquidationCount.toDouble(),
            maxAllowed = 0.0
        ),
        inverseThresholdMission(
            id = MissionId.KEEP_DRAWDOWN_LIMIT,
            targetLevel = CareerLevel.LEVEL_4_FUND_MANAGER,
            current = stats.maxDrawdownPct,
            maxAllowed = GameBalance.LV3_MAX_DRAWDOWN_PCT
        )
    )

    private fun buildLevel5Missions(stats: CompetencyStats): List<MissionProgress> = listOf(
        countMission(
            id = MissionId.DIVERSIFY_ASSET_CLASSES,
            targetLevel = CareerLevel.LEVEL_5_HEDGE_FUND,
            current = stats.distinctCategoriesTraded,
            target = GameBalance.LV4_TARGET_ASSET_CATEGORIES
        ),
        countMission(
            id = MissionId.PASS_CANDLE_DRILLS,
            targetLevel = CareerLevel.LEVEL_5_HEDGE_FUND,
            current = stats.candleDrillsPassed,
            target = GameBalance.LV4_TARGET_CANDLE_DRILLS
        ),
        MissionProgress(
            id = MissionId.ACHIEVE_WIN_RATE,
            targetLevel = CareerLevel.LEVEL_5_HEDGE_FUND,
            currentValue = stats.winRatePct,
            targetValue = GameBalance.LV4_MIN_WIN_RATE_PCT,
            isCompleted = stats.hasClosedTrades && stats.winRatePct >= GameBalance.LV4_MIN_WIN_RATE_PCT
        ),
        inverseThresholdMission(
            id = MissionId.KEEP_DRAWDOWN_LIMIT,
            targetLevel = CareerLevel.LEVEL_5_HEDGE_FUND,
            current = stats.maxDrawdownPct,
            maxAllowed = GameBalance.LV4_MAX_DRAWDOWN_PCT
        ),
        inverseThresholdMission(
            id = MissionId.ZERO_LIQUIDATIONS,
            targetLevel = CareerLevel.LEVEL_5_HEDGE_FUND,
            current = stats.liquidationCount.toDouble(),
            maxAllowed = 0.0
        )
    )

    private fun collectActivityStats(
        portfolio: PortfolioState,
        categoriesBySymbol: Map<String, AssetCategory>,
        learningProgress: LearningProgress,
        metrics: PortfolioRiskMetrics
    ): CompetencyStats {
        val nonLiqFills = portfolio.fills.filter { !it.isLiquidation }
        val nonRejectedOrders = portfolio.orders.filter { it.status != OrderStatus.REJECTED }
        val stopLossOrders = countStopLossProtectiveOrders(nonRejectedOrders)
        val disciplinedRiskOrders = countDisciplinedRiskOrders(
            orders = nonRejectedOrders,
            fillsByOrderId = portfolio.fills.associateBy { it.orderId },
            accountBaseEquity = portfolio.initialCash
        )
        val distinctCategories = nonLiqFills
            .mapNotNull { categoriesBySymbol[it.symbol] }
            .distinct()
            .size

        return CompetencyStats(
            completedLessons = learningProgress.completedLessonIds,
            candleDrillsPassed = learningProgress.candleDrillsPassed,
            totalFills = nonLiqFills.size,
            distinctAssetsTraded = nonLiqFills.map { it.symbol }.distinct().size,
            distinctCategoriesTraded = distinctCategories,
            stopLossOrdersPlaced = stopLossOrders,
            limitOrStopFills = nonLiqFills.count { it.orderType != OrderType.MARKET },
            disciplinedRiskOrders = disciplinedRiskOrders,
            shortOrHedgeFills = countShortOrHedgeFills(portfolio),
            liquidationCount = portfolio.liquidationEvents.size,
            maxDrawdownPct = metrics.maxDrawdownPct,
            riskRewardRatio = metrics.riskRewardRatio,
            winRatePct = metrics.winRatePct,
            hasClosedTrades = metrics.winCount + metrics.lossCount > 0
        )
    }

    private fun countStopLossProtectiveOrders(orders: List<Order>): Int = orders.count { order ->
        val isExplicitStop = (order.type == OrderType.STOP_MARKET || order.type == OrderType.STOP_LIMIT) &&
            !order.orderId.endsWith("_sl")
        val hasBracketSl = order.stopLossPrice != null
        isExplicitStop || hasBracketSl
    }

    private fun countShortOrHedgeFills(portfolio: PortfolioState): Int {
        val runningSignedQty = mutableMapOf<String, Long>()
        var shortFills = 0
        for (fill in portfolio.fills.sortedWith(compareBy({ it.tickIndex }, { it.executedAtEpochMillis }))) {
            val prev = runningSignedQty[fill.symbol] ?: 0L
            val delta = if (fill.side == OrderSide.BUY) fill.quantity else -fill.quantity
            val next = prev + delta
            if (!fill.isLiquidation && (prev < 0L || next < 0L)) {
                shortFills += 1
            }
            runningSignedQty[fill.symbol] = next
        }
        return shortFills
    }
}

private const val MIN_RISK_PCT_PRECISION = 100.0

private fun countDisciplinedRiskOrders(
    orders: List<Order>,
    fillsByOrderId: Map<String, com.tradinginaja.domain.model.TradeFill>,
    accountBaseEquity: Money
): Int {
    if (accountBaseEquity.minorUnits <= 0L) return 0
    return orders.count { order ->
        val slPrice = order.stopLossPrice ?: return@count false
        val entryRef = order.limitPrice ?: fillsByOrderId[order.orderId]?.executionPrice ?: return@count false
        val riskMinor = abs(entryRef.minorUnits - slPrice.minorUnits).toDouble() * order.quantity.toDouble()
        val riskPct = (riskMinor / accountBaseEquity.minorUnits.toDouble()) * MIN_RISK_PCT_PRECISION
        riskPct > 0.0 && riskPct <= GameBalance.MAX_DISCIPLINED_RISK_PER_TRADE_PCT
    }
}

private fun lessonMission(id: MissionId, targetLevel: CareerLevel, completed: Boolean): MissionProgress =
    MissionProgress(
        id = id,
        targetLevel = targetLevel,
        currentValue = if (completed) 1.0 else 0.0,
        targetValue = 1.0,
        isCompleted = completed
    )

private fun countMission(id: MissionId, targetLevel: CareerLevel, current: Int, target: Int): MissionProgress =
    MissionProgress(
        id = id,
        targetLevel = targetLevel,
        currentValue = current.toDouble(),
        targetValue = target.toDouble(),
        isCompleted = current >= target
    )

private fun inverseThresholdMission(
    id: MissionId,
    targetLevel: CareerLevel,
    current: Double,
    maxAllowed: Double
): MissionProgress = MissionProgress(
    id = id,
    targetLevel = targetLevel,
    currentValue = current,
    targetValue = maxAllowed,
    isInverseThreshold = true,
    isCompleted = current <= maxAllowed
)

private data class CompetencyStats(
    val completedLessons: Set<String>,
    val candleDrillsPassed: Int,
    val totalFills: Int,
    val distinctAssetsTraded: Int,
    val distinctCategoriesTraded: Int,
    val stopLossOrdersPlaced: Int,
    val limitOrStopFills: Int,
    val disciplinedRiskOrders: Int,
    val shortOrHedgeFills: Int,
    val liquidationCount: Int,
    val maxDrawdownPct: Double,
    val riskRewardRatio: Double,
    val winRatePct: Double,
    val hasClosedTrades: Boolean
)
