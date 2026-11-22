package com.tradinginaja.simulation.engine

import com.tradinginaja.domain.model.AssetSpec

/**
 * Konteks satu tick simulasi yang diberikan ke setiap strategi.
 */
class TickContext(
    val tick: Int,
    val specs: List<AssetSpec>,
    val history: PriceHistory,
    val midPricesMinor: LongArray,
    val portfolio: PaperPortfolio
) {
    fun close(index: Int): Double = history.last(index)

    fun equityMinor(): Long = portfolio.equityMinor(midPricesMinor)
}

/**
 * Kontrak strategi yang diuji pada simulasi headless. Setiap strategi hanya boleh berdagang lewat
 * [PaperPortfolio] agar biaya, spread, dan pencatatan trade identik antar strategi.
 */
interface TradingStrategy {
    val id: String
    val label: String

    fun onTick(context: TickContext)
}

/** Beli sekali di awal secara equal-weight lalu tahan sampai akhir run. */
class BuyAndHoldStrategy(private val allocationRatio: Double = 0.98) : TradingStrategy {
    override val id = "buy_and_hold"
    override val label = "Buy & Hold (equal-weight)"

    private var invested = false

    override fun onTick(context: TickContext) {
        if (invested) return
        invested = true
        val count = context.specs.size
        val budgetPerAsset = (context.equityMinor() * allocationRatio / count).toLong()
        for (index in context.specs.indices) {
            context.portfolio.buy(index, budgetPerAsset, context.midPricesMinor[index])
        }
    }
}

/**
 * Trend following SMA 20/60 dengan Stop-Loss 6% dan maksimum 5 posisi.
 * Keputusan diambil tiap 4 tick (1 jam game) agar realistis untuk pemain, bukan HFT.
 */
class MomentumTrendStrategy : StrategyBase() {
    override val id = "momentum_trend"
    override val label = "Trend Following SMA20/60 + SL 6%"

    override fun onTick(context: TickContext) {
        enforceStopLoss(context, STOP_LOSS_PCT)
        if (context.tick % DECISION_INTERVAL_TICKS != 0) return

        for (index in context.specs.indices) {
            val fast = context.history.sma(index, FAST_WINDOW)
            val slow = context.history.sma(index, SLOW_WINDOW)
            if (fast != null && slow != null) {
                val holding = context.portfolio.quantities[index] > 0L
                if (holding) {
                    if (fast < slow) context.portfolio.sellAll(index, context.midPricesMinor[index])
                } else if (
                    context.portfolio.openPositionCount < MAX_POSITIONS &&
                    fast > slow * (1.0 + MIN_TREND_EDGE)
                ) {
                    val budget = (context.equityMinor() * POSITION_RATIO).toLong()
                    context.portfolio.buy(index, budget, context.midPricesMinor[index])
                }
            }
        }
    }

    private companion object {
        const val DECISION_INTERVAL_TICKS = 8
        const val FAST_WINDOW = 20
        const val SLOW_WINDOW = 60
        const val MAX_POSITIONS = 4
        const val POSITION_RATIO = 0.22
        const val MIN_TREND_EDGE = 0.004
        const val STOP_LOSS_PCT = 0.06
    }
}

/**
 * Mean reversion 20 tick: beli saat harga <= mean - 1,5 sigma, keluar saat kembali ke mean.
 * Stop-Loss 6% dan maksimum 5 posisi. Keputusan tiap 4 tick.
 */
class MeanReversionStrategy : StrategyBase() {
    override val id = "mean_reversion"
    override val label = "Mean Reversion 20t (2σ) + SL 6%"

    private val lastExitTick = IntArray(MAX_SYMBOLS) { -COOLDOWN_TICKS }

    override fun onTick(context: TickContext) {
        closeStoppedPositions(context)
        if (context.tick % DECISION_INTERVAL_TICKS != 0) return

        for (index in context.specs.indices) {
            val mean = context.history.sma(index, MEAN_WINDOW)
            val sigma = if (mean == null) null else context.history.stdDev(index, MEAN_WINDOW, mean)
            if (mean != null && sigma != null) {
                val holding = context.portfolio.quantities[index] > 0L
                val price = context.close(index)
                if (holding) {
                    if (price >= mean && context.portfolio.sellAll(index, context.midPricesMinor[index])) {
                        lastExitTick[index] = context.tick
                    }
                } else if (
                    context.portfolio.openPositionCount < MAX_POSITIONS &&
                    context.tick - lastExitTick[index] >= COOLDOWN_TICKS &&
                    price <= mean - ENTRY_SIGMA * sigma
                ) {
                    val budget = (context.equityMinor() * POSITION_RATIO).toLong()
                    context.portfolio.buy(index, budget, context.midPricesMinor[index])
                }
            }
        }
    }

    private fun closeStoppedPositions(context: TickContext) {
        for (index in context.specs.indices) {
            val entry = context.portfolio.entryPriceMinor[index]
            if (context.portfolio.quantities[index] <= 0L || entry <= 0L) continue
            if (context.midPricesMinor[index] <= entry * (1.0 - STOP_LOSS_PCT)) {
                if (context.portfolio.sellAll(index, context.midPricesMinor[index])) {
                    lastExitTick[index] = context.tick
                }
            }
        }
    }

    private companion object {
        const val DECISION_INTERVAL_TICKS = 8
        const val MEAN_WINDOW = 20
        const val MAX_POSITIONS = 4
        const val POSITION_RATIO = 0.22
        const val ENTRY_SIGMA = 2.0
        const val COOLDOWN_TICKS = 24
        const val STOP_LOSS_PCT = 0.06
        const val MAX_SYMBOLS = 32
    }
}

/** Helper Stop-Loss berbasis harga tengah agar kedua strategi aktif memakai aturan identik. */
abstract class StrategyBase : TradingStrategy {
    protected fun enforceStopLoss(context: TickContext, stopLossPct: Double) {
        for (index in context.specs.indices) {
            val entry = context.portfolio.entryPriceMinor[index]
            if (context.portfolio.quantities[index] <= 0L || entry <= 0L) continue
            if (context.midPricesMinor[index] <= entry * (1.0 - stopLossPct)) {
                context.portfolio.sellAll(index, context.midPricesMinor[index])
            }
        }
    }
}

fun defaultStrategies(): List<TradingStrategy> = listOf(
    BuyAndHoldStrategy(),
    MomentumTrendStrategy(),
    MeanReversionStrategy()
)
