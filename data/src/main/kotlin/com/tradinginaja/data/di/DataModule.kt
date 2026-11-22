package com.tradinginaja.data.di

import com.tradinginaja.data.repository.EventCatalogRepositoryImpl
import com.tradinginaja.data.repository.MarketCatalogRepositoryImpl
import com.tradinginaja.data.repository.MarketSimulationRepositoryImpl
import com.tradinginaja.data.repository.PortfolioRepositoryImpl
import com.tradinginaja.data.repository.PreferencesRepositoryImpl
import com.tradinginaja.data.repository.WatchlistRepositoryImpl
import com.tradinginaja.domain.repository.EventCatalogRepository
import com.tradinginaja.domain.repository.MarketCatalogRepository
import com.tradinginaja.domain.repository.MarketSimulationRepository
import com.tradinginaja.domain.repository.PortfolioRepository
import com.tradinginaja.domain.repository.PreferencesRepository
import com.tradinginaja.domain.repository.WatchlistRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindMarketCatalogRepository(impl: MarketCatalogRepositoryImpl): MarketCatalogRepository

    @Binds
    @Singleton
    abstract fun bindEventCatalogRepository(impl: EventCatalogRepositoryImpl): EventCatalogRepository

    @Binds
    @Singleton
    abstract fun bindWatchlistRepository(impl: WatchlistRepositoryImpl): WatchlistRepository

    @Binds
    @Singleton
    abstract fun bindMarketSimulationRepository(impl: MarketSimulationRepositoryImpl): MarketSimulationRepository

    @Binds
    @Singleton
    abstract fun bindPortfolioRepository(impl: PortfolioRepositoryImpl): PortfolioRepository
}
