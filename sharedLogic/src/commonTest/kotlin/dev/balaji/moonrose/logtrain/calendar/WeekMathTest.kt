package dev.balaji.moonrose.logtrain.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class WeekMathTest {
    private fun epochDay(year: Int, month: Int, day: Int): Long =
        LocalDate(year, month, day).toEpochDays()

    @Test
    fun weekStartOnOrBefore_returnsSameDayForSunday() {
        val sunday = epochDay(2026, 7, 5)
        assertEquals(sunday, WeekMath.weekStartOnOrBefore(sunday))
    }

    @Test
    fun weekStartOnOrBefore_returnsPrecedingSundayForEveryWeekday() {
        val sunday = epochDay(2026, 7, 5)
        for (offset in 0L..6L) {
            assertEquals(sunday, WeekMath.weekStartOnOrBefore(sunday + offset))
        }
    }

    @Test
    fun weekStartOnOrBefore_returnsSameDayForMonday() {
        val monday = epochDay(2026, 7, 6)
        assertEquals(monday, WeekMath.weekStartOnOrBefore(monday, WeekStart.MONDAY))
    }

    @Test
    fun weekStartOnOrBefore_returnsPrecedingMondayForEveryWeekday() {
        val monday = epochDay(2026, 7, 6)
        for (offset in 0L..6L) {
            assertEquals(monday, WeekMath.weekStartOnOrBefore(monday + offset, WeekStart.MONDAY))
        }
    }

    @Test
    fun weekNumber_jan1IsAlwaysWeek1() {
        for (year in 2020..2030) {
            assertEquals(1, WeekMath.weekNumber(epochDay(year, 1, 1)), "Jan 1 $year")
            assertEquals(1, WeekMath.weekNumber(epochDay(year, 1, 1), WeekStart.MONDAY), "Jan 1 $year (Monday start)")
        }
    }

    @Test
    fun weekNumber_july6_2026_isWeek28() {
        assertEquals(28, WeekMath.weekNumber(epochDay(2026, 7, 6)))
    }

    @Test
    fun buildWeeks_yearStraddlingWeekLabeledAsNewYearsWeek1() {
        // Sun Dec 28 2025 .. Sat Jan 3 2026 straddles New Year.
        val weeks = WeekMath.buildWeeks(
            startEpochDay = epochDay(2025, 12, 28),
            endEpochDay = epochDay(2026, 1, 3),
            completedDays = emptySet(),
            todayEpochDay = epochDay(2026, 1, 2),
        )
        assertEquals(1, weeks.size)
        assertEquals(1, weeks[0].weekNumber)
    }

    @Test
    fun buildWeeks_producesContinuousSevenDayWeeks() {
        val start = epochDay(2026, 6, 7) // a Sunday
        val end = epochDay(2026, 7, 4) // a Saturday, 4 weeks later
        val weeks = WeekMath.buildWeeks(start, end, emptySet(), epochDay(2026, 7, 1))
        assertEquals(4, weeks.size)
        var expectedDay = start
        for (week in weeks) {
            assertEquals(expectedDay, week.weekStartEpochDay)
            assertEquals(7, week.days.size)
            for (day in week.days) {
                assertEquals(expectedDay, day.epochDay)
                expectedDay++
            }
        }
    }

    @Test
    fun buildWeeks_flagsCompletedTodayAndFutureDays() {
        val today = epochDay(2026, 7, 6)
        val completed = setOf(epochDay(2026, 7, 5))
        val weeks = WeekMath.buildWeeks(
            startEpochDay = epochDay(2026, 7, 5),
            endEpochDay = epochDay(2026, 7, 11),
            completedDays = completed,
            todayEpochDay = today,
        )
        val days = weeks.single().days
        assertTrue(days[0].hasCompletedWorkout)
        assertTrue(days[1].isToday)
        assertTrue(days.drop(2).all { it.isFuture })
        assertTrue(days.take(2).none { it.isFuture })
    }

    @Test
    fun buildWeeks_daysCarryTheirMonthAcrossAMonthBoundary() {
        // Week containing Jul 1 2026 (a Wednesday): Sun Jun 28 .. Sat Jul 4.
        val weeks = WeekMath.buildWeeks(
            startEpochDay = epochDay(2026, 6, 28),
            endEpochDay = epochDay(2026, 7, 4),
            completedDays = emptySet(),
            todayEpochDay = epochDay(2026, 7, 1),
        )
        val days = weeks.single().days
        assertEquals(listOf(6, 6, 6, 7, 7, 7, 7), days.map { it.monthOfYear })
        // The UI labels the month on the 1st; only Jul 1 qualifies in this week.
        assertEquals(listOf(3), days.withIndex().filter { it.value.dayOfMonth == 1 }.map { it.index })
    }

    @Test
    fun dayParts_exposeCalendarComponents() {
        assertEquals(DayParts(year = 2026, monthOfYear = 7, dayOfMonth = 6), WeekMath.dayParts(epochDay(2026, 7, 6)))
        assertEquals(DayParts(year = 2025, monthOfYear = 12, dayOfMonth = 31), WeekMath.dayParts(epochDay(2025, 12, 31)))
    }

    @Test
    fun buildWeeks_flagsSaturdayAndSundayAsWeekend() {
        // Sun Jul 5 .. Sat Jul 11 2026.
        val weeks = WeekMath.buildWeeks(
            startEpochDay = epochDay(2026, 7, 5),
            endEpochDay = epochDay(2026, 7, 11),
            completedDays = emptySet(),
            todayEpochDay = epochDay(2026, 7, 5),
        )
        val days = weeks.single().days
        assertTrue(days[0].isWeekend) // Sun
        assertTrue(days.subList(1, 6).none { it.isWeekend }) // Mon-Fri
        assertTrue(days[6].isWeekend) // Sat
    }

    @Test
    fun buildWeeks_mondayStart_ordersDaysMondayThroughSunday() {
        // Mon Jul 6 .. Sun Jul 12 2026.
        val weeks = WeekMath.buildWeeks(
            startEpochDay = epochDay(2026, 7, 6),
            endEpochDay = epochDay(2026, 7, 12),
            completedDays = emptySet(),
            todayEpochDay = epochDay(2026, 7, 6),
            weekStart = WeekStart.MONDAY,
        )
        val days = weeks.single().days
        assertEquals(epochDay(2026, 7, 6), weeks.single().weekStartEpochDay)
        assertTrue(days.subList(0, 5).none { it.isWeekend }) // Mon-Fri
        assertTrue(days[5].isWeekend) // Sat
        assertTrue(days[6].isWeekend) // Sun
    }
}
