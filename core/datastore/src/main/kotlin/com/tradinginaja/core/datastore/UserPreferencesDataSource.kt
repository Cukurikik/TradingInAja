package com.tradinginaja.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.tradinginaja.domain.model.LearningProgress
import com.tradinginaja.domain.model.ThemeMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class UserPreferencesDataSource @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_THEME_MODE]
        ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
    }

    val learningProgress: Flow<LearningProgress> = dataStore.data.map { prefs ->
        LearningProgress(
            tutorialCompleted = prefs[KEY_TUTORIAL_COMPLETED] ?: false,
            completedLessonIds = prefs[KEY_COMPLETED_LESSON_IDS] ?: emptySet(),
            candleDrillsPassed = prefs[KEY_CANDLE_DRILLS_PASSED] ?: 0
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun setTutorialCompleted(completed: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_TUTORIAL_COMPLETED] = completed
        }
    }

    suspend fun markLessonCompleted(lessonId: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_COMPLETED_LESSON_IDS] ?: emptySet()
            prefs[KEY_COMPLETED_LESSON_IDS] = current + lessonId
        }
    }

    suspend fun incrementCandleDrillsPassed() {
        dataStore.edit { prefs ->
            val current = prefs[KEY_CANDLE_DRILLS_PASSED] ?: 0
            prefs[KEY_CANDLE_DRILLS_PASSED] = current + 1
        }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_TUTORIAL_COMPLETED = booleanPreferencesKey("tutorial_completed")
        val KEY_COMPLETED_LESSON_IDS = stringSetPreferencesKey("completed_lesson_ids")
        val KEY_CANDLE_DRILLS_PASSED = intPreferencesKey("candle_drills_passed")
    }
}
