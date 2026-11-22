package com.tradinginaja.domain.model

import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.core.common.Money
import com.tradinginaja.core.common.MoneyRoundingMode
import kotlin.math.abs

enum class OrderSide {
    BUY,
    SELL
}

enum class OrderType {
    MARKET,
    LIMIT,
    STOP_MARKET,
    STOP_LIMIT
}

enum class OrderStatus {
    PENDING,
    FILLED,
    CANCELLED,
    REJECTED
}

enum class OrderRejectReason {
    INVALID_QUANTITY,
    INVALID_LIMIT_PRICE,
    INVALID_TRIGGER_PRICE,
    INVALID_LEVERAGE,
    LEVERAGE_EXCEEDS_CAREER_LIMIT,
    SHORT_NOT_ALLOWED_FOR_LEVEL,
    INVALID_OCO_BRACKET,
    INSUFFICIENT_CASH,
    INSUFFICIENT_POSITION,
    UNKNOWN_ASSET
}

/**
 * Kutipan pasar diskrit berbasis [Money] untuk kebutuhan tiket order dan eksekusi.
 */
data class MarketQuote(
    val symbol: String,
    val midPrice: Money,
    val bidPrice: Money,
    val askPrice: Money,
    val spreadBps: Long,
    val referenceTickVolume: Long,
    val liquidityTier: LiquidityTier
)

data class OrderRequest(
    val orderId: String,
    val symbol: String,
    val side: OrderSide,
    val type: OrderType,
    val quantity: Long,
    val limitPrice: Money? = null,
    val triggerPrice: Money? = null,
    val leverage: Int = 1,
    val takeProfitPrice: Money? = null,
    val stopLossPrice: Money? = null,
    val ocoGroupId: String? = null,
    val createdAtEpochMillis: Long = 0L
)

/**
 * Ringkasan biaya, slippage, margin, harga likuidasi, dan risiko portofolio SEBELUM konfirmasi order.
 */
data class OrderPreview(
    val symbol: String,
    val side: OrderSide,
    val type: OrderType,
    val quantity: Long,
    val leverage: Int,
    val isShortOpening: Boolean,
    val referencePrice: Money,
    val estimatedExecutionPrice: Money,
    val spreadBps: Long,
    val estimatedSlippageBps: Long,
    val estimatedSlippageCost: Money,
    val estimatedNotional: Money,
    val requiredMargin: Money,
    val estimatedBorrowFeePerDay: Money,
    val estimatedLiquidationPrice: Money?,
    val estimatedCommission: Money,
    val estimatedNetCashImpact: Money,
    val projectedCashAfterOrder: Money,
    val projectedAssetConcentrationPct: Double,
    val isHighConcentrationWarning: Boolean,
    val isHighSlippageWarning: Boolean,
    val isMarginLiquidationRiskWarning: Boolean,
    val estimatedRealizedPnl: Money?,
    val rejectReason: OrderRejectReason?
) {
    val isValid: Boolean get() = rejectReason == null
}

data class Order(
    val orderId: String,
    val symbol: String,
    val side: OrderSide,
    val type: OrderType,
    val quantity: Long,
    val limitPrice: Money?,
    val triggerPrice: Money? = null,
    val leverage: Int = 1,
    val takeProfitPrice: Money? = null,
    val stopLossPrice: Money? = null,
    val ocoGroupId: String? = null,
    val reservedCash: Money,
    val status: OrderStatus,
    val createdAtTick: Long,
    val createdAtEpochMillis: Long,
    val updatedAtTick: Long,
    val rejectReason: OrderRejectReason? = null
)

data class TradeFill(
    val fillId: String,
    val orderId: String,
    val symbol: String,
    val side: OrderSide,
    val orderType: OrderType,
    val quantity: Long,
    val executionPrice: Money,
    val grossNotional: Money,
    val commission: Money,
    val slippageBps: Long,
    val realizedPnl: Money,
    val tickIndex: Long,
    val executedAtEpochMillis: Long,
    val isLiquidation: Boolean = false
)

