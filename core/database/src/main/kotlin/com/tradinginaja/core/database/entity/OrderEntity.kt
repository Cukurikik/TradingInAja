package com.tradinginaja.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val orderId: String,
    val symbol: String,
    val side: String,
    val type: String,
    val quantity: Long,
    val limitPriceMinorUnits: Long?,
    val triggerPriceMinorUnits: Long? = null,
    val leverage: Int = 1,
    val takeProfitPriceMinorUnits: Long? = null,
    val stopLossPriceMinorUnits: Long? = null,
    val ocoGroupId: String? = null,
    val reservedCashMinorUnits: Long,
    val status: String,
    val createdAtTick: Long,
    val createdAtEpochMillis: Long,
    val updatedAtTick: Long,
    val rejectReason: String?
)
