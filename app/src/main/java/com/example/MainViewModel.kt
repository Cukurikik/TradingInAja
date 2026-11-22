package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.domain.model.ThemeMode
import com.tradinginaja.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MainUiState {
    data class Ready(val themeMode: ThemeMode, val showTutorial: Boolean, val tutorialStepIndex: Int) : MainUiState
}

sealed interface MainUiEvent {
    data object ReplayTutorial : MainUiEvent
    data object NextTutorialStep : MainUiEvent
    data object PreviousTutorialStep : MainUiEvent
    data object SkipOrFinishTutorial : MainUiEvent
}

@HiltViewModel
class MainViewModel @Inject constructor(private val preferencesRepository: PreferencesRepository) : ViewModel() {

    private val replayRequested = MutableStateFlow(false)
    private val tutorialStepIndex = MutableStateFlow(0)

    val uiState: StateFlow<MainUiState> = combine(
        preferencesRepository.themeMode,
        preferencesRepository.learningProgress,
        replayRequested,
        tutorialStepIndex
    ) { mode, learning, replay, step ->
        MainUiState.Ready(
            themeMode = mode,
            showTutorial = !learning.tutorialCompleted || replay,
            tutorialStepIndex = step
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = MainUiState.Ready(
            themeMode = ThemeMode.SYSTEM,
            showTutorial = false,
            tutorialStepIndex = 0
        )
    )

    fun onEvent(event: MainUiEvent) {
        when (event) {
            MainUiEvent.ReplayTutorial -> {
                tutorialStepIndex.value = 0
                replayRequested.value = true
            }
            MainUiEvent.NextTutorialStep -> {
                val next = tutorialStepIndex.value + 1
                if (next >= GameBalance.INTERACTIVE_TUTORIAL_STEP_COUNT) {
                    completeTutorial()
                } else {
                    tutorialStepIndex.value = next
                }
            }
            MainUiEvent.PreviousTutorialStep -> {
                tutorialStepIndex.value = (tutorialStepIndex.value - 1).coerceAtLeast(0)
            }
            MainUiEvent.SkipOrFinishTutorial -> completeTutorial()
        }
    }

    private fun completeTutorial() {
        replayRequested.value = false
        tutorialStepIndex.value = 0
        viewModelScope.launch {
            preferencesRepository.setTutorialCompleted(true)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
