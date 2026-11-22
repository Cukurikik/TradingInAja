package com.tradinginaja.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tradinginaja.core.designsystem.component.FeaturePlaceholderLayout
import com.tradinginaja.core.designsystem.component.careerLevelTitleRes
import com.tradinginaja.core.designsystem.theme.TradingTheme
import com.tradinginaja.domain.engine.LearnPracticeEngine
import com.tradinginaja.domain.model.ThemeMode

@Composable
fun ProfileRoute(
    onReplayTutorial: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onReplayTutorial = onReplayTutorial,
        modifier = modifier
    )
}

@Composable
fun ProfileScreen(
    uiState: ProfileUiState,
    onEvent: (ProfileUiEvent) -> Unit,
    onReplayTutorial: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    FeaturePlaceholderLayout(
        title = stringResource(R.string.profile_title),
        subtitle = stringResource(R.string.profile_placeholder_subtitle),
        badgeLabel = stringResource(R.string.profile_phase_badge),
        icon = Icons.Rounded.Person,
        showDisclaimer = true,
        modifier = modifier.testTag("profile_screen")
    ) {
        when (uiState) {
            ProfileUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }
            is ProfileUiState.Content -> {
                ProfileCareerAndTutorialCard(
                    state = uiState,
                    onReplayTutorial = onReplayTutorial
                )
                ProfileThemeCard(
                    selectedMode = uiState.themeMode,
                    onSelectMode = { onEvent(ProfileUiEvent.SelectThemeMode(it)) }
                )
            }
        }
    }
}

@Composable
private fun ProfileCareerAndTutorialCard(state: ProfileUiState.Content, onReplayTutorial: () -> Unit) {
    val totalLessons = LearnPracticeEngine.defaultLessons().size
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_learning_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.profile_career_summary_title,
                    state.careerLevel.levelNumber,
                    stringResource(careerLevelTitleRes(state.careerLevel))
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(
                    R.string.profile_learning_progress_summary,
                    state.learningProgress.completedLessonIds.size,
                    totalLessons,
                    state.learningProgress.candleDrillsPassed
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onReplayTutorial,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("profile_replay_tutorial_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayCircleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.profile_replay_tutorial),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfileThemeCard(selectedMode: ThemeMode, onSelectMode: (ThemeMode) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.profile_theme_section_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.profile_theme_section_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ThemeMode.entries.forEach { mode ->
                ThemeModeRow(
                    mode = mode,
                    selected = selectedMode == mode,
                    onSelect = { onSelectMode(mode) }
                )
            }
        }
    }
}

@Composable
private fun ThemeModeRow(mode: ThemeMode, selected: Boolean, onSelect: () -> Unit) {
    val label = when (mode) {
        ThemeMode.SYSTEM -> stringResource(R.string.theme_mode_system)
        ThemeMode.LIGHT -> stringResource(R.string.theme_mode_light)
        ThemeMode.DARK -> stringResource(R.string.theme_mode_dark)
        ThemeMode.AMOLED -> stringResource(R.string.theme_mode_amoled)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton
            )
            .padding(vertical = 6.dp)
            .testTag("theme_option_${mode.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
