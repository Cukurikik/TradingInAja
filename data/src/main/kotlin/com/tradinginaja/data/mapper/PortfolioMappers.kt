package com.tradinginaja.data.mapper

import com.tradinginaja.core.common.Money
import com.tradinginaja.core.database.entity.EquitySnapshotEntity
import com.tradinginaja.core.database.entity.LiquidationEventEntity
import com.tradinginaja.core.database.entity.OrderEntity
import com.tradinginaja.core.database.entity.PositionEntity
import com.tradinginaja.core.database.entity.TradeFillEntity
import com.tradinginaja.domain.model.EquityPoint
import com.tradinginaja.domain.model.LiquidationEvent
import com.tradinginaja.domain.model.Order
import com.tradinginaja.domain.model.OrderRejectReason
import com.tradinginaja.domain.model.OrderSide
import com.tradinginaja.domain.model.OrderStatus
import com.tradinginaja.domain.model.OrderType
import com.tradinginaja.domain.model.Position
import com.tradinginaja.domain.model.TradeFill

fun PositionEntity.toDomain(): Position = Position(
    symbol = symbol,
    signedQuantity = quantity,
    costBasis = Money(costBasisMinorUnits),
    marginLocked = Money(marginLockedMinorUnits),
    leverage = leverage,
    accumulatedBorrowFee = Money(accumulatedBorrowFeeMinorUnits)
)

fun Position.toEntity(): PositionEntity = PositionEntity(
    symbol = symbol,
    quantity = signedQuantity,
    costBasisMinorUnits = costBasis.minorUnits,
    marginLockedMinorUnits = marginLocked.minorUnits,
    leverage = leverage,
    accumulatedBorrowFeeMinorUnits = accumulatedBorrowFee.minorUnits
)

fun OrderEntity.toDomain(): Order = Order(
    orderId = orderId,
    symbol = symbol,
    side = OrderSide.valueOf(side),
    type = OrderType.valueOf(type),
    quantity = quantity,
    limitPrice = limitPriceMinorUnits?.let { Money(it) },
    triggerPrice = triggerPriceMinorUnits?.let { Money(it) },
    leverage = leverage,
    takeProfitPrice = takeProfitPriceMinorUnits?.let { Money(it) },
    stopLossPrice = stopLossPriceMinorUnits?.let { Money(it) },
    ocoGroupId = ocoGroupId,
    reservedCash = Money(reservedCashMinorUnits),
    status = OrderStatus.valueOf(status),
    createdAtTick = createdAtTick,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtTick = updatedAtTick,
    rejectReason = rejectReason?.let { OrderRejectReason.valueOf(it) }
)

fun Order.toEntity(): OrderEntity = OrderEntity(
    orderId = orderId,
    symbol = symbol,
    side = side.name,
    type = type.name,
    quantity = quantity,
    limitPriceMinorUnits = limitPrice?.minorUnits,
    triggerPriceMinorUnits = triggerPrice?.minorUnits,
    leverage = leverage,
    takeProfitPriceMinorUnits = takeProfitPrice?.minorUnits,
    stopLossPriceMinorUnits = stopLossPrice?.minorUnits,
    ocoGroupId = ocoGroupId,
    reservedCashMinorUnits = reservedCash.minorUnits,
    status = status.name,
    createdAtTick = createdAtTick,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtTick = updatedAtTick,
    rejectReason = rejectReason?.name
)

fun TradeFillEntity.toDomain(): TradeFill = TradeFill(
    fillId = fillId,
    orderId = orderId,
    symbol = symbol,
    side = OrderSide.valueOf(side),
    orderType = OrderType.valueOf(orderType),
    quantity = quantity,
    executionPrice = Money(executionPriceMinorUnits),
    grossNotional = Money(grossNotionalMinorUnits),
    commission = Money(commissionMinorUnits),
    slippageBps = slippageBps,
    realizedPnl = Money(realizedPnlMinorUnits),
    tickIndex = tickIndex,
    executedAtEpochMillis = executedAtEpochMillis,
    isLiquidation = isLiquidation
)

fun TradeFill.toEntity(): TradeFillEntity = TradeFillEntity(
    fillId = fillId,
    orderId = orderId,
    symbol = symbol,
    side = side.name,
    orderType = orderType.name,
    quantity = quantity,
    executionPriceMinorUnits = executionPrice.minorUnits,
    grossNotionalMinorUnits = grossNotional.minorUnits,
    commissionMinorUnits = commission.minorUnits,
    slippageBps = slippageBps,
    realizedPnlMinorUnits = realizedPnl.minorUnits,
    tickIndex = tickIndex,
    executedAtEpochMillis = executedAtEpochMillis,
    isLiquidation = isLiquidation
)

fun EquitySnapshotEntity.toDomain(): EquityPoint = EquityPoint(
    tickIndex = tickIndex,
    equity = Money(equityMinorUnits),
    cash = Money(cashMinorUnits),
    positionsValue = Money(positionsValueMinorUnits),
    recordedAtEpochMillis = recordedAtEpochMillis
)

fun EquityPoint.toEntity(): EquitySnapshotEntity = EquitySnapshotEntity(
    tickIndex = tickIndex,
    equityMinorUnits = equity.minorUnits,
    cashMinorUnits = cash.minorUnits,
    positionsValueMinorUnits = positionsValue.minorUnits,
    recordedAtEpochMillis = recordedAtEpochMillis
)

fun LiquidationEventEntity.toDomain(): LiquidationEvent = LiquidationEvent(
    eventId = eventId,
    symbol = symbol,
    closedSide = OrderSide.valueOf(closedSide),
    closedQuantity = closedQuantity,
    liquidationPrice = Money(liquidationPriceMinorUnits),
    realizedPnl = Money(realizedPnlMinorUnits),
    marginLevelBeforePct = marginLevelBeforePct,
    tickIndex = tickIndex,
    occurredAtEpochMillis = occurredAtEpochMillis
)

fun LiquidationEvent.toEntity(): LiquidationEventEntity = LiquidationEventEntity(
    eventId = eventId,
    symbol = symbol,
    closedSide = closedSide.name,
    closedQuantity = closedQuantity,
    liquidationPriceMinorUnits = liquidationPrice.minorUnits,
    realizedPnlMinorUnits = realizedPnl.minorUnits,
    marginLevelBeforePct = marginLevelBeforePct,
    tickIndex = tickIndex,
    occurredAtEpochMillis = occurredAtEpochMillis
)
