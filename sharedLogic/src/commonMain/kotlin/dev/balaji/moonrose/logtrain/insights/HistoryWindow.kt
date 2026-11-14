package dev.balaji.moonrose.logtrain.insights

/**
 * Zoom windows for the exercise history chart: "zoomed in" shows the most
 * recent [RECENT_WEEKS] weeks, "zoomed out" shows all history. Shared so
 * Compose and SwiftUI agree on what each zoom level contains.
 */
object HistoryWindow {
    const val RECENT_WEEKS = 5

    /** Weeks with data from the last [RECENT_WEEKS] calendar weeks of [points]' span. */
    fun recent(points: List<SparkPoint>): List<SparkPoint> {
        if (points.isEmpty()) return points
        val cutoff = points.last().epochDay - 7L * (RECENT_WEEKS - 1)
        val window = points.filter { it.epochDay >= cutoff }
        // Sparse training: fall back to the last few data points so the chart isn't empty.
        return if (window.size >= 2) window else points.takeLast(RECENT_WEEKS)
    }

    /** Zooming only matters when there is more history than the recent window. */
    fun canZoom(points: List<SparkPoint>): Boolean = points.size > recent(points).size

    /** Whether the x-axis spans over a year, so labels need the year to be unambiguous. */
    fun spansYears(points: List<SparkPoint>): Boolean =
        points.size >= 2 && points.last().epochDay - points.first().epochDay > 52 * 7L
}
