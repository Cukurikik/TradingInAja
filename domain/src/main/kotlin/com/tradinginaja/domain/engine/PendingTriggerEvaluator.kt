package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.core.common.Money
import com.tradinginaja.core.common.MoneyRoundingMode
import com.tradinginaja.domain.model.Candle
import com.tradinginaja.domain.model.MarketQuote
import com.tradinginaja.domain.model.Order
import com.tradinginaja.domain.model.OrderSide
import com.tradinginaja.domain.model.OrderType

internal object PendingTriggerEvaluator {

    sealed interface TriggerOutcome {
        data class Fill(val executionPrice: Money, val slippageBps: Long) : TriggerOutcome
        data object ConvertStopLimitToLimit : TriggerOutcome
    }

    fun evaluate(order: Order, candle: Candle, quote: MarketQuote): TriggerOutcome? = when (order.type) {
        OrderType.LIMIT -> evaluateLimitTrigger(order, candle, quote)
        OrderType.STOP_MARKET -> evaluateStopMarketTrigger(order, candle, quote)
        OrderType.STOP_LIMIT -> evaluateStopLimitTrigger(order, candle, quote)
        OrderType.MARKET -> null
    }

    private fun evaluateLimitTrigger(order: Order, candle: Candle, quote: MarketQuote): TriggerOutcome? {
        val limitPrice = order.limitPrice ?: return null
        val candleOpen = Candle.doublePriceToMoney(candle.open, MoneyRoundingMode.HALF_EVEN)
        val candleHigh = Candle.doublePriceToMoney(candle.high, MoneyRoundingMode.UP)
        val candleLow = Candle.doublePriceToMoney(candle.low, MoneyRoundingMode.DOWN)
        val fillPrice = when (order.side) {
            OrderSide.BUY -> when {
                quote.askPrice <= limitPrice -> quote.askPrice
                candleLow <= limitPrice -> minOf(candleOpen, limitPrice)
                else -> null
            }
            OrderSide.SELL -> when {
                quote.bidPrice >= limitPrice -> quote.bidPrice
                candleHigh >= limitPrice -> maxOf(candleOpen, limitPrice)
                else -> null
            }
        }
        return fillPrice?.let { TriggerOutcome.Fill(it, slippageBps = 0L) }
    }

    private fun evaluateStopMarketTrigger(order: Order, candle: Candle, quote: MarketQuote): TriggerOutcome? {
        val triggerPrice = order.triggerPrice ?: return null
        val candleOpen = Candle.doublePriceToMoney(candle.open, MoneyRoundingMode.HALF_EVEN)
        val candleHigh = Candle.doublePriceToMoney(candle.high, MoneyRoundingMode.UP)
        val candleLow = Candle.doublePriceToMoney(candle.low, MoneyRoundingMode.DOWN)
        val baseSlippage = OrderFormulas.calculateSlippageBps(order.quantity, quote)

        return when (order.side) {
            OrderSide.SELL -> buildStopSellOutcome(triggerPrice, candleOpen, candleLow, quote.bidPrice, baseSlippage)
            OrderSide.BUY -> buildStopBuyOutcome(triggerPrice, candleOpen, candleHigh, quote.askPrice, baseSlippage)
        }
    }

    private fun buildStopSellOutcome(
        triggerPrice: Money,
        candleOpen: Money,
        candleLow: Money,
        bidPrice: Money,
        baseSlippage: Long
    ): TriggerOutcome? {
        val triggered = bidPrice <= triggerPrice || candleLow <= triggerPrice
        if (!triggered) return null
        val isGapDown = candleOpen < triggerPrice
        val rawBase = when {
            isGapDown -> candleOpen
            bidPrice <= triggerPrice -> minOf(bidPrice, triggerPrice)
            else -> triggerPrice
        }
        val totalSlippage = adjustStopSlippage(baseSlippage, isGapDown)
        return TriggerOutcome.Fill(
            executionPrice = OrderFormulas.applySlippage(rawBase, OrderSide.SELL, totalSlippage),
            slippageBps = totalSlippage
        )
    }

    private fun buildStopBuyOutcome(
        triggerPrice: Money,
        candleOpen: Money,
        candleHigh: Money,
        askPrice: Money,
        baseSlippage: Long
    ): TriggerOutcome? {
        val triggered = askPrice >= triggerPrice || candleHigh >= triggerPrice
        if (!triggered) return null
        val isGapUp = candleOpen > triggerPrice
        val rawBase = when {
            isGapUp -> candleOpen
            askPrice >= triggerPrice -> maxOf(askPrice, triggerPrice)
            else -> triggerPrice
        }
        val totalSlippage = adjustStopSlippage(baseSlippage, isGapUp)
        return TriggerOutcome.Fill(
            executionPrice = OrderFormulas.applySlippage(rawBase, OrderSide.BUY, totalSlippage),
            slippageBps = totalSlippage
        )
    }

    private fun adjustStopSlippage(baseSlippage: Long, isGap: Boolean): Long = if (isGap) {
        (baseSlippage + GameBalance.STOP_GAP_EXTRA_SLIPPAGE_BPS).coerceAtMost(GameBalance.MAX_SLIPPAGE_BPS)
    } else {
        baseSlippage
    }

    private fun evaluateStopLimitTrigger(order: Order, candle: Candle, quote: MarketQuote): TriggerOutcome? {
        val triggerPrice = order.triggerPrice
        val limitPrice = order.limitPrice
        return if (triggerPrice == null || limitPrice == null) {
            null
        } else {
            val candleOpen = Candle.doublePriceToMoney(candle.open, MoneyRoundingMode.HALF_EVEN)
            val candleHigh = Candle.doublePriceToMoney(candle.high, MoneyRoundingMode.UP)
            val candleLow = Candle.doublePriceToMoney(candle.low, MoneyRoundingMode.DOWN)
            when (order.side) {
                OrderSide.SELL -> evalStopLimitSell(
                    triggerPrice,
                    limitPrice,
                    candleOpen,
                    candleHigh,
                    candleLow,
                    quote.bidPrice
                )
                OrderSide.BUY -> evalStopLimitBuy(
                    triggerPrice,
                    limitPrice,
                    candleOpen,
                    candleHigh,
                    candleLow,
                    quote.askPrice
                )
            }
        }
    }

    private fun evalStopLimitSell(
        triggerPrice: Money,
        limitPrice: Money,
        candleOpen: Money,
        candleHigh: Money,
        candleLow: Money,
        bidPrice: Money
    ): TriggerOutcome? {
        val triggered = bidPrice <= triggerPrice || candleLow <= triggerPrice
        return when {
            !triggered -> null
            bidPrice >= limitPrice || candleHigh >= limitPrice -> TriggerOutcome.Fill(
                executionPrice = minOf(maxOf(candleOpen, limitPrice), maxOf(triggerPrice, limitPrice)),
                slippageBps = 0L
            )
            else -> TriggerOutcome.ConvertStopLimitToLimit
        }
    }

    private fun evalStopLimitBuy(
        triggerPrice: Money,
        limitPrice: Money,
        candleOpen: Money,
        candleHigh: Money,
        candleLow: Money,
        askPrice: Money
    ): TriggerOutcome? {
        val triggered = askPrice >= triggerPrice || candleHigh >= triggerPrice
        return when {
            !triggered -> null
            askPrice <= limitPrice || candleLow <= limitPrice -> TriggerOutcome.Fill(
                executionPrice = maxOf(minOf(candleOpen, limitPrice), minOf(triggerPrice, limitPrice)),
                slippageBps = 0L
            )
            else -> TriggerOutcome.ConvertStopLimitToLimit
        }
    }
}
