package com.tradinginaja.simulation.engine

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.domain.model.AssetSpec
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Portofolio kertas long-only dengan akuntansi minor units (Long), mengikuti aturan biaya produksi:
 * komisi [GameBalance.DEFAULT_TRADING_FEE_BPS], biaya minimum [GameBalance.MIN_TRADING_FEE_MINOR],
 * dan eksekusi pada sisi bid/ask sesuai [com.tradinginaja.domain.model.LiquidityTier.baseSpreadBps].
 *
 * Sengaja tidak memodelkan leverage/short karena pertanyaan balancing Fase 8 adalah apakah
 * buy-and-hold spot mendominasi strategi aktif spot biasa.
 */
class PaperPortfolio(val specs: List<AssetSpec>, initialCashMinor: Long) {

    var cashMinor: Long = initialCashMinor
        private set

    val quantities = LongArray(specs.size)
    val entryPriceMinor = LongArray(specs.size)

    var feesPaidMinor: Long = 0L
        private set
    var tradeCount: Int = 0
        private set
    var maxDrawdownPct: Double = 0.0
        private set
    var peakEquityMinor: Long = initialCashMinor
        private set

    val openPositionCount: Int
        get() = quantities.count { it > 0L }

    fun equityMinor(midPricesMinor: LongArray): Long {
        var holdings = 0L
        for (index in quantities.indices) {
            if (quantities[index] > 0L) {
                holdings += quantities[index] * bidPriceMinor(index, midPricesMinor[index])
            }
        }
        return cashMinor + holdings
    }

    fun markToMarket(midPricesMinor: LongArray) {
        val equity = equityMinor(midPricesMinor)
        if (equity > peakEquityMinor) peakEquityMinor = equity
        if (peakEquityMinor > 0L) {
            val drawdownPct = (peakEquityMinor - equity).toDouble() / peakEquityMinor.toDouble() * PERCENT
            if (drawdownPct > maxDrawdownPct) maxDrawdownPct = drawdownPct
        }
    }

    fun buy(index: Int, desiredNotionalMinor: Long, midPriceMinor: Long): Boolean {
        val ask = askPriceMinor(index, midPriceMinor)
        var filled = false
        if (ask > 0L && desiredNotionalMinor > 0L) {
            val quantity = minOf(desiredNotionalMinor / ask, affordableQuantity(ask))
            if (quantity > 0L) {
                val notional = quantity * ask
                val fee = commission(notional)
                if (notional + fee <= cashMinor) {
                    cashMinor -= notional + fee
                    feesPaidMinor += fee
                    tradeCount += 1
                    val existing = quantities[index]
                    entryPriceMinor[index] = if (existing <= 0L) {
                        ask
                    } else {
                        (entryPriceMinor[index] * existing + notional) / (existing + quantity)
                    }
                    quantities[index] = existing + quantity
                    filled = true
                }
            }
        }
        return filled
    }

    fun sellAll(index: Int, midPriceMinor: Long): Boolean {
        val quantity = quantities[index]
        if (quantity <= 0L) return false
        val bid = bidPriceMinor(index, midPriceMinor)
        val proceeds = quantity * bid
        val fee = commission(proceeds)
        cashMinor += proceeds - fee
        feesPaidMinor += fee
        tradeCount += 1
        quantities[index] = 0L
        entryPriceMinor[index] = 0L
        return true
    }

    private fun affordableQuantity(askPriceMinor: Long): Long {
        if (askPriceMinor <= 0L) return 0L
        var quantity = cashMinor / askPriceMinor
        while (quantity > 0L && quantity * askPriceMinor + commission(quantity * askPriceMinor) > cashMinor) {
            quantity -= 1L
        }
        return quantity
    }

    private fun bidPriceMinor(index: Int, midPriceMinor: Long): Long {
        val spread = specs[index].liquidityTier.baseSpreadBps
        return max((midPriceMinor * (HALF_SPREAD_DENOMINATOR - spread) / HALF_SPREAD_DENOMINATOR), MIN_PRICE_MINOR)
    }

    private fun askPriceMinor(index: Int, midPriceMinor: Long): Long {
        val spread = specs[index].liquidityTier.baseSpreadBps
        return max((midPriceMinor * (HALF_SPREAD_DENOMINATOR + spread) / HALF_SPREAD_DENOMINATOR), MIN_PRICE_MINOR)
    }

    private fun commission(notionalMinor: Long): Long = max(
        (notionalMinor * GameBalance.DEFAULT_TRADING_FEE_BPS) / GameBalance.BASIS_POINTS_DIVISOR,
        GameBalance.MIN_TRADING_FEE_MINOR
    )

    companion object {
        private const val PERCENT = 100.0
        private const val HALF_SPREAD_DENOMINATOR = 20_000L
        private const val MIN_PRICE_MINOR = 1L

        /** Konversi harga pasar Double ke minor units dengan aturan pembulatan tunggal. */
        fun priceToMinor(price: Double): Long = max(
            (price * GameBalance.MINOR_UNITS_PER_MAJOR).roundToLong(),
            GameBalance.MIN_ASSET_PRICE_MINOR
        )
    }
}
