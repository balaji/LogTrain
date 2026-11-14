package dev.balaji.moonrose.trainfree.db

import dev.balaji.moonrose.trainfree.calendar.WeekMath
import dev.balaji.moonrose.trainfree.insights.ExerciseHistoryUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * Reactive history data for the Exercise Detail screen's chart section.
 * Observes completed sets for [exerciseId] and produces pre-aggregated
 * weekly series for all applicable metrics; the UI picks which to display.
 * Series span from the exercise's first completed set to the current week
 * (empty weeks are 0.0), so the UI can offer both an all-history view and a
 * recent-weeks zoom (see `HistoryWindow`).
 */
class ExerciseHistoryState(private val exerciseId: Long) {
    private val _uiState = MutableStateFlow(ExerciseHistoryUiState())
    val uiState: StateFlow<ExerciseHistoryUiState> = _uiState.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        scope.launch {
            combine(
                AppDb.db.workoutSessionDetailDao()
                    .observeCompletedSetsForExercise(PROFILE_ID, exerciseId, 0L),
                UserSettingsRepository.settings,
            ) { rows, settings ->
                val zone = TimeZone.currentSystemDefault()
                val todayDay = WeekMath.todayEpochDay()
                val sets = rows.map { it.toSetPoint(zone).convertedTo(settings.weightUnit) }

                val currentWeekStart = WeekMath.weekStartOnOrBefore(todayDay, settings.weekStart)
                val weeks = sets.minOfOrNull { it.epochDay }
                    ?.let { earliestDay ->
                        val firstWeekStart = WeekMath.weekStartOnOrBefore(earliestDay, settings.weekStart)
                        (((currentWeekStart - firstWeekStart) / 7L).toInt() + 1).coerceAtLeast(1)
                    }
                    ?: 1

                val oneRm = singleExerciseWeeklyOneRm(sets, weeks, todayDay, settings.weekStart, settings.oneRmFormula)
                val volume = singleExerciseWeeklyVolume(sets, weeks, todayDay, settings.weekStart)
                val duration = singleExerciseWeeklyDuration(sets, weeks, todayDay, settings.weekStart)
                val distance = singleExerciseWeeklyDistance(sets, weeks, todayDay, settings.weekStart)

                ExerciseHistoryUiState(
                    loading = false,
                    weeklyOneRm = oneRm,
                    weeklyVolume = volume,
                    weeklyDuration = duration,
                    weeklyDistance = distance,
                    hasAnyData = (oneRm + volume + duration + distance).any { it.value > 0.0 },
                    weightUnit = settings.weightUnit,
                )
            }.collect { _uiState.value = it }
        }
    }
}
