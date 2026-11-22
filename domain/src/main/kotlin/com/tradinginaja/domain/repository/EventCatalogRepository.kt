package com.tradinginaja.domain.repository

import com.tradinginaja.domain.model.EventCatalog

interface EventCatalogRepository {
    suspend fun getEventCatalog(): EventCatalog
}
