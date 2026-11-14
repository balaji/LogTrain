package dev.balaji.moonrose.trainfree.calendar

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.todayIn

/**
 * Which weekday a calendar week is laid out from. Persisted as the lowercase
 * [value] string in user_settings.
 */
enum class WeekStart(val value: String) {
    SUNDAY("sunday"),
    MONDAY("monday"),
    ;

    companion object {
        fun fromValue(value: String?): WeekStart = entries.firstOrNull { it.value == value } ?: SUNDAY
    }
}

/**
 * Calendar models are primitive-typed (Long/Int/Boolean/String) on purpose:
 * they cross the SKIE bridge to SwiftUI, where kotlinx-datetime types and
 * boxed collection elements would be awkward to consume.
 */
data class CalendarDay(
    val epochDay: Long,
    val dayOfMonth: Int,
    /** Month (1-12) of this day's date; UIs label the 1st of each month with its localized abbreviation. */
    val monthOfYear: Int,
    val isToday: Boolean,
    val isFuture: Boolean,
    val hasCompletedWorkout: Boolean,
    /** Saturday or Sunday, regardless of the configured week start. */
    val isWeekend: Boolean,
)

data class CalendarWeek(
    val weekStartEpochDay: Long,
    /** Week 1 is the week containing Jan 1, per the configured [WeekStart]. */
    val weekNumber: Int,
    /** Always 7 days, in [WeekStart] order. */
    val days: List<CalendarDay>,
)

data class CalendarModel(
    val weeks: List<CalendarWeek>,
    /** Index into [weeks] of the week containing today. */
    val currentWeekIndex: Int,
)

object WeekMath {
    @OptIn(ExperimentalTime::class)
    fun todayEpochDay(): Long =
        Clock.System.todayIn(TimeZone.currentSystemDefault()).toEpochDays()

    fun weekStartOnOrBefore(epochDay: Long, weekStart: WeekStart = WeekStart.SUNDAY): Long =
        epochDay - weekDayIndex(LocalDate.fromEpochDays(epochDay), weekStart)

    fun weekNumber(epochDay: Long, weekStart: WeekStart = WeekStart.SUNDAY): Int {
        val date = LocalDate.fromEpochDays(epochDay)
        val jan1Index = weekDayIndex(LocalDate(date.year, 1, 1), weekStart)
        return (date.dayOfYear + jan1Index - 1) / 7 + 1
    }

    /**
     * Continuous weeks, starting on [weekStart], covering
     * [startEpochDay]..[endEpochDay]. A week straddling New Year is labeled
     * from its last day, so the row containing Jan 1 shows the new year's
     * week 1.
     */
    fun buildWeeks(
        startEpochDay: Long,
        endEpochDay: Long,
        completedDays: Set<Long>,
        todayEpochDay: Long,
        weekStart: WeekStart = WeekStart.SUNDAY,
    ): List<CalendarWeek> {
        val weeks = mutableListOf<CalendarWeek>()
        var weekStartDay = weekStartOnOrBefore(startEpochDay, weekStart)
        while (weekStartDay <= endEpochDay) {
            val days = (0L..6L).map { offset ->
                val epochDay = weekStartDay + offset
                val date = LocalDate.fromEpochDays(epochDay)
                CalendarDay(
                    epochDay = epochDay,
                    dayOfMonth = date.day,
                    monthOfYear = date.month.number,
                    isToday = epochDay == todayEpochDay,
                    isFuture = epochDay > todayEpochDay,
                    hasCompletedWorkout = epochDay in completedDays,
                    isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY,
                )
            }
            weeks += CalendarWeek(
                weekStartEpochDay = weekStartDay,
                weekNumber = weekNumber(weekStartDay + 6, weekStart),
                days = days,
            )
            weekStartDay += 7
        }
        return weeks
    }

    /** Day index within the week: [weekStart]'s day is 0, wrapping up to 6. */
    private fun weekDayIndex(date: LocalDate, weekStart: WeekStart): Int =
        when (weekStart) {
            WeekStart.SUNDAY -> date.dayOfWeek.isoDayNumber % 7
            WeekStart.MONDAY -> date.dayOfWeek.isoDayNumber - 1
        }

    /**
     * The calendar components of [epochDay], for UI layers that need to
     * format a date as localized text without a kotlinx-datetime dependency.
     */
    fun dayParts(epochDay: Long): DayParts {
        val date = LocalDate.fromEpochDays(epochDay)
        return DayParts(year = date.year, monthOfYear = date.month.number, dayOfMonth = date.day)
    }
}

/** Calendar components of one day; primitive-typed like the other calendar models. */
data class DayParts(
    val year: Int,
    /** 1-12. */
    val monthOfYear: Int,
    val dayOfMonth: Int,
)
