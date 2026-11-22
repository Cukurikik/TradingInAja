package com.tradinginaja.data.repository

import android.content.Context
import com.tradinginaja.data.catalog.AssetCatalogParser
import com.tradinginaja.domain.model.MarketCatalog
import com.tradinginaja.domain.repository.MarketCatalogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class MarketCatalogRepositoryImpl @Inject constructor(@ApplicationContext private val context: Context) :
    MarketCatalogRepository {

    private val mutex = Mutex()
    private var cachedCatalog: MarketCatalog? = null

    override suspend fun getCatalog(): MarketCatalog = cachedCatalog ?: mutex.withLock {
        cachedCatalog ?: withContext(Dispatchers.IO) {
            val rawJson = context.assets.open(CATALOG_ASSET_FILE).bufferedReader().use { it.readText() }
            AssetCatalogParser.parse(rawJson)
        }.also { parsed ->
            cachedCatalog = parsed
        }
    }

    private companion object {
        const val CATALOG_ASSET_FILE = "market_catalog_v1.json"
    }
}
