package com.tradinginaja.domain.repository

import com.tradinginaja.domain.model.LearningProgress
import com.tradinginaja.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Kontrak penyimpanan preferensi pengguna dan progres tutorial/belajar lokal.
 */
interface PreferencesRepository {
    val themeMode: Flow<ThemeMode>
    val learningProgress: Flow<LearningProgress>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setTutorialCompleted(completed: Boolean)
    suspend fun markLessonCompleted(lessonId: String)
    suspend fun incrementCandleDrillsPassed()
}
