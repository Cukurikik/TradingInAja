package com.tradinginaja.core.designsystem.component

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import com.tradinginaja.domain.model.Candle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal const val AXIS_GUTTER_DP = 70
private const val GRID_LINE_COUNT = 4
private const val VOLUME_AREA_RATIO = 0.18f
private const val VOLUME_BAR_MAX_FILL = 0.85f
private const val VOLUME_ALPHA_CANDLE = 0.28f
private const val VOLUME_ALPHA_LINE = 0.22f
private const val PRICE_PADDING_RATIO = 0.08
private const val MIN_PRICE_SPAN_RATIO = 0.004
private const val MIN_PRICE_SPAN_ABS = 0.1
private const val MIN_PRICE_RANGE = 0.01
private const val DOJI_MIN_HEIGHT_DP = 1.5f
private const val CANDLE_BODY_WIDTH_RATIO = 0.66f
private const val WICK_MAX_BODY_RATIO = 0.45f
private const val DASH_INTERVAL_PX = 10f
private const val DASH_GAP_PX = 8f
private const val COMPACT_INTEGER_THRESHOLD = 1_000.0
private const val THOUSAND = 1_000L
private const val MILLION = 1_000_000L
private const val HALF = 0.5f

internal data class ChartViewportGeometry(
    val plotWidth: Float,
    val axisWidthPx: Float,
    val verticalPad: Float,
    val priceTop: Float,
    val priceBottom: Float,
    val priceHeight: Float,
    val volumeHeight: Float,
    val minPrice: Double,
    val maxPrice: Double,
    val priceRange: Double,
    val maxVolume: Long
) {
    fun priceToY(price: Double): Float {
        val ratio = ((price - minPrice) / priceRange).toFloat().coerceIn(0f, 1f)
        return priceBottom - ratio * priceHeight
    }
}

internal data class ChartPalette(
    val bullish: Color,
    val bearish: Color,
    val line: Color,
    val grid: Color,
    val crosshair: Color,
    val badgeBackground: Color
)

internal fun DrawScope.computeChartGeometry(
    candles: List<Candle>,
    displayMode: ChartDisplayMode
): ChartViewportGeometry {
    val axisWidthPx = AXIS_GUTTER_DP.dp.toPx()
    val plotWidth = (size.width - axisWidthPx).coerceAtLeast(1f)
    val verticalPad = 10.dp.toPx()
    val usableHeight = (size.height - verticalPad * 2f).coerceAtLeast(1f)
    val volumeHeight = usableHeight * VOLUME_AREA_RATIO
    val priceTop = verticalPad
    val priceBottom = size.height - verticalPad - volumeHeight
    val priceHeight = (priceBottom - priceTop).coerceAtLeast(1f)

    var rawMin = Double.MAX_VALUE
    var rawMax = Double.MIN_VALUE
    var maxVolume = 1L
    for (candle in candles) {
        val lo = if (displayMode == ChartDisplayMode.LINE) candle.close else candle.low
        val hi = if (displayMode == ChartDisplayMode.LINE) candle.close else candle.high
        if (lo < rawMin) rawMin = lo
        if (hi > rawMax) rawMax = hi
        if (candle.volume > maxVolume) maxVolume = candle.volume
    }

    val span = (rawMax - rawMin).coerceAtLeast(rawMax * MIN_PRICE_SPAN_RATIO).coerceAtLeast(MIN_PRICE_SPAN_ABS)
    val minPrice = (rawMin - span * PRICE_PADDING_RATIO).coerceAtLeast(0.0)
    val maxPrice = rawMax + span * PRICE_PADDING_RATIO
    val priceRange = (maxPrice - minPrice).coerceAtLeast(MIN_PRICE_RANGE)

    return ChartViewportGeometry(
        plotWidth = plotWidth,
        axisWidthPx = axisWidthPx,
        verticalPad = verticalPad,
        priceTop = priceTop,
        priceBottom = priceBottom,
        priceHeight = priceHeight,
        volumeHeight = volumeHeight,
        minPrice = minPrice,
        maxPrice = maxPrice,
        priceRange = priceRange,
        maxVolume = maxVolume
    )
}

