package dev.balaji.moonrose.trainfree.db

import dev.balaji.moonrose.trainfree.calendar.CalendarModel
import dev.balaji.moonrose.trainfree.calendar.WeekMath
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * One calendar day with at least one completed session, for the workout
 * history list. Primitive-typed for the SKIE bridge, like the calendar models.
 */
data class CompletedWorkoutDay(
    val epochDay: Long,
    /** Routine names in session order; "Workout" for sessions without a routine. */
    val routineNames: List<String>,
)

object CalendarRepository {
    /**
     * Weekly calendar of completed workout sessions: continuous weeks (start
     * day per the user_settings week-start) from min(earliest completed
     * session, one year ago) through four weeks from now. Days are keyed on
     * performed_at (session start), in the device timezone at emission time.
     */
    @OptIn(ExperimentalTime::class)
    val calendar: Flow<CalendarModel> =
        combine(
            AppDb.db.workoutSessionDao().observeCompletedPerformedAt(PROFILE_ID),
            UserSettingsRepository.settings,
        ) { performedAt, settings ->
            val weekStart = settings.weekStart
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

    /**
     * Days with completed sessions, most recent first, keyed on performed_at
     * in the device timezone like [calendar]. The query returns sessions
     * newest-first and groupBy preserves encounter order, so no re-sort is
     * needed; names within a day are flipped back to chronological order.
     */
    @OptIn(ExperimentalTime::class)
    val completedWorkoutDays: Flow<List<CompletedWorkoutDay>> =
        AppDb.db.workoutSessionDao().observeCompletedWithRoutineName(PROFILE_ID).map { rows ->
            val zone = TimeZone.currentSystemDefault()
            rows
                .groupBy { row ->
                    Instant.fromEpochMilliseconds(row.performedAt).toLocalDateTime(zone).date.toEpochDays()
                }
                .map { (epochDay, sessions) ->
                    CompletedWorkoutDay(
                        epochDay = epochDay,
                        routineNames = sessions.asReversed().map { it.routineName ?: "Workout" },
                    )
                }
        }
}
