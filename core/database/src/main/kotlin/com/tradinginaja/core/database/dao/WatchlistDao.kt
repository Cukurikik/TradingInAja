package com.tradinginaja.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tradinginaja.core.database.entity.WatchlistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {
    @Query("SELECT symbol FROM watchlist_items ORDER BY addedAtEpochMillis ASC")
    fun observeWatchlistSymbols(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist_items WHERE symbol = :symbol)")
    suspend fun exists(symbol: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchlistItemEntity)

    @Query("DELETE FROM watchlist_items WHERE symbol = :symbol")
    suspend fun deleteBySymbol(symbol: String)
}
