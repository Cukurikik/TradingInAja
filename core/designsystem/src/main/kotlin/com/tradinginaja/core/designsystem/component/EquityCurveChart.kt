package com.tradinginaja.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tradinginaja.core.common.Money
import com.tradinginaja.core.designsystem.R
import com.tradinginaja.core.designsystem.theme.TradingNumberTextStyle
import com.tradinginaja.core.designsystem.theme.TradingTheme
import com.tradinginaja.domain.model.EquityPoint

private const val MAX_CURVE_SAMPLES = 120
private const val FILL_ALPHA = 0.25f
private const val GRID_ALPHA = 0.45f
private const val LINE_STROKE_DP = 2.25f
private const val DOT_RADIUS_DP = 4f
private const val DASH_INTERVAL_PX = 10f
private const val PADDING_RATIO = 0.14f

/**
 * Grafik kurva ekuitas portofolio berbasis Compose Canvas dengan garis modal awal.
 */
@Composable
fun EquityCurveChart(points: List<EquityPoint>, baselineEquity: Money, modifier: Modifier = Modifier) {
    val tradingColors = TradingTheme.colors
    val colorScheme = MaterialTheme.colorScheme

    val sampled = remember(points) {
        downsampleEquityPoints(points, MAX_CURVE_SAMPLES)
    }
    val latestEquity = sampled.lastOrNull()?.equity ?: baselineEquity
    val isAboveBaseline = latestEquity >= baselineEquity
    val lineColor = if (isAboveBaseline) tradingColors.bullish else tradingColors.bearish
    val fillBrush = remember(lineColor) {
        Brush.verticalGradient(
            colors = listOf(
                lineColor.copy(alpha = FILL_ALPHA),
                Color.Transparent
            )
        )
    }

    val minEquity = remember(sampled, baselineEquity) {
        sampled.minOfOrNull { it.equity.minorUnits }
            ?.coerceAtMost(baselineEquity.minorUnits)
            ?: baselineEquity.minorUnits
    }
    val maxEquity = remember(sampled, baselineEquity) {
        sampled.maxOfOrNull { it.equity.minorUnits }
            ?.coerceAtLeast(baselineEquity.minorUnits)
            ?: baselineEquity.minorUnits
    }

    val linePath = remember { Path() }
    val fillPath = remember { Path() }
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(DASH_INTERVAL_PX, DASH_INTERVAL_PX), 0f) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .testTag("equity_curve_chart"),
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, tradingColors.cardBorder)
    ) {
        if (sampled.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.equity_chart_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
            return@Surface
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.equity_chart_peak, Money(maxEquity).formatDisplay()),
                    style = TradingNumberTextStyle.copy(
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                        color = colorScheme.onSurfaceVariant
                    )
                )
                Text(
                    text = stringResource(R.string.equity_chart_low, Money(minEquity).formatDisplay()),
                    style = TradingNumberTextStyle.copy(
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                        color = colorScheme.onSurfaceVariant
                    )
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val padY = size.height * PADDING_RATIO
                val usableHeight = (size.height - padY * 2f).coerceAtLeast(1f)
                val span = (maxEquity - minEquity).toDouble()

                fun mapY(minorUnits: Long): Float {
                    val ratio = if (span <= 0.0) 0.5f else ((minorUnits - minEquity).toDouble() / span).toFloat()
                    return size.height - padY - (ratio * usableHeight)
                }

                val baselineY = mapY(baselineEquity.minorUnits)
                drawLine(
                    color = colorScheme.outlineVariant.copy(alpha = GRID_ALPHA),
                    start = Offset(0f, baselineY),
                    end = Offset(size.width, baselineY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect
                )

                if (sampled.size == 1) {
                    val singleY = mapY(sampled.first().equity.minorUnits)
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, singleY),
                        end = Offset(size.width, singleY),
                        strokeWidth = LINE_STROKE_DP.dp.toPx()
                    )
                    drawCircle(
                        color = lineColor,
                        radius = DOT_RADIUS_DP.dp.toPx(),
                        center = Offset(size.width * 0.5f, singleY)
                    )
                    return@Canvas
                }

                linePath.reset()
                fillPath.reset()
                val stepX = size.width / (sampled.size - 1).toFloat()

                for (index in sampled.indices) {
                    val x = index * stepX
                    val y = mapY(sampled[index].equity.minorUnits)
                    if (index == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, size.height)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(size.width, size.height)
                fillPath.close()

                drawPath(path = fillPath, brush = fillBrush)
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(
                        width = LINE_STROKE_DP.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
                drawCircle(
                    color = lineColor,
                    radius = DOT_RADIUS_DP.dp.toPx(),
                    center = Offset(size.width, mapY(sampled.last().equity.minorUnits))
                )
            }
        }
    }
}

private fun downsampleEquityPoints(points: List<EquityPoint>, maxSamples: Int): List<EquityPoint> {
    if (points.size <= maxSamples) return points
    val result = ArrayList<EquityPoint>(maxSamples)
    val step = (points.size - 1).toDouble() / (maxSamples - 1).toDouble()
    for (i in 0 until maxSamples) {
        val idx = (i * step).toInt().coerceIn(0, points.lastIndex)
        result.add(points[idx])
    }
    return result
}
