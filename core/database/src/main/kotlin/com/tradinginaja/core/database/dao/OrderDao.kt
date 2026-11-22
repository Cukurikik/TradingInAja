package com.tradinginaja.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tradinginaja.core.database.entity.OrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders ORDER BY createdAtTick ASC, createdAtEpochMillis ASC")
    fun observeAll(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders ORDER BY createdAtTick ASC, createdAtEpochMillis ASC")
    suspend fun getAll(): List<OrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(orders: List<OrderEntity>)

    @Query("DELETE FROM orders")
    suspend fun clearAll()
}
