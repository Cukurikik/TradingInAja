package com.tradinginaja.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tradinginaja.core.common.GameBalance

@Entity(tableName = "virtual_accounts")
data class VirtualAccountEntity(
    @PrimaryKey val accountId: String = DEFAULT_ACCOUNT_ID,
    val cashMinorUnits: Long = GameBalance.INITIAL_VIRTUAL_CASH_MINOR,
    val careerLevel: Int = 1,
    val totalBorrowFeesMinorUnits: Long = 0L,
    val insolvencyCoverageMinorUnits: Long = 0L,
    val updatedAtEpochMillis: Long = 0L
) {
    companion object {
        const val DEFAULT_ACCOUNT_ID = "default_virtual_account"
    }
}
