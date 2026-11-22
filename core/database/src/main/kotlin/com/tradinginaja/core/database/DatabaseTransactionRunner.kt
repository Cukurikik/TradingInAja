package com.tradinginaja.core.database

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

interface DatabaseTransactionRunner {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}

@Singleton
class RoomTransactionRunner @Inject constructor(private val database: TradingDatabase) : DatabaseTransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T = database.withTransaction { block() }
}
