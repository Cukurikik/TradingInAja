package com.tradinginaja.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradinginaja.domain.model.CareerLevel
import com.tradinginaja.domain.model.LearningProgress
import com.tradinginaja.domain.model.ThemeMode
import com.tradinginaja.domain.repository.PortfolioRepository
import com.tradinginaja.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    data class Content(val themeMode: ThemeMode, val careerLevel: CareerLevel, val learningProgress: LearningProgress) :
        ProfileUiState
}

sealed interface ProfileUiEvent {
    data class SelectThemeMode(val mode: ThemeMode) : ProfileUiEvent
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val portfolioRepository: PortfolioRepository
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        preferencesRepository.themeMode,
        preferencesRepository.learningProgress,
        portfolioRepository.portfolioState
    ) { mode, learning, portfolio ->
        ProfileUiState.Content(
            themeMode = mode,
            careerLevel = portfolio?.careerLevel ?: CareerLevel.LEVEL_1_NOVICE,
            learningProgress = learning
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ProfileUiState.Loading
    )

    init {
        viewModelScope.launch {
            portfolioRepository.ensureInitialized()
        }
    }

    fun onEvent(event: ProfileUiEvent) {
        when (event) {
            is ProfileUiEvent.SelectThemeMode -> {
                viewModelScope.launch {
                    preferencesRepository.setThemeMode(event.mode)
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
