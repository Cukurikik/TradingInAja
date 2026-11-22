package com.example.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.tradinginaja.core.common.GameBalance

private const val STEP_MARKET = 1
private const val STEP_RISK = 2
private const val STEP_POST_MORTEM = 3
private const val STEP_CAREER = 4

@Composable
internal fun InteractiveTutorialDialog(
    stepIndex: Int,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    onJumpToDestination: (TopLevelDestination) -> Unit
) {
    val totalSteps = GameBalance.INTERACTIVE_TUTORIAL_STEP_COUNT
    val safeStep = stepIndex.coerceIn(0, totalSteps - 1)
    val content = tutorialStepContent(safeStep)
    val isLastStep = safeStep == totalSteps - 1

    AlertDialog(
        onDismissRequest = onSkip,
        modifier = Modifier.testTag("interactive_tutorial_dialog"),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = content.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.tutorial_step_badge, safeStep + 1, totalSteps),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("tutorial_skip_button")
                    ) {
                        Text(stringResource(R.string.tutorial_skip))
                    }
                }
                LinearProgressIndicator(
                    progress = { (safeStep + 1).toFloat() / totalSteps.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = content.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = content.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                content.destination?.let { dest ->
                    Surface(
                        onClick = {
                            onSkip()
                            onJumpToDestination(dest)
                        },
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("tutorial_try_action_${dest.name.lowercase()}")
                    ) {
                        Text(
                            text = content.actionLabel.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onNext,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("tutorial_next_button")
            ) {
                Text(
                    text = stringResource(
                        if (isLastStep) R.string.tutorial_finish else R.string.tutorial_next
                    )
                )
            }
        },
        dismissButton = {
            if (safeStep > 0) {
                TextButton(
                    onClick = onPrevious,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("tutorial_back_button")
                ) {
                    Text(stringResource(R.string.tutorial_back))
                }
            }
        }
    )
}

@Composable
private fun tutorialStepContent(stepIndex: Int): TutorialStepUi = when (stepIndex) {
    0 -> TutorialStepUi(
        icon = Icons.Rounded.AccountBalanceWallet,
        title = stringResource(R.string.tutorial_step_1_title),
        body = stringResource(R.string.tutorial_step_1_body),
        destination = null,
        actionLabel = null
    )
    STEP_MARKET -> TutorialStepUi(
        icon = Icons.AutoMirrored.Rounded.ShowChart,
        title = stringResource(R.string.tutorial_step_2_title),
        body = stringResource(R.string.tutorial_step_2_body),
        destination = TopLevelDestination.MARKET,
        actionLabel = stringResource(R.string.tutorial_try_market)
    )
    STEP_RISK -> TutorialStepUi(
        icon = Icons.Rounded.Shield,
        title = stringResource(R.string.tutorial_step_3_title),
        body = stringResource(R.string.tutorial_step_3_body),
        destination = TopLevelDestination.TRADE,
        actionLabel = stringResource(R.string.tutorial_try_trade)
    )
    STEP_POST_MORTEM -> TutorialStepUi(
        icon = Icons.Rounded.Psychology,
        title = stringResource(R.string.tutorial_step_4_title),
        body = stringResource(R.string.tutorial_step_4_body),
        destination = TopLevelDestination.PORTFOLIO,
        actionLabel = stringResource(R.string.tutorial_try_portfolio)
    )
    STEP_CAREER -> TutorialStepUi(
        icon = Icons.Rounded.MilitaryTech,
        title = stringResource(R.string.tutorial_step_5_title),
        body = stringResource(R.string.tutorial_step_5_body),
        destination = TopLevelDestination.LEARN,
        actionLabel = stringResource(R.string.tutorial_try_learn)
    )
    else -> TutorialStepUi(
        icon = Icons.Rounded.AccountBalanceWallet,
        title = stringResource(R.string.tutorial_step_1_title),
        body = stringResource(R.string.tutorial_step_1_body),
        destination = null,
        actionLabel = null
    )
}

private data class TutorialStepUi(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val destination: TopLevelDestination?,
    val actionLabel: String?
)
