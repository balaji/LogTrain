package dev.balaji.moonrose.logtrain.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.logtrain.insights.BmiCategory
import dev.balaji.moonrose.logtrain.insights.BmiClassification
import dev.balaji.moonrose.logtrain.insights.InsightMath
import dev.balaji.moonrose.logtrain.insights.SparkPoint
import dev.balaji.moonrose.logtrain.insights.SparkSeries

// Categorical palette for series lines and the body-part pie, in a fixed
// CVD-safe order with a separate step set per color mode.
internal val PieColorsLight = listOf(
    Color(0xFF2A78D6), Color(0xFF1BAF7A), Color(0xFFEDA100), Color(0xFF008300),
    Color(0xFF4A3AA7), Color(0xFFE34948), Color(0xFFE87BA4), Color(0xFFEB6834),
)
internal val PieColorsDark = listOf(
    Color(0xFF3987E5), Color(0xFF199E70), Color(0xFFC98500), Color(0xFF008300),
    Color(0xFF9085E9), Color(0xFFE66767), Color(0xFFD55181), Color(0xFFD95926),
)
internal val PieOtherColor = Color(0xFF898781)

@Composable
internal fun seriesColor(index: Int): Color {
    val palette = if (isSystemInDarkTheme()) PieColorsDark else PieColorsLight
    return palette[index % palette.size]
}

/**
 * Overlaid line chart for 1–3 series sharing one x/y domain, with min/max
 * y-axis labels so the %-change toggle has a visible effect. In %-change
 * mode every series is re-based to percent moved since its first week.
 */