internal fun DrawScope.drawPriceGrid(
    geometry: ChartViewportGeometry,
    gridColor: Color,
    textMeasurer: TextMeasurer,
    axisLabelStyle: TextStyle
) {
    for (step in 0..GRID_LINE_COUNT) {
        val fraction = step.toFloat() / GRID_LINE_COUNT.toFloat()
        val y = geometry.priceTop + fraction * geometry.priceHeight
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(geometry.plotWidth, y),
            strokeWidth = 1.dp.toPx()
        )
        val labelPrice = geometry.maxPrice - fraction * geometry.priceRange
        val measured = textMeasurer.measure(
            text = formatCompactPrice(labelPrice),
            style = axisLabelStyle
        )
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(
                x = geometry.plotWidth + 6.dp.toPx(),
                y = (y - measured.size.height * HALF).coerceIn(0f, size.height - measured.size.height)
            )
        )
    }
}

internal fun DrawScope.drawCandlesSeries(
    candles: List<Candle>,
    geometry: ChartViewportGeometry,
    palette: ChartPalette
) {
    val slotWidth = geometry.plotWidth / candles.size.toFloat()
    val bodyWidth = (slotWidth * CANDLE_BODY_WIDTH_RATIO).coerceAtLeast(2f)
    val dojiMinPx = DOJI_MIN_HEIGHT_DP.dp.toPx()

    candles.forEachIndexed { index, candle ->
        val centerX = (index + HALF) * slotWidth
        val candleColor = if (candle.isBullish) palette.bullish else palette.bearish

        val volRatio = candle.volume.toFloat() / geometry.maxVolume.toFloat()
        val barH = (volRatio * geometry.volumeHeight * VOLUME_BAR_MAX_FILL).coerceAtLeast(1.dp.toPx())
        drawRect(
            color = candleColor.copy(alpha = VOLUME_ALPHA_CANDLE),
            topLeft = Offset(centerX - bodyWidth * HALF, size.height - geometry.verticalPad - barH),
            size = Size(bodyWidth, barH)
        )

        drawLine(
            color = candleColor,
            start = Offset(centerX, geometry.priceToY(candle.high)),
            end = Offset(centerX, geometry.priceToY(candle.low)),
            strokeWidth = 1.3.dp.toPx().coerceAtMost(bodyWidth * WICK_MAX_BODY_RATIO)
        )

        val openY = geometry.priceToY(candle.open)
        val closeY = geometry.priceToY(candle.close)
        val topY = min(openY, closeY)
        val bodyH = max(abs(closeY - openY), dojiMinPx)
        drawRect(
            color = candleColor,
            topLeft = Offset(centerX - bodyWidth * HALF, topY),
            size = Size(bodyWidth, bodyH)
        )
    }
}

