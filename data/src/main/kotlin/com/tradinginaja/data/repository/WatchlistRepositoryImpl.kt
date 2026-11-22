package com.tradinginaja.data.repository

import com.tradinginaja.core.database.dao.WatchlistDao
import com.tradinginaja.core.database.entity.WatchlistItemEntity
import com.tradinginaja.domain.repository.WatchlistRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class WatchlistRepositoryImpl @Inject constructor(private val watchlistDao: WatchlistDao) : WatchlistRepository {

    override val watchlistSymbols: Flow<Set<String>> =
        watchlistDao.observeWatchlistSymbols().map { it.toSet() }

    override suspend fun toggleWatchlist(symbol: String) {
        if (watchlistDao.exists(symbol)) {
            watchlistDao.deleteBySymbol(symbol)
        } else {
            watchlistDao.upsert(
                WatchlistItemEntity(
                    symbol = symbol,
                    addedAtEpochMillis = System.currentTimeMillis()
                )
            )
        }
    }
}