@Composable
internal fun ProgressionChart(
    series: List<SparkSeries>,
    showPctChange: Boolean,
    modifier: Modifier = Modifier,
    startYAtZero: Boolean = false,
    showDateAxis: Boolean = false,
) {
    val colors = series.indices.map { seriesColor(it) }
    val labelStyle = MaterialTheme.typography.labelSmall
        .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()
    val lines = series.map { s ->
        if (showPctChange) {
            val base = s.points.first().value
            s.points.map { SparkPoint(it.epochDay, if (base == 0.0) 0.0 else (it.value / base - 1) * 100) }
        } else {
            s.points
        }
    }
    Canvas(modifier) {
        val all = lines.flatten()
        val minX = all.minOf { it.epochDay }
        val maxX = all.maxOf { it.epochDay }
        val rawMinY = all.minOf { it.value }
        val minY = if (startYAtZero && !showPctChange) minOf(0.0, rawMinY) else rawMinY
        val maxY = all.maxOf { it.value }
        val spanX = (maxX - minX).coerceAtLeast(1).toFloat()
        val spanY = (maxY - minY).toFloat().coerceAtLeast(1e-3f)

        fun label(value: Double) =
            if (showPctChange) "${InsightMath.formatNumber(value, 0)}%" else InsightMath.formatNumber(value, 0)
        val maxLabel = textMeasurer.measure(label(maxY), labelStyle)
        val minLabel = textMeasurer.measure(label(minY), labelStyle)
        val chartLeft = maxOf(maxLabel.size.width, minLabel.size.width) + 6.dp.toPx()
        val chartWidth = (size.width - chartLeft).coerceAtLeast(1f)

        // Plot area; with a date axis the bottom strip is reserved for x labels.
        val axisHeight = if (showDateAxis) textMeasurer.measure("Mmm-00", labelStyle).size.height + 4.dp.toPx() else 0f
        val plotHeight = (size.height - axisHeight).coerceAtLeast(1f)

        drawText(maxLabel, topLeft = Offset(0f, 0f))
        drawText(minLabel, topLeft = Offset(0f, plotHeight - minLabel.size.height))

        if (showDateAxis) {
            // Label every point; when they would collide, keep every Nth,
            // counting back from the newest so the latest date is always shown.
            val xs = lines.first().map { it.epochDay }
            val measured = xs.map { textMeasurer.measure(formatMonthDay(it), labelStyle) }
            val gap = 8.dp.toPx()
            val minStep = if (xs.size < 2) 1 else (1..xs.size).first { step ->
                val pxPerPoint = chartWidth * (xs.zipWithNext { a, b -> (b - a) }.minOrNull() ?: 1) / spanX
                pxPerPoint * step >= measured.maxOf { it.size.width } + gap
            }
            for (i in xs.indices.reversed().filter { (xs.lastIndex - it) % minStep == 0 }) {
                val m = measured[i]
                val cx = chartLeft + (xs[i] - minX) / spanX * chartWidth
                val left = (cx - m.size.width / 2f).coerceIn(0f, size.width - m.size.width)
                drawText(m, topLeft = Offset(left, size.height - m.size.height))
            }
        }

        lines.forEachIndexed { seriesIndex, points ->
            if (points.size < 2) return@forEachIndexed
            val color = colors[seriesIndex]
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = chartLeft + (p.epochDay - minX) / spanX * chartWidth
                val y = plotHeight - (p.value - minY).toFloat() / spanY * plotHeight
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
            points.last().let { p ->
                val x = chartLeft + (p.epochDay - minX) / spanX * chartWidth
                val y = plotHeight - (p.value - minY).toFloat() / spanY * plotHeight
                drawCircle(color, radius = 3.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

private val MonthAbbrev = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** Civil (year, month 1-12, day) from an epoch day, via the civil-from-days algorithm. */
private fun civilFromEpochDay(epochDay: Long): Triple<Int, Int, Int> {
    val z = epochDay + 719468
    val era = z.floorDiv(146097L)
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
    val year = (yoe + era * 400 + if (month <= 2) 1 else 0).toInt()
    return Triple(year, month, day)
}

/** Formats an epoch day as `MMM-dd` (e.g. `Nov-14`). */
internal fun formatMonthDay(epochDay: Long): String {
    val (_, month, day) = civilFromEpochDay(epochDay)
    return "${MonthAbbrev[month - 1]}-${day.toString().padStart(2, '0')}"
}

/** Formats an epoch day as `MMM yy` (e.g. `Nov 26`), for axes spanning more than a year. */
internal fun formatMonthYear(epochDay: Long): String {
    val (year, month, _) = civilFromEpochDay(epochDay)
    return "${MonthAbbrev[month - 1]} ${(year % 100).toString().padStart(2, '0')}"
}

@Composable
internal fun Sparkline(points: List<SparkPoint>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val minX = points.minOf { it.epochDay }
        val maxX = points.maxOf { it.epochDay }
        val minY = points.minOf { it.value }
        val maxY = points.maxOf { it.value }
        val spanX = (maxX - minX).coerceAtLeast(1).toFloat()
        val spanY = (maxY - minY).toFloat().coerceAtLeast(1e-3f)
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = (p.epochDay - minX) / spanX * size.width
            val y = size.height - (p.value - minY).toFloat() / spanY * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
        points.lastOrNull()?.let { p ->
            val x = (p.epochDay - minX) / spanX * size.width
            val y = size.height - (p.value - minY).toFloat() / spanY * size.height
            drawCircle(color, radius = 3.dp.toPx(), center = Offset(x, y))
        }
    }
}

// Health-band palette for the BMI spectrum, sharing the categorical hues used
// elsewhere: blue (underweight), green (normal), amber (overweight), red (obese).
private val BmiColorsLight = mapOf(
    BmiCategory.UNDERWEIGHT to Color(0xFF2A78D6),
    BmiCategory.NORMAL to Color(0xFF1BAF7A),
    BmiCategory.OVERWEIGHT to Color(0xFFEDA100),
    BmiCategory.OBESE to Color(0xFFE34948),
)
private val BmiColorsDark = mapOf(
    BmiCategory.UNDERWEIGHT to Color(0xFF3987E5),
    BmiCategory.NORMAL to Color(0xFF199E70),
    BmiCategory.OVERWEIGHT to Color(0xFFC98500),
    BmiCategory.OBESE to Color(0xFFE66767),
)

/**
 * Horizontal color-banded scale from [BmiCategory.CHART_MIN] to
 * [BmiCategory.CHART_MAX] with a marker at the calculated [bmi]. Each band is
 * one category under [classification]; the marker is clamped to the drawn
 * range so extreme values still land at an end rather than off-canvas.
 */
@Composable
internal fun BmiSpectrum(bmi: Double, classification: BmiClassification, modifier: Modifier = Modifier) {
    val colors = if (isSystemInDarkTheme()) BmiColorsDark else BmiColorsLight
    val markerColor = MaterialTheme.colorScheme.onSurface
    val trackColor = MaterialTheme.colorScheme.surface
    Canvas(modifier.fillMaxWidth().height(40.dp)) {
        val min = BmiCategory.CHART_MIN
        val max = BmiCategory.CHART_MAX
        val span = (max - min).toFloat()
        fun xFor(value: Double) = ((value.coerceIn(min, max) - min) / span * size.width).toFloat()

        val barTop = 14.dp.toPx()
        val barBottom = size.height
        val radius = (barBottom - barTop) / 2f
        val clip = Path().apply {
            addRoundRect(RoundRect(0f, barTop, size.width, barBottom, CornerRadius(radius, radius)))
        }
        clipPath(clip) {
            BmiCategory.entries.forEachIndexed { i, category ->
                val start = if (i == 0) min else classification.lowerBound(category)
                val end = if (i == BmiCategory.entries.lastIndex) {
                    max
                } else {
                    classification.lowerBound(BmiCategory.entries[i + 1])
                }
                val left = xFor(start)
                drawRect(
                    color = colors.getValue(category),
                    topLeft = Offset(left, barTop),
                    size = Size(xFor(end) - left, barBottom - barTop),
                )
            }
        }

        // Downward triangle above the bar plus a hairline through it, both
        // ringed in the track color so the marker reads over any band.
        val markerX = xFor(bmi)
        val half = 6.dp.toPx()
        val triangle = Path().apply {
            moveTo(markerX - half, 0f)
            lineTo(markerX + half, 0f)
            lineTo(markerX, barTop)
            close()
        }
        drawPath(triangle, trackColor, style = Stroke(width = 3.dp.toPx()))
        drawPath(triangle, markerColor)
        drawLine(
            trackColor,
            start = Offset(markerX, barTop),
            end = Offset(markerX, barBottom),
            strokeWidth = 4.dp.toPx(),
        )
        drawLine(
            markerColor,
            start = Offset(markerX, barTop),
            end = Offset(markerX, barBottom),
            strokeWidth = 2.dp.toPx(),
        )
    }
}
