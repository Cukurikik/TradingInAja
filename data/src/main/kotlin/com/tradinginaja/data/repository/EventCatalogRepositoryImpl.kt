package com.tradinginaja.data.repository

import android.content.Context
import com.tradinginaja.data.catalog.EventCatalogParser
import com.tradinginaja.domain.model.EventCatalog
import com.tradinginaja.domain.repository.EventCatalogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class EventCatalogRepositoryImpl @Inject constructor(@ApplicationContext private val context: Context) :
    EventCatalogRepository {

    private val mutex = Mutex()
    private var cachedCatalog: EventCatalog? = null

    override suspend fun getEventCatalog(): EventCatalog = cachedCatalog ?: mutex.withLock {
        cachedCatalog ?: withContext(Dispatchers.IO) {
            val rawJson = context.assets.open(EVENTS_ASSET_FILE).bufferedReader().use { it.readText() }
            EventCatalogParser.parse(rawJson)
        }.also { parsed ->
            cachedCatalog = parsed
        }
    }

    private companion object {
        const val EVENTS_ASSET_FILE = "market_events_v1.json"
    }
}
