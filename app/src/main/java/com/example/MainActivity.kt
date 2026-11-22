package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.navigation.TradingAppShell
import com.tradinginaja.core.designsystem.theme.TradingTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
            val ready = uiState as MainUiState.Ready
            TradingTheme(themeMode = ready.themeMode) {
                TradingAppShell(
                    showTutorial = ready.showTutorial,
                    tutorialStepIndex = ready.tutorialStepIndex,
                    onTutorialEvent = mainViewModel::onEvent
                )
            }
        }
    }
}
