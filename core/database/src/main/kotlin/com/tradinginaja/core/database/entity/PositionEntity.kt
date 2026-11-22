package com.tradinginaja.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "positions")
data class PositionEntity(
    @PrimaryKey val symbol: String,
    val quantity: Long,
    val costBasisMinorUnits: Long,
    val marginLockedMinorUnits: Long = costBasisMinorUnits,
    val leverage: Int = 1,
    val accumulatedBorrowFeeMinorUnits: Long = 0L
)
