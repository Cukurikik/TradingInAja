package com.tradinginaja.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "equity_snapshots")
data class EquitySnapshotEntity(
    @PrimaryKey val tickIndex: Long,
    val equityMinorUnits: Long,
    val cashMinorUnits: Long,
    val positionsValueMinorUnits: Long,
    val recordedAtEpochMillis: Long
)
