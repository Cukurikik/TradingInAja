package com.tradinginaja.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.tradinginaja.core.designsystem.theme.TradingTheme

private const val FLAT_PRICE_EPSILON = 1e-9
private const val FILL_TOP_ALPHA = 0.24f
private const val STROKE_WIDTH_DP = 1.75f
private const val VERTICAL_PADDING_RATIO = 0.1f

/**
 * Sparkline ringan berbasis Compose Canvas untuk daftar aset di layar Market.
 */
@Composable
fun SparklineChart(prices: List<Double>, isPositive: Boolean, modifier: Modifier = Modifier) {
    val tradingColors = TradingTheme.colors
    val strokeColor = if (isPositive) tradingColors.bullish else tradingColors.bearish
    val fillBrush = remember(strokeColor) {
        Brush.verticalGradient(
            colors = listOf(
                strokeColor.copy(alpha = FILL_TOP_ALPHA),
                Color.Transparent
            )
        )
    }
    val linePath = remember { Path() }
    val fillPath = remember { Path() }

    Canvas(
        modifier = modifier
            .width(76.dp)
            .height(34.dp)
            // Graf dekoratif: arah tren sudah dibacakan oleh teks harga & persentase di kartu aset.
            .clearAndSetSemantics {}
            .testTag("sparkline_chart")
    ) {
        if (prices.size < 2) {
            val midY = size.height * 0.5f
            drawLine(
                color = strokeColor,
                start = Offset(0f, midY),
                end = Offset(size.width, midY),
                strokeWidth = STROKE_WIDTH_DP.dp.toPx(),
                cap = StrokeCap.Round
            )
            return@Canvas
        }

        var minPrice = Double.POSITIVE_INFINITY
        var maxPrice = Double.NEGATIVE_INFINITY
        for (p in prices) {
            if (p < minPrice) minPrice = p
            if (p > maxPrice) maxPrice = p
        }

        val priceSpan = maxPrice - minPrice
        val verticalPad = size.height * VERTICAL_PADDING_RATIO
        val usableHeight = (size.height - verticalPad * 2f).coerceAtLeast(1f)
        val stepX = size.width / (prices.size - 1).toFloat()

        linePath.reset()
        fillPath.reset()

        for (index in prices.indices) {
            val x = index * stepX
            val normalizedY = if (priceSpan <= FLAT_PRICE_EPSILON) {
                0.5f
            } else {
                ((prices[index] - minPrice) / priceSpan).toFloat()
            }
            val y = size.height - verticalPad - (normalizedY * usableHeight)
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
            color = strokeColor,
            style = Stroke(
                width = STROKE_WIDTH_DP.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
