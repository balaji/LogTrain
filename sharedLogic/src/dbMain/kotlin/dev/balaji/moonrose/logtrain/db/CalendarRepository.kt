package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.calendar.CalendarModel
import dev.balaji.moonrose.logtrain.calendar.WeekMath
import dev.balaji.moonrose.logtrain.calendar.WeekStart
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object CalendarRepository {
    /**
     * Weekly calendar of completed workout sessions: continuous weeks (start
     * day per the profile's week-start setting) from min(earliest completed
     * session, one year ago) through four weeks from now. Days are keyed on
     * performed_at (session start), in the device timezone at emission time.
     */
    @OptIn(ExperimentalTime::class)
    val calendar: Flow<CalendarModel> =
        combine(
            AppDb.db.workoutSessionDao().observeCompletedPerformedAt(PROFILE_ID),
            ProfileRepository.profile,
        ) { performedAt, profile ->
            val weekStart = if (profile?.weekStartsMonday == true) WeekStart.MONDAY else WeekStart.SUNDAY
            val zone = TimeZone.currentSystemDefault()
            val completedDays = performedAt.mapTo(mutableSetOf()) { millis ->
                Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date.toEpochDays()
            }
            val today = WeekMath.todayEpochDay()
            val currentWeekStart = WeekMath.weekStartOnOrBefore(today, weekStart)
            val start = WeekMath.weekStartOnOrBefore(
                minOf(completedDays.minOrNull() ?: today, today - 364),
                weekStart,
            )
            val end = currentWeekStart + 7 * 4 + 6
            val weeks = WeekMath.buildWeeks(start, end, completedDays, today, weekStart)
            CalendarModel(
                weeks = weeks,
                currentWeekIndex = weeks.indexOfFirst { it.weekStartEpochDay == currentWeekStart },
            )
        }
}
