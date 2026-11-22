package com.tradinginaja.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watchlist_items")
data class WatchlistItemEntity(@PrimaryKey val symbol: String, val addedAtEpochMillis: Long)
