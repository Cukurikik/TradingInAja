package com.tradinginaja.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "liquidation_events")
data class LiquidationEventEntity(
    @PrimaryKey val eventId: String,
    val symbol: String,
    val closedSide: String,
    val closedQuantity: Long,
    val liquidationPriceMinorUnits: Long,
    val realizedPnlMinorUnits: Long,
    val marginLevelBeforePct: Double,
    val tickIndex: Long,
    val occurredAtEpochMillis: Long
)
