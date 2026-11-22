package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.core.common.Money
import com.tradinginaja.domain.model.AssetExposure
import com.tradinginaja.domain.model.EquityPoint
import com.tradinginaja.domain.model.MarginHealthSnapshot
import com.tradinginaja.domain.model.MarketQuote
import com.tradinginaja.domain.model.OrderSide
import com.tradinginaja.domain.model.PortfolioRiskMetrics
import com.tradinginaja.domain.model.PortfolioState
import com.tradinginaja.domain.model.TradeFill
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

internal object RiskMetricsCalculator {

    private const val PERCENT_MULTIPLIER = 100.0

    fun computeMetrics(
        state: PortfolioState,
        quotesBySymbol: Map<String, MarketQuote>,
        marginHealth: MarginHealthSnapshot,
        liquidationPriceProvider: (String) -> Money?
    ): PortfolioRiskMetrics {
        val (currentDrawdownPct, maxDrawdownPct) = computeDrawdowns(
            curve = state.equityCurve,
            liveEquity = marginHealth.accountEquity
        )
        val tradeStats = computeClosedTradeStats(state.fills)
        val exposures = computeExposures(
            state = state,
            quotesBySymbol = quotesBySymbol,
            accountEquity = marginHealth.accountEquity,
            liquidationPriceProvider = liquidationPriceProvider
        )
        val grossExposurePct = exposures.sumOf { it.exposurePctOfEquity }
        val netExposurePct = exposures.sumOf { exp ->
            if (exp.isShort) -exp.exposurePctOfEquity else exp.exposurePctOfEquity
        }
        val sharpe = computeSimpleSharpe(state.equityCurve)

        return PortfolioRiskMetrics(
            currentDrawdownPct = currentDrawdownPct,
            maxDrawdownPct = maxDrawdownPct,
            winRatePct = tradeStats.winRatePct,
            winCount = tradeStats.wins,
            lossCount = tradeStats.losses,
            riskRewardRatio = tradeStats.riskRewardRatio,
            grossExposurePct = grossExposurePct,
            netExposurePct = netExposurePct,
            simpleSharpeRatio = sharpe,
            exposures = exposures,
            marginHealth = marginHealth
        )
    }

    private fun computeDrawdowns(curve: List<EquityPoint>, liveEquity: Money): Pair<Double, Double> {
        val series = buildList {
            curve.forEach { add(it.equity.minorUnits) }
            if (isEmpty() || last() != liveEquity.minorUnits) {
                add(liveEquity.minorUnits)
            }
        }
        var peak = 0L
        var maxDd = 0.0
        var latestDd = 0.0
        for (equity in series) {
            if (equity > peak) {
                peak = equity
            }
            if (peak > 0L) {
                val dd = ((peak - equity).coerceAtLeast(0L).toDouble() / peak.toDouble()) * PERCENT_MULTIPLIER
                maxDd = max(maxDd, dd)
                latestDd = dd
            }
        }
        return latestDd to maxDd
    }

    private fun computeClosedTradeStats(fills: List<TradeFill>): ClosedTradeStats {
        // Realized PnL pada BUY pembuka long hanya komisi negatif; trade penutup dikenali lewat
        // eksekusi dengan grossNotional berbeda atau side penutup terhadap riwayat posisi berurutan.
        val runningSignedQty = mutableMapOf<String, Long>()
        val closedPnls = mutableListOf<Long>()

        for (fill in fills) {
            val prevQty = runningSignedQty[fill.symbol] ?: 0L
            val delta = if (fill.side == OrderSide.BUY) fill.quantity else -fill.quantity
            val isClosing = (prevQty > 0L && fill.side == OrderSide.SELL) ||
                (prevQty < 0L && fill.side == OrderSide.BUY)
            if (isClosing) {
                closedPnls.add(fill.realizedPnl.minorUnits)
            }
            runningSignedQty[fill.symbol] = prevQty + delta
        }

        val winsList = closedPnls.filter { it > 0L }
        val lossesList = closedPnls.filter { it < 0L }
        val totalDecided = winsList.size + lossesList.size
        val winRatePct = if (totalDecided > 0) {
            (winsList.size.toDouble() / totalDecided.toDouble()) * PERCENT_MULTIPLIER
        } else {
            0.0
        }
        val avgWin = if (winsList.isNotEmpty()) winsList.average() else 0.0
        val avgLoss = if (lossesList.isNotEmpty()) abs(lossesList.average()) else 0.0
        val riskReward = when {
            avgLoss > 0.0 -> avgWin / avgLoss
            avgWin > 0.0 -> GameBalance.MAX_DISPLAY_RISK_REWARD
            else -> 0.0
        }
        return ClosedTradeStats(
            wins = winsList.size,
            losses = lossesList.size,
            winRatePct = winRatePct,
            riskRewardRatio = riskReward
        )
    }

    private fun computeExposures(
        state: PortfolioState,
        quotesBySymbol: Map<String, MarketQuote>,
        accountEquity: Money,
        liquidationPriceProvider: (String) -> Money?
    ): List<AssetExposure> {
        val equityDouble = accountEquity.minorUnits.toDouble()
        return state.positions.values
            .sortedBy { it.symbol }
            .map { pos ->
                val markPrice = quotesBySymbol[pos.symbol]?.midPrice ?: pos.averageEntryPrice
                val notional = pos.marketValue(markPrice)
                val exposurePct = if (equityDouble > 0.0) {
                    (notional.minorUnits.toDouble() / equityDouble) * PERCENT_MULTIPLIER
                } else {
                    0.0
                }
                AssetExposure(
                    symbol = pos.symbol,
                    isShort = pos.isShort,
                    absoluteQuantity = pos.absoluteQuantity,
                    leverage = pos.leverage,
                    notionalValue = notional,
                    marginLocked = pos.marginLocked,
                    exposurePctOfEquity = exposurePct,
                    estLiquidationPrice = liquidationPriceProvider(pos.symbol)
                )
            }
    }

    private fun computeSimpleSharpe(curve: List<EquityPoint>): Double? {
        val returns = if (curve.size < GameBalance.MIN_SHARPE_RETURN_SAMPLES + 1) {
            emptyList()
        } else {
            (1 until curve.size).mapNotNull { index ->
                val prev = curve[index - 1].equity.minorUnits.toDouble()
                val curr = curve[index].equity.minorUnits.toDouble()
                if (prev > 0.0) (curr - prev) / prev else null
            }
        }
        return if (returns.size < GameBalance.MIN_SHARPE_RETURN_SAMPLES) {
            null
        } else {
            val mean = returns.average()
            val variance = returns.sumOf { (it - mean) * (it - mean) } / (returns.size - 1).toDouble()
            val stdDev = sqrt(variance)
            if (stdDev > 0.0) (mean / stdDev) * sqrt(GameBalance.TICKS_PER_TRADING_YEAR) else null
        }
    }

    private data class ClosedTradeStats(
        val wins: Int,
        val losses: Int,
        val winRatePct: Double,
        val riskRewardRatio: Double
    )
}
