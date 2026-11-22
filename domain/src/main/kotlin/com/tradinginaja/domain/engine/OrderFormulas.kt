package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.core.common.Money
import com.tradinginaja.core.common.MoneyRoundingMode
import com.tradinginaja.domain.model.Candle
import com.tradinginaja.domain.model.LiquidityTier
import com.tradinginaja.domain.model.MarketQuote
import com.tradinginaja.domain.model.OrderPreview
import com.tradinginaja.domain.model.OrderRejectReason
import com.tradinginaja.domain.model.OrderRequest
import com.tradinginaja.domain.model.OrderSide
import com.tradinginaja.domain.model.OrderType
import com.tradinginaja.domain.model.PortfolioState
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Formula kuantitatif deterministik berbasis [Money] untuk kutipan spread,
 * slippage berbasis ukuran order & likuiditas, komisi, margin, dan pratinjau risiko.
 */
object OrderFormulas {

    fun quoteFromAssetState(assetState: MarketAssetState): MarketQuote {
        val midMoney = Candle.doublePriceToMoney(assetState.currentPrice, MoneyRoundingMode.HALF_EVEN)
        val (bidDouble, askDouble) = MarketEngine.bidAskFor(assetState)
        val bidMoney = Candle.doublePriceToMoney(bidDouble, MoneyRoundingMode.DOWN)
        val rawAskMoney = Candle.doublePriceToMoney(askDouble, MoneyRoundingMode.UP)
        val askMoney = if (rawAskMoney >= bidMoney) rawAskMoney else bidMoney
        val refVolume = max(
            1L,
            (assetState.spec.baseVolumePerTick.toDouble() * assetState.spec.liquidityTier.baseVolumeMultiplier)
                .roundToLong()
        )
        return MarketQuote(
            symbol = assetState.spec.symbol,
            midPrice = midMoney,
            bidPrice = bidMoney,
            askPrice = askMoney,
            spreadBps = assetState.spec.liquidityTier.baseSpreadBps,
            referenceTickVolume = refVolume,
            liquidityTier = assetState.spec.liquidityTier
        )
    }

    fun calculateSlippageBps(quantity: Long, quote: MarketQuote): Long {
        if (quantity <= 0L) return 0L
        val baseBps = when (quote.liquidityTier) {
            LiquidityTier.VERY_HIGH -> 1L
            LiquidityTier.HIGH -> 2L
            LiquidityTier.MEDIUM -> 4L
            LiquidityTier.LOW_MEDIUM -> 8L
        }
        val tierFactor = when (quote.liquidityTier) {
            LiquidityTier.VERY_HIGH -> 0.5
            LiquidityTier.HIGH -> 1.0
            LiquidityTier.MEDIUM -> 1.8
            LiquidityTier.LOW_MEDIUM -> 3.0
        }
        val participationRatio = quantity.toDouble() / max(1L, quote.referenceTickVolume).toDouble()
        val sizeImpact = (participationRatio * GameBalance.SLIPPAGE_SIZE_IMPACT_FACTOR_BPS * tierFactor).roundToLong()
        return (baseBps + sizeImpact).coerceIn(0L, GameBalance.MAX_SLIPPAGE_BPS)
    }

    fun applySlippage(referencePrice: Money, side: OrderSide, slippageBps: Long): Money {
        val delta = referencePrice.multiplyByBps(slippageBps, MoneyRoundingMode.UP)
        return when (side) {
            OrderSide.BUY -> referencePrice + delta
            OrderSide.SELL -> {
                val raw = referencePrice - delta
                if (raw.minorUnits >= GameBalance.MIN_ASSET_PRICE_MINOR) {
                    raw
                } else {
                    Money(GameBalance.MIN_ASSET_PRICE_MINOR)
                }
            }
        }
    }

    fun calculateCommission(grossNotional: Money, feeBps: Long = GameBalance.DEFAULT_TRADING_FEE_BPS): Money =
        if (grossNotional <= Money.ZERO) {
            Money.ZERO
        } else {
            val computed = grossNotional.multiplyByBps(feeBps, MoneyRoundingMode.HALF_UP)
            if (computed.minorUnits >= GameBalance.MIN_TRADING_FEE_MINOR) {
                computed
            } else {
                Money(GameBalance.MIN_TRADING_FEE_MINOR)
            }
        }

    fun validateRequest(portfolio: PortfolioState, request: OrderRequest, quote: MarketQuote): OrderRejectReason? =
        OrderValidation.validateRequest(portfolio, request, quote)

    fun referenceExecutionPriceForMargin(request: OrderRequest, quote: MarketQuote): Money = when (request.type) {
        OrderType.MARKET -> applySlippage(
            referencePrice = if (request.side == OrderSide.BUY) quote.askPrice else quote.bidPrice,
            side = request.side,
            slippageBps = calculateSlippageBps(request.quantity, quote)
        )
        OrderType.LIMIT -> request.limitPrice ?: quote.midPrice
        OrderType.STOP_MARKET -> applySlippage(
            referencePrice = request.triggerPrice ?: quote.midPrice,
            side = request.side,
            slippageBps = calculateSlippageBps(request.quantity, quote)
        )
        OrderType.STOP_LIMIT -> request.limitPrice ?: request.triggerPrice ?: quote.midPrice
    }

    fun previewOrder(
        portfolio: PortfolioState,
        request: OrderRequest,
        quote: MarketQuote,
        quotesBySymbol: Map<String, MarketQuote>
    ): OrderPreview = OrderPreviewCalculator.preview(portfolio, request, quote, quotesBySymbol)
}
