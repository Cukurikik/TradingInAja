package com.tradinginaja.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trade_fills")
data class TradeFillEntity(
    @PrimaryKey val fillId: String,
    val orderId: String,
    val symbol: String,
    val side: String,
    val orderType: String,
    val quantity: Long,
    val executionPriceMinorUnits: Long,
    val grossNotionalMinorUnits: Long,
    val commissionMinorUnits: Long,
    val slippageBps: Long,
    val realizedPnlMinorUnits: Long,
    val tickIndex: Long,
    val executedAtEpochMillis: Long,
    val isLiquidation: Boolean = false
)
