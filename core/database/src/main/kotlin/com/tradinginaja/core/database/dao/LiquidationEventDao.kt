package com.tradinginaja.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tradinginaja.core.database.entity.LiquidationEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LiquidationEventDao {
    @Query("SELECT * FROM liquidation_events ORDER BY tickIndex DESC, occurredAtEpochMillis DESC")
    fun observeAll(): Flow<List<LiquidationEventEntity>>

    @Query("SELECT * FROM liquidation_events ORDER BY tickIndex ASC, occurredAtEpochMillis ASC")
    suspend fun getAll(): List<LiquidationEventEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(events: List<LiquidationEventEntity>)

    @Query("DELETE FROM liquidation_events")
    suspend fun clearAll()
}
