package dev.balaji.moonrose.logtrain.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisTickComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import dev.balaji.moonrose.logtrain.insights.HistoryWindow
import dev.balaji.moonrose.logtrain.insights.InsightMath
import dev.balaji.moonrose.logtrain.insights.SparkPoint
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

private val LabelSpacingKey = ExtraStore.Key<Int>()
private val LabelOffsetKey = ExtraStore.Key<Int>()
private val ShowYearKey = ExtraStore.Key<Boolean>()

/**
 * Ensures at least a 2-unit span on integer boundaries so 3 integer-formatted
 * vertical ticks never collapse or repeat the same number on flat/narrow series.
 */
internal fun yAxisBounds(minY: Double, maxY: Double): Pair<Double, Double> {
    if (maxY - minY >= 2.0) return minY to maxY
    val center = round((minY + maxY) / 2.0).coerceAtLeast(1.0)
    if (center - 1.0 <= minY && center + 1.0 >= maxY) {
        return (center - 1.0) to (center + 1.0)
    }
    val lo = floor(minY).coerceAtLeast(0.0)
    val hi = maxOf(ceil(maxY), lo + 2.0)
    return lo to hi
}

private val YRangeProvider = object : CartesianLayerRangeProvider {
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) =
        yAxisBounds(minY, maxY).first

    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) =
        yAxisBounds(minY, maxY).second
}

/**
 * Weekly history line chart (exercise detail) built on Vico. [points] are one
 * entry per calendar week (x = epoch day, 7 apart). The x-axis shows `MMM-dd`
 * labels on the weekly grid, thinned to every Nth week (counting back from the
 * newest, which is always labelled) once there are more than [HistoryWindow.MAX_X_LABELS].
 * Over a year of history the labels switch to `MMM yy`. Scrolling is off for now; flip `scrollEnabled` when >12 weeks are shown.
 */
@Composable
internal fun VicoHistoryChart(
    points: List<SparkPoint>,
    modifier: Modifier = Modifier,
) {
    val lineColor = seriesColor(0)
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    val months = (1..12).map { monthAbbreviation(it) }

    val minX = points.minOf { it.epochDay }
    val weekSpan = ((points.maxOf { it.epochDay } - minX) / HistoryWindow.WEEK_DAYS).toInt().coerceAtLeast(0)
    val labelSpacing = HistoryWindow.labelSpacingWeeks(weekSpan)
    val labelOffset = weekSpan % labelSpacing

    // Over a year of history, `MMM-dd` is ambiguous: switch to `MMM yy`.
    val showYear = HistoryWindow.spansYears(points)

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points, labelSpacing, labelOffset, showYear) {
        modelProducer.runTransaction {
            lineSeries {
                series(x = points.map { it.epochDay.toDouble() }, y = points.map { it.value })
            }
            extras {
                it[LabelSpacingKey] = labelSpacing
                it[LabelOffsetKey] = labelOffset
                it[ShowYearKey] = showYear
            }
        }
    }

    val chart = rememberCartesianChart(
        rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.rememberLine(LineCartesianLayer.LineFill.single(Fill(lineColor))),
            ),
            rangeProvider = YRangeProvider,
        ),
        startAxis = VerticalAxis.rememberStart(
            line = null,
            tick = null,
            label = rememberAxisLabelComponent(style = labelStyle),
            guideline = rememberAxisGuidelineComponent(fill = Fill(axisColor)),
            valueFormatter = remember {
                CartesianValueFormatter { _, value, _ -> InsightMath.formatNumber(value, 0) }
            },
            itemPlacer = remember { VerticalAxis.ItemPlacer.count(count = { 3 }) },
        ),
        bottomAxis = HorizontalAxis.rememberBottom(
            line = rememberAxisLineComponent(fill = Fill(axisColor)),
            tick = rememberAxisTickComponent(fill = Fill(axisColor)),
            label = rememberAxisLabelComponent(style = labelStyle),
            guideline = null,
            valueFormatter = remember(months) {
                CartesianValueFormatter { context, value, _ ->
                    val useYear = context.model.extraStore.getOrNull(ShowYearKey) == true
                    if (useYear) formatMonthYear(value.toLong(), months) else formatMonthDay(value.toLong(), months)
                }
            },
            // Spacing/offset/showYear are read from the model so they always match
            // the data being drawn (never a stale value mid-transition).
            itemPlacer = remember {
                HorizontalAxis.ItemPlacer.aligned(
                    spacing = { (it.getOrNull(LabelSpacingKey) ?: 1).coerceAtLeast(1) },
                    offset = { (it.getOrNull(LabelOffsetKey) ?: 0).coerceAtLeast(0) },
                    addExtremeLabelPadding = false,
                )
            },
        ),
        getXStep = { _, _, _ -> HistoryWindow.WEEK_DAYS.toDouble() },
    )

    CartesianChartHost(
        chart = chart,
        modelProducer = modelProducer,
        modifier = modifier,
        scrollState = rememberVicoScrollState(scrollEnabled = false),
    )
}
