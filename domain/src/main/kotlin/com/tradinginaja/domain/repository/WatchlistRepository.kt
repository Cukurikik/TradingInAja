package com.tradinginaja.domain.repository

import kotlinx.coroutines.flow.Flow

interface WatchlistRepository {
    val watchlistSymbols: Flow<Set<String>>
    suspend fun toggleWatchlist(symbol: String)
}