/**
 * Posisi terbuka Long (`signedQuantity > 0`) atau Short (`signedQuantity < 0`).
 * [costBasis] menyimpan nilai masuk absolut (> 0), dan [marginLocked] menyimpan kolateral awal yang dikunci.
 */
data class Position(
    val symbol: String,
    val signedQuantity: Long,
    val costBasis: Money,
    val marginLocked: Money = costBasis,
    val leverage: Int = 1,
    val accumulatedBorrowFee: Money = Money.ZERO
) {
    val quantity: Long get() = signedQuantity
    val absoluteQuantity: Long get() = abs(signedQuantity)
    val isShort: Boolean get() = signedQuantity < 0L
    val isLong: Boolean get() = signedQuantity > 0L

    val averageEntryPrice: Money
        get() = if (absoluteQuantity > 0L) {
            costBasis.divideBy(absoluteQuantity, MoneyRoundingMode.HALF_EVEN)
        } else {
            Money.ZERO
        }

    fun marketValue(markPrice: Money): Money = markPrice * absoluteQuantity

    fun unrealizedPnl(markPrice: Money): Money = if (isShort) {
        costBasis - marketValue(markPrice)
    } else {
        marketValue(markPrice) - costBasis
    }

    fun netEquityContribution(markPrice: Money): Money = marginLocked + unrealizedPnl(markPrice)
}

data class EquityPoint(
    val tickIndex: Long,
    val equity: Money,
    val cash: Money,
    val positionsValue: Money,
    val recordedAtEpochMillis: Long
)

data class PortfolioState(
    val initialCash: Money = Money(GameBalance.INITIAL_VIRTUAL_CASH_MINOR),
    val cashBalance: Money = Money(GameBalance.INITIAL_VIRTUAL_CASH_MINOR),
    val careerLevel: CareerLevel = CareerLevel.LEVEL_1_NOVICE,
    val positions: Map<String, Position> = emptyMap(),
    val orders: List<Order> = emptyList(),
    val fills: List<TradeFill> = emptyList(),
    val equityCurve: List<EquityPoint> = emptyList(),
    val liquidationEvents: List<LiquidationEvent> = emptyList(),
    val totalBorrowFeesPaid: Money = Money.ZERO,
    val insolvencyCoverage: Money = Money.ZERO,
    val processedOrderIds: Set<String> = emptySet()
) {
    val reservedCashForPendingBuys: Money
        get() = orders
            .asSequence()
            .filter { it.status == OrderStatus.PENDING && it.reservedCash.isPositive }
            .groupBy { it.ocoGroupId ?: it.orderId }
            .values
            .fold(Money.ZERO) { acc, group ->
                acc + (group.maxOfOrNull { it.reservedCash } ?: Money.ZERO)
            }

    val availableCash: Money
        get() = (cashBalance - reservedCashForPendingBuys).coerceAtLeast(Money.ZERO)

    val totalRealizedPnl: Money
        get() = fills.fold(Money.ZERO) { acc, fill -> acc + fill.realizedPnl }

    val totalCommissionsPaid: Money
        get() = fills.fold(Money.ZERO) { acc, fill -> acc + fill.commission }

    fun reservedQuantityForPendingSells(symbol: String): Long = reservedClosingQuantity(symbol, OrderSide.SELL)

    fun reservedClosingQuantity(symbol: String, closingSide: OrderSide): Long = orders
        .asSequence()
        .filter { it.status == OrderStatus.PENDING && it.side == closingSide && it.symbol == symbol }
        .groupBy { it.ocoGroupId ?: it.orderId }
        .values
        .sumOf { group -> group.maxOfOrNull { it.quantity } ?: 0L }
}

sealed interface OrderSubmissionResult {
    data class Executed(val order: Order, val fill: TradeFill) : OrderSubmissionResult
    data class PlacedPending(val order: Order) : OrderSubmissionResult
    data class Rejected(val order: Order, val reason: OrderRejectReason) : OrderSubmissionResult
    data class DuplicateIgnored(val existingOrder: Order) : OrderSubmissionResult
}
