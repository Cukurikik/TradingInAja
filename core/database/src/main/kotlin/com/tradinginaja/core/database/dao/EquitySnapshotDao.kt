package com.tradinginaja.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tradinginaja.core.database.entity.EquitySnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EquitySnapshotDao {
    @Query("SELECT * FROM equity_snapshots ORDER BY tickIndex ASC")
    fun observeAll(): Flow<List<EquitySnapshotEntity>>

    @Query("SELECT * FROM equity_snapshots ORDER BY tickIndex ASC")
    suspend fun getAll(): List<EquitySnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(snapshots: List<EquitySnapshotEntity>)

    @Query("DELETE FROM equity_snapshots")
    suspend fun clearAll()
}
