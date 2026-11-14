package dev.balaji.moonrose.logtrain.insights

/**
 * Zoom windows for the exercise history chart: "zoomed in" shows the most
 * recent [RECENT_WEEKS] weeks, "zoomed out" shows all history. Shared so
 * Compose and SwiftUI agree on what each zoom level contains.
 */
object HistoryWindow {
    const val RECENT_WEEKS = 5
    const val WEEK_DAYS = 7
    const val MAX_X_LABELS = 6
    private const val MIN_CHART_POINTS = 2

    /** Weeks with data from the last [RECENT_WEEKS] calendar weeks of [points]' span. */
    fun recent(points: List<SparkPoint>): List<SparkPoint> {
        if (points.isEmpty()) return points
        val cutoff = points.last().epochDay - WEEK_DAYS.toLong() * (RECENT_WEEKS - 1)
        val window = points.filter { it.epochDay >= cutoff }
        // Sparse training: fall back to the last two data points so a line can still be drawn.
        return if (window.size >= MIN_CHART_POINTS) window else points.takeLast(MIN_CHART_POINTS)
    }

    /** Zooming only matters when there is more history than the recent window. */
    fun canZoom(points: List<SparkPoint>): Boolean = points.size > recent(points).size

    /** Whether the x-axis spans a year or more, so labels need the year to be unambiguous. */
    fun spansYears(points: List<SparkPoint>): Boolean =
        points.size >= MIN_CHART_POINTS && points.last().epochDay - points.first().epochDay >= 52L * WEEK_DAYS

    /** Step in weeks between x-axis labels so at most [MAX_X_LABELS] labels are shown across [weekSpan]. */
    fun labelSpacingWeeks(weekSpan: Int): Int =
        ((weekSpan.coerceAtLeast(0) + MAX_X_LABELS) / MAX_X_LABELS).coerceAtLeast(1)

    /**
     * Epoch days for x-axis ticks across [points]' span on the weekly grid, thinned to at most
     * [MAX_X_LABELS] ticks and anchored so the newest week is always labeled.
     */
    fun xAxisLabelDays(points: List<SparkPoint>): List<Long> {
        if (points.size < MIN_CHART_POINTS) return points.map { it.epochDay }
        val minX = points.minOf { it.epochDay }
        val maxX = points.maxOf { it.epochDay }
        val weekSpan = ((maxX - minX) / WEEK_DAYS).toInt().coerceAtLeast(0)
        val spacing = labelSpacingWeeks(weekSpan)
        val offset = weekSpan % spacing
        val stepDays = spacing * WEEK_DAYS.toLong()
        var day = minX + offset * WEEK_DAYS.toLong()
        return buildList {
            while (day <= maxX) {
                add(day)
                day += stepDays
            }
        }
    }
}
