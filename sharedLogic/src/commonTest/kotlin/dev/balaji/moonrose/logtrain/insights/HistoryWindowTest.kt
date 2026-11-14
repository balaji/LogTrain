package dev.balaji.moonrose.logtrain.insights

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryWindowTest {
    private fun weeklyPoints(vararg weekIndices: Int): List<SparkPoint> =
        weekIndices.map { SparkPoint(epochDay = 100L + 7L * it, value = 100.0 + it) }

    @Test
    fun `recent returns empty list for empty input`() {
        assertEquals(emptyList(), HistoryWindow.recent(emptyList()))
    }

    @Test
    fun `recent returns points inside the last RECENT_WEEKS calendar weeks when at least two exist`() {
        val points = weeklyPoints(0, 1, 5, 6, 8, 9)
        // Last point is week 9 -> cutoff is week 9 - (5 - 1) = week 5.
        assertEquals(weeklyPoints(5, 6, 8, 9), HistoryWindow.recent(points))
    }

    @Test
    fun `recent falls back to last two points for sparse training`() {
        val points = weeklyPoints(0, 10, 20, 30)
        // Only week 30 falls within [26, 30], so fallback returns the last 2 points.
        assertEquals(weeklyPoints(20, 30), HistoryWindow.recent(points))
    }

    @Test
    fun `canZoom is false when all points fit in recent and true when older history exists`() {
        assertFalse(HistoryWindow.canZoom(emptyList()))
        assertFalse(HistoryWindow.canZoom(weeklyPoints(0, 1, 2, 3, 4)))
        assertFalse(HistoryWindow.canZoom(weeklyPoints(0, 10)))
        assertTrue(HistoryWindow.canZoom(weeklyPoints(0, 1, 5, 6)))
        assertTrue(HistoryWindow.canZoom(weeklyPoints(0, 10, 20)))
    }

    @Test
    fun `spansYears switches at 52 weeks`() {
        assertFalse(HistoryWindow.spansYears(emptyList()))
        assertFalse(HistoryWindow.spansYears(weeklyPoints(0, 51)))
        assertTrue(HistoryWindow.spansYears(weeklyPoints(0, 52)))
        assertTrue(HistoryWindow.spansYears(weeklyPoints(0, 60)))
    }

    @Test
    fun `xAxisLabelDays labels every week up to MAX_X_LABELS weeks`() {
        val points = weeklyPoints(0, 1, 2, 3, 4, 5)
        assertEquals(points.map { it.epochDay }, HistoryWindow.xAxisLabelDays(points))
    }

    @Test
    fun `xAxisLabelDays thins by calendar week span anchored at newest week even for sparse points`() {
        // Week 0 plus weeks 17..20 (5 points spanning 21 weeks -> weekSpan = 20, spacing = 4, offset = 0).
        val sparseSpan = weeklyPoints(0, 17, 18, 19, 20)
        val ticks = HistoryWindow.xAxisLabelDays(sparseSpan)
        assertTrue(ticks.size <= HistoryWindow.MAX_X_LABELS)
        assertEquals(listOf(0, 4, 8, 12, 16, 20).map { 100L + 7L * it }, ticks)
        assertEquals(sparseSpan.last().epochDay, ticks.last())
    }
}
