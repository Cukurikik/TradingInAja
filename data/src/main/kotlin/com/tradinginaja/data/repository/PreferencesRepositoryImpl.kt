package com.tradinginaja.data.repository

import com.tradinginaja.core.datastore.UserPreferencesDataSource
import com.tradinginaja.domain.model.LearningProgress
import com.tradinginaja.domain.model.ThemeMode
import com.tradinginaja.domain.repository.PreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class PreferencesRepositoryImpl @Inject constructor(private val dataSource: UserPreferencesDataSource) :
    PreferencesRepository {
    override val themeMode: Flow<ThemeMode> = dataSource.themeMode
    override val learningProgress: Flow<LearningProgress> = dataSource.learningProgress

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataSource.setThemeMode(mode)
    }

    override suspend fun setTutorialCompleted(completed: Boolean) {
        dataSource.setTutorialCompleted(completed)
    }

    override suspend fun markLessonCompleted(lessonId: String) {
        dataSource.markLessonCompleted(lessonId)
    }

    override suspend fun incrementCandleDrillsPassed() {
        dataSource.incrementCandleDrillsPassed()
    }
}