internal fun DrawScope.drawLineSeries(candles: List<Candle>, geometry: ChartViewportGeometry, palette: ChartPalette) {
    val slotWidth = geometry.plotWidth / candles.size.toFloat()
    val bodyWidth = (slotWidth * CANDLE_BODY_WIDTH_RATIO).coerceAtLeast(2f)
    val linePath = Path()
    val areaPath = Path()

    candles.forEachIndexed { index, candle ->
        val x = if (candles.size == 1) {
            geometry.plotWidth * HALF
        } else {
            index * (geometry.plotWidth / (candles.size - 1))
        }
        val y = geometry.priceToY(candle.close)
        if (index == 0) {
            linePath.moveTo(x, y)
            areaPath.moveTo(x, geometry.priceBottom)
            areaPath.lineTo(x, y)
        } else {
            linePath.lineTo(x, y)
            areaPath.lineTo(x, y)
        }
        if (index == candles.lastIndex) {
            areaPath.lineTo(x, geometry.priceBottom)
            areaPath.close()
        }

        val barCenter = (index + HALF) * slotWidth
        val volRatio = candle.volume.toFloat() / geometry.maxVolume.toFloat()
        val barH = (volRatio * geometry.volumeHeight * VOLUME_BAR_MAX_FILL).coerceAtLeast(1.dp.toPx())
        val barColor = if (candle.isBullish) palette.bullish else palette.bearish
        drawRect(
            color = barColor.copy(alpha = VOLUME_ALPHA_LINE),
            topLeft = Offset(barCenter - bodyWidth * HALF, size.height - geometry.verticalPad - barH),
            size = Size(bodyWidth, barH)
        )
    }

    drawPath(
        path = areaPath,
        brush = Brush.verticalGradient(
            colors = listOf(palette.line.copy(alpha = VOLUME_ALPHA_CANDLE), Color.Transparent),
            startY = geometry.priceTop,
            endY = geometry.priceBottom
        )
    )
    drawPath(
        path = linePath,
        color = palette.line,
        style = Stroke(
            width = 2.2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

internal fun DrawScope.drawLastPriceIndicator(
    lastCandle: Candle,
    geometry: ChartViewportGeometry,
    badgeBackground: Color,
    textMeasurer: TextMeasurer,
    badgeLabelStyle: TextStyle
) {
    val lastY = geometry.priceToY(lastCandle.close)
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_INTERVAL_PX, DASH_GAP_PX), 0f)
    drawLine(
        color = badgeBackground.copy(alpha = 0.7f),
        start = Offset(0f, lastY),
        end = Offset(geometry.plotWidth, lastY),
        strokeWidth = 1.dp.toPx(),
        pathEffect = dashEffect
    )

    val lastMeasured = textMeasurer.measure(
        text = formatCompactPrice(lastCandle.close),
        style = badgeLabelStyle
    )
    val pillPadH = 4.dp.toPx()
    val pillPadV = 2.dp.toPx()
    val pillTop = (lastY - lastMeasured.size.height * HALF - pillPadV)
        .coerceIn(0f, size.height - lastMeasured.size.height - pillPadV * 2f)
    drawRoundRect(
        color = badgeBackground,
        topLeft = Offset(geometry.plotWidth + 3.dp.toPx(), pillTop),
        size = Size(
            width = (lastMeasured.size.width + pillPadH * 2f).coerceAtMost(geometry.axisWidthPx - 4.dp.toPx()),
            height = lastMeasured.size.height + pillPadV * 2f
        ),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
    )
    drawText(
        textLayoutResult = lastMeasured,
        topLeft = Offset(geometry.plotWidth + 3.dp.toPx() + pillPadH, pillTop + pillPadV)
    )
}

internal fun DrawScope.drawCrosshairOverlay(
    candles: List<Candle>,
    inspectedIndex: Int,
    displayMode: ChartDisplayMode,
    geometry: ChartViewportGeometry,
    palette: ChartPalette
) {
    if (inspectedIndex !in candles.indices) return
    val slotWidth = geometry.plotWidth / candles.size.toFloat()
    val selected = candles[inspectedIndex]
    val crossX = if (displayMode == ChartDisplayMode.LINE && candles.size > 1) {
        inspectedIndex * (geometry.plotWidth / (candles.size - 1))
    } else {
        (inspectedIndex + HALF) * slotWidth
    }
    val crossY = geometry.priceToY(selected.close)
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_INTERVAL_PX, DASH_GAP_PX), 0f)
    drawLine(
        color = palette.crosshair.copy(alpha = 0.85f),
        start = Offset(crossX, geometry.priceTop),
        end = Offset(crossX, geometry.priceBottom),
        strokeWidth = 1.dp.toPx(),
        pathEffect = dashEffect
    )
    drawLine(
        color = palette.crosshair.copy(alpha = 0.85f),
        start = Offset(0f, crossY),
        end = Offset(geometry.plotWidth, crossY),
        strokeWidth = 1.dp.toPx(),
        pathEffect = dashEffect
    )
    drawCircle(
        color = palette.badgeBackground,
        radius = 4.5.dp.toPx(),
        center = Offset(crossX, crossY)
    )
}

internal fun formatCompactPrice(price: Double): String = if (price >= COMPACT_INTEGER_THRESHOLD) {
    String.format(Locale.US, "%,.0f", price).replace(',', '.')
} else {
    String.format(Locale.US, "%.2f", price)
}

internal fun formatCompactVolume(volume: Long): String = when {
    volume >= MILLION -> String.format(Locale.US, "%.1fM", volume.toDouble() / MILLION)
    volume >= THOUSAND -> String.format(Locale.US, "%.1fK", volume.toDouble() / THOUSAND)
    else -> volume.toString()
}
