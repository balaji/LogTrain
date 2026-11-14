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

// Y-range travels with the model (ExtraStore) so the range provider is read
// consistently with the data it applies to, instead of racing recomposition.
private val MinYKey = ExtraStore.Key<Double>()
private val MaxYKey = ExtraStore.Key<Double>()
private val LabelSpacingKey = ExtraStore.Key<Int>()
private val LabelOffsetKey = ExtraStore.Key<Int>()
private val YRangeProvider = object : CartesianLayerRangeProvider {
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) =
        extraStore.getOrNull(MinYKey) ?: minY

    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) =
        extraStore.getOrNull(MaxYKey) ?: maxY
}

private const val WEEK_DAYS = 7
private const val MAX_X_LABELS = 6

/**
 * Weekly history line chart (exercise detail) built on Vico. [points] are one
 * entry per calendar week (x = epoch day, 7 apart). The x-axis shows `MMM-dd`
 * labels on the weekly grid, thinned to every Nth week (counting back from the
 * newest, which is always labelled) once there are more than [MAX_X_LABELS].
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

    val minX = points.minOf { it.epochDay }
    val weekSpan = ((points.maxOf { it.epochDay } - minX) / WEEK_DAYS).toInt()
    val labelSpacing = ceil((weekSpan + 1) / MAX_X_LABELS.toDouble()).toInt().coerceAtLeast(1)
    val labelOffset = weekSpan % labelSpacing

    // Over a year of history, `MMM-dd` is ambiguous: switch to `MMM yy`.
    val showYear = HistoryWindow.spansYears(points)

    val rawMinY = points.minOf { it.value }
    val maxY = points.maxOf { it.value }
    val minY = rawMinY

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points, minY, maxY, labelSpacing, labelOffset) {
        modelProducer.runTransaction {
            lineSeries {
                series(x = points.map { it.epochDay.toDouble() }, y = points.map { it.value })
            }
            extras {
                it[MinYKey] = minY
                it[MaxYKey] = maxY
                it[LabelSpacingKey] = labelSpacing
                it[LabelOffsetKey] = labelOffset
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
            valueFormatter = remember(showYear) {
                CartesianValueFormatter { _, value, _ ->
                    if (showYear) formatMonthYear(value.toLong()) else formatMonthDay(value.toLong())
                }
            },
            // Spacing/offset are read from the model so they always match the
            // data being drawn (never a stale value mid-transition).
            itemPlacer = remember {
                HorizontalAxis.ItemPlacer.aligned(
                    spacing = { (it.getOrNull(LabelSpacingKey) ?: 1).coerceAtLeast(1) },
                    offset = { (it.getOrNull(LabelOffsetKey) ?: 0).coerceAtLeast(0) },
                    addExtremeLabelPadding = false,
                )
            },
        ),
        getXStep = { _, _, _ -> WEEK_DAYS.toDouble() },
    )

    CartesianChartHost(
        chart = chart,
        modelProducer = modelProducer,
        modifier = modifier,
        scrollState = rememberVicoScrollState(scrollEnabled = false),
    )
}
