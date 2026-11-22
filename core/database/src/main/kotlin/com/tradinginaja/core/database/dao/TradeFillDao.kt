package com.tradinginaja.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tradinginaja.core.database.entity.TradeFillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeFillDao {
    @Query("SELECT * FROM trade_fills ORDER BY tickIndex ASC, executedAtEpochMillis ASC")
    fun observeAll(): Flow<List<TradeFillEntity>>

    @Query("SELECT * FROM trade_fills ORDER BY tickIndex ASC, executedAtEpochMillis ASC")
    suspend fun getAll(): List<TradeFillEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(fills: List<TradeFillEntity>)

    @Query("DELETE FROM trade_fills")
    suspend fun clearAll()
}
