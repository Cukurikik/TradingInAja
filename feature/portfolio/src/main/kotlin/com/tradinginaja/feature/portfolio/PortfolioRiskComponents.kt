package com.tradinginaja.feature.portfolio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradinginaja.core.designsystem.component.TradingTermTooltipButton
import com.tradinginaja.core.designsystem.theme.TradingTheme
import com.tradinginaja.domain.model.LiquidationEvent
import com.tradinginaja.domain.model.MarginHealthSnapshot
import com.tradinginaja.domain.model.MarginStatus
import com.tradinginaja.domain.model.PortfolioRiskMetrics
import com.tradinginaja.domain.model.TradingTerm
import java.util.Locale

@Composable
internal fun MarginHealthCard(health: MarginHealthSnapshot, latestLiquidation: LiquidationEvent?) {
    val statusText = when (health.status) {
        MarginStatus.HEALTHY -> stringResource(R.string.portfolio_margin_status_healthy)
        MarginStatus.MARGIN_CALL -> stringResource(R.string.portfolio_margin_status_call)
        MarginStatus.LIQUIDATION -> stringResource(R.string.portfolio_margin_status_liq)
    }
    val statusColor = when (health.status) {
        MarginStatus.HEALTHY -> TradingTheme.colors.bullish
        MarginStatus.MARGIN_CALL -> TradingTheme.colors.warning
        MarginStatus.LIQUIDATION -> TradingTheme.colors.bearish
    }
    val marginLevelLabel = health.marginLevelPct?.let { pct ->
        String.format(Locale.US, "%.1f%%", pct)
    } ?: stringResource(R.string.portfolio_margin_no_leverage)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("portfolio_margin_health_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.portfolio_margin_health_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    TradingTermTooltipButton(term = TradingTerm.MARGIN_CALL)
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCell(
                    label = stringResource(R.string.portfolio_margin_level_label),
                    value = marginLevelLabel,
                    valueColor = statusColor,
                    modifier = Modifier.weight(1f)
                )
                MetricCell(
                    label = stringResource(R.string.portfolio_margin_used_label),
                    value = health.usedLeveragedMargin.formatDisplay(),
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = stringResource(
                    if (health.status == MarginStatus.HEALTHY) {
                        R.string.portfolio_margin_explanation_healthy
                    } else {
                        R.string.portfolio_margin_explanation_call
                    }
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            latestLiquidation?.let { event ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = TradingTheme.colors.bearishContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("portfolio_liquidation_notice")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = TradingTheme.colors.bearish,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                stringResource(
                                    R.string.portfolio_liquidation_notice,
                                    event.symbol,
                                    event.closedQuantity,
                                    event.closedSide.name,
                                    event.liquidationPrice.formatDisplay(),
                                    event.marginLevelBeforePct
                                )
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PortfolioRiskMetricsCard(metrics: PortfolioRiskMetrics) {
    val sharpeText = metrics.simpleSharpeRatio?.let {
        String.format(Locale.US, "%.2f", it)
    } ?: stringResource(R.string.portfolio_metric_na)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("portfolio_risk_metrics_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, TradingTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.portfolio_metrics_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Row {
                    TradingTermTooltipButton(term = TradingTerm.DRAWDOWN)
                    TradingTermTooltipButton(term = TradingTerm.RISK_REWARD)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCell(
                    label = String.format(
                        Locale.US,
                        stringResource(R.string.portfolio_metric_max_drawdown),
                        metrics.currentDrawdownPct
                    ),
                    value = String.format(Locale.US, "%.2f%%", metrics.maxDrawdownPct),
                    valueColor = TradingTheme.colors.bearish,
                    modifier = Modifier.weight(1f)
                )
                MetricCell(
                    label = stringResource(R.string.portfolio_metric_win_rate, metrics.winCount, metrics.lossCount),
                    value = String.format(Locale.US, "%.1f%%", metrics.winRatePct),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCell(
                    label = stringResource(R.string.portfolio_metric_risk_reward),
                    value = String.format(Locale.US, "%.2f", metrics.riskRewardRatio),
                    modifier = Modifier.weight(1f)
                )
                MetricCell(
                    label = stringResource(R.string.portfolio_metric_sharpe),
                    value = sharpeText,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCell(
                    label = stringResource(R.string.portfolio_metric_gross_exposure),
                    value = String.format(Locale.US, "%.1f%%", metrics.grossExposurePct),
                    modifier = Modifier.weight(1f)
                )
                MetricCell(
                    label = stringResource(R.string.portfolio_metric_net_exposure),
                    value = String.format(Locale.US, "%.1f%%", metrics.netExposurePct),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
