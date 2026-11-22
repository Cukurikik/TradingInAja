package com.tradinginaja.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.core.designsystem.R
import com.tradinginaja.core.designsystem.theme.JetBrainsMonoFontFamily
import com.tradinginaja.core.designsystem.theme.TradingTheme
import com.tradinginaja.domain.engine.CandleDownsampler
import com.tradinginaja.domain.model.Candle
import kotlin.math.max
import kotlin.math.roundToInt

private const val MIN_VISIBLE_CANDLES = 12
private const val DEFAULT_VISIBLE_CANDLES = 60
private const val HALF = 0.5f

/**
 * Chart trading interaktif berbasis Compose Canvas.
 * Mendukung mode Candlestick & Line, pinch-to-zoom, pan horizontal,
 * crosshair tap/long-press drag, serta downsampling otomatis untuk data besar.
 */
@Composable
fun CandlestickChart(
    candles: List<Candle>,
    displayMode: ChartDisplayMode,
    modifier: Modifier = Modifier,
    maxRenderPoints: Int = GameBalance.DEFAULT_CHART_MAX_RENDER_POINTS,
    onCandleInspected: ((Candle?) -> Unit)? = null
) {
    val tradingColors = TradingTheme.colors
    val colorScheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()

    var visibleCount by remember { mutableIntStateOf(DEFAULT_VISIBLE_CANDLES) }
    var panOffsetFromEnd by remember { mutableFloatStateOf(0f) }
    var crosshairIndex by remember { mutableStateOf<Int?>(null) }
    var canvasWidthPx by remember { mutableFloatStateOf(1f) }

    val totalCount = candles.size
    val maxVisible = max(MIN_VISIBLE_CANDLES, totalCount)
    val clampedVisibleCount = visibleCount.coerceIn(MIN_VISIBLE_CANDLES, maxVisible)
    val maxPan = (totalCount - clampedVisibleCount).coerceAtLeast(0).toFloat()
    val clampedPan = panOffsetFromEnd.coerceIn(0f, maxPan)

    val endExclusive = (totalCount - clampedPan.roundToInt()).coerceIn(0, totalCount)
    val startInclusive = (endExclusive - clampedVisibleCount).coerceAtLeast(0)

    val visibleSlice = remember(candles, startInclusive, endExclusive) {
        if (candles.isEmpty() || startInclusive >= endExclusive) {
            emptyList()
        } else {
            candles.subList(startInclusive, endExclusive)
        }
    }

    val renderedCandles = remember(visibleSlice, maxRenderPoints) {
        CandleDownsampler.downsample(visibleSlice, maxBuckets = maxRenderPoints)
    }

    val inspectedCandle = remember(renderedCandles, crosshairIndex) {
        val idx = crosshairIndex
        if (idx != null && idx in renderedCandles.indices) renderedCandles[idx] else null
    }

    LaunchedEffect(inspectedCandle) {
        onCandleInspected?.invoke(inspectedCandle)
    }

    val axisLabelStyle = remember(colorScheme.onSurfaceVariant) {
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 10.sp,
            color = colorScheme.onSurfaceVariant
        )
    }
    val badgeLabelStyle = remember(colorScheme.onPrimary) {
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 10.sp,
            color = colorScheme.onPrimary
        )
    }
    val accessibilitySummary = if (renderedCandles.isEmpty()) {
        stringResource(R.string.chart_empty_state)
    } else {
        val first = renderedCandles.first()
        val last = renderedCandles.last()
        val changePct = if (first.open != 0.0) (last.close - first.open) / first.open * 100.0 else 0.0
        stringResource(
            R.string.chart_a11y_summary,
            formatCompactPrice(last.close),
            String.format(java.util.Locale.US, "%+.2f%%", changePct),
            formatCompactPrice(renderedCandles.maxOf { it.high }),
            formatCompactPrice(renderedCandles.minOf { it.low })
        )
    }

    val palette = remember(tradingColors, colorScheme) {
        ChartPalette(
            bullish = tradingColors.bullish,
            bearish = tradingColors.bearish,
            line = colorScheme.primary,
            grid = colorScheme.outlineVariant.copy(alpha = 0.55f),
            crosshair = colorScheme.onSurfaceVariant,
            badgeBackground = colorScheme.primary
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp)
            .semantics { contentDescription = accessibilitySummary }
            .testTag("candlestick_chart"),
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, tradingColors.cardBorder)
    ) {
        if (renderedCandles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.chart_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
            }
            return@Surface
        }

        Column(modifier = Modifier.fillMaxSize()) {
            ChartInspectionHeader(
                inspectedCandle = inspectedCandle,
                isPanned = clampedPan > HALF,
                onResetView = {
                    panOffsetFromEnd = 0f
                    visibleCount = DEFAULT_VISIBLE_CANDLES
                    crosshairIndex = null
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                InteractiveChartCanvas(
                    renderedCandles = renderedCandles,
                    displayMode = displayMode,
                    crosshairIndex = crosshairIndex,
                    clampedVisibleCount = clampedVisibleCount,
                    maxVisible = maxVisible,
                    maxPan = maxPan,
                    canvasWidthPx = canvasWidthPx,
                    palette = palette,
                    textMeasurer = textMeasurer,
                    axisLabelStyle = axisLabelStyle,
                    badgeLabelStyle = badgeLabelStyle,
                    onCanvasWidthChanged = { canvasWidthPx = it },
                    onZoomChanged = { visibleCount = it },
                    onPanDelta = { delta ->
                        panOffsetFromEnd = (panOffsetFromEnd + delta).coerceIn(0f, maxPan)
                    },
                    onCrosshairChanged = { crosshairIndex = it },
                    onDoubleTapReset = {
                        panOffsetFromEnd = 0f
                        visibleCount = DEFAULT_VISIBLE_CANDLES
                        crosshairIndex = null
                    }
                )
            }
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun InteractiveChartCanvas(
    renderedCandles: List<Candle>,
    displayMode: ChartDisplayMode,
    crosshairIndex: Int?,
    clampedVisibleCount: Int,
    maxVisible: Int,
    maxPan: Float,
    canvasWidthPx: Float,
    palette: ChartPalette,
    textMeasurer: TextMeasurer,
    axisLabelStyle: TextStyle,
    badgeLabelStyle: TextStyle,
    onCanvasWidthChanged: (Float) -> Unit,
    onZoomChanged: (Int) -> Unit,
    onPanDelta: (Float) -> Unit,
    onCrosshairChanged: (Int?) -> Unit,
    onDoubleTapReset: () -> Unit
) {
    val latestRenderedState by rememberUpdatedState(renderedCandles)
    val latestVisibleCount by rememberUpdatedState(clampedVisibleCount)
    val latestMaxVisible by rememberUpdatedState(maxVisible)
    val latestWidthPx by rememberUpdatedState(canvasWidthPx)
    val latestCrosshair by rememberUpdatedState(crosshairIndex)
    val latestMaxPan by rememberUpdatedState(maxPan)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                onCanvasWidthChanged(size.width.toFloat().coerceAtLeast(1f))
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1f) {
                        val nextVisible = (latestVisibleCount / zoom)
                            .roundToInt()
                            .coerceIn(MIN_VISIBLE_CANDLES, latestMaxVisible)
                        onZoomChanged(nextVisible)
                    }
                    if (pan.x != 0f && latestMaxPan >= 0f) {
                        val plotWidth = (latestWidthPx - AXIS_GUTTER_DP.dp.toPx()).coerceAtLeast(1f)
                        val candlesPerPixel = latestVisibleCount / plotWidth
                        onPanDelta(pan.x * candlesPerPixel)
                        onCrosshairChanged(null)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        val list = latestRenderedState
                        val plotWidth = (size.width - AXIS_GUTTER_DP.dp.toPx()).coerceAtLeast(1f)
                        if (list.isEmpty() || tapOffset.x > plotWidth) {
                            onCrosshairChanged(null)
                            return@detectTapGestures
                        }
                        val index = mapXToCandleIndex(tapOffset.x, plotWidth, list.size)
                        onCrosshairChanged(if (latestCrosshair == index) null else index)
                    },
                    onDoubleTap = { onDoubleTapReset() }
                )
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { startOffset ->
                        val list = latestRenderedState
                        if (list.isNotEmpty()) {
                            val plotWidth = (size.width - AXIS_GUTTER_DP.dp.toPx()).coerceAtLeast(1f)
                            onCrosshairChanged(mapXToCandleIndex(startOffset.x, plotWidth, list.size))
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val list = latestRenderedState
                        if (list.isNotEmpty()) {
                            val plotWidth = (size.width - AXIS_GUTTER_DP.dp.toPx()).coerceAtLeast(1f)
                            onCrosshairChanged(mapXToCandleIndex(change.position.x, plotWidth, list.size))
                        }
                    }
                )
            }
    ) {
        val geometry = computeChartGeometry(renderedCandles, displayMode)
        drawPriceGrid(geometry, palette.grid, textMeasurer, axisLabelStyle)
        when (displayMode) {
            ChartDisplayMode.CANDLESTICK -> drawCandlesSeries(renderedCandles, geometry, palette)
            ChartDisplayMode.LINE -> drawLineSeries(renderedCandles, geometry, palette)
        }
        drawLastPriceIndicator(
            lastCandle = renderedCandles.last(),
            geometry = geometry,
            badgeBackground = palette.badgeBackground,
            textMeasurer = textMeasurer,
            badgeLabelStyle = badgeLabelStyle
        )
        val activeIndex = crosshairIndex
        if (activeIndex != null) {
            drawCrosshairOverlay(
                candles = renderedCandles,
                inspectedIndex = activeIndex,
                displayMode = displayMode,
                geometry = geometry,
                palette = palette
            )
        }
    }
}

@Composable
private fun ChartInspectionHeader(inspectedCandle: Candle?, isPanned: Boolean, onResetView: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val infoText = if (inspectedCandle != null) {
            stringResource(
                R.string.chart_ohlcv_format,
                formatCompactPrice(inspectedCandle.open),
                formatCompactPrice(inspectedCandle.high),
                formatCompactPrice(inspectedCandle.low),
                formatCompactPrice(inspectedCandle.close),
                formatCompactVolume(inspectedCandle.volume)
            )
        } else {
            stringResource(R.string.chart_crosshair_hint)
        }

        Text(
            text = infoText,
            style = MaterialTheme.typography.labelSmall,
            color = if (inspectedCandle != null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (isPanned || inspectedCandle != null) {
            FilledTonalIconButton(
                onClick = onResetView,
                modifier = Modifier.testTag("chart_reset_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.MyLocation,
                    contentDescription = stringResource(R.string.chart_reset_view)
                )
            }
        }
    }
}

private fun mapXToCandleIndex(x: Float, plotWidth: Float, candleCount: Int): Int {
    if (candleCount <= 1) return 0
    val slotWidth = plotWidth / candleCount.toFloat()
    return (x / slotWidth).toInt().coerceIn(0, candleCount - 1)
}
