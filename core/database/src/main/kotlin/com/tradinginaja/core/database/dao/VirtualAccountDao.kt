package com.tradinginaja.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tradinginaja.core.database.entity.VirtualAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VirtualAccountDao {
    @Query("SELECT * FROM virtual_accounts WHERE accountId = :accountId LIMIT 1")
    fun observeAccount(accountId: String = VirtualAccountEntity.DEFAULT_ACCOUNT_ID): Flow<VirtualAccountEntity?>

    @Query("SELECT * FROM virtual_accounts WHERE accountId = :accountId LIMIT 1")
    suspend fun getAccount(accountId: String = VirtualAccountEntity.DEFAULT_ACCOUNT_ID): VirtualAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: VirtualAccountEntity)
}
