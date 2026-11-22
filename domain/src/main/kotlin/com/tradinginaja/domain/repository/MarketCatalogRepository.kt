package com.tradinginaja.domain.repository

import com.tradinginaja.domain.model.MarketCatalog

interface MarketCatalogRepository {
    suspend fun getCatalog(): MarketCatalog
}
