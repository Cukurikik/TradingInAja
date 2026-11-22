package com.tradinginaja.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tradinginaja.core.database.entity.PositionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PositionDao {
    @Query("SELECT * FROM positions ORDER BY symbol ASC")
    fun observeAll(): Flow<List<PositionEntity>>

    @Query("SELECT * FROM positions ORDER BY symbol ASC")
    suspend fun getAll(): List<PositionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(positions: List<PositionEntity>)

    @Query("DELETE FROM positions")
    suspend fun clearAll()
}
