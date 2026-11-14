package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.calendar.WeekMath
import dev.balaji.moonrose.logtrain.insights.ExerciseHistoryUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

private const val HISTORY_WEEKS = 12

/**
 * Reactive history data for the Exercise Detail screen's chart section.
 * Observes completed sets for [exerciseId] and produces pre-aggregated
 * weekly series for all applicable metrics; the UI picks which to display.
 */
class ExerciseHistoryState(private val exerciseId: Long) {
    private val _uiState = MutableStateFlow(ExerciseHistoryUiState())
    val uiState: StateFlow<ExerciseHistoryUiState> = _uiState.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        val today = WeekMath.todayEpochDay()
        val firstWeekStartApprox = today - 7L * HISTORY_WEEKS
        val startMillis = startOfDayMillis(firstWeekStartApprox)
        scope.launch {
            combine(
                AppDb.db.workoutSessionDetailDao()
                    .observeCompletedSetsForExercise(PROFILE_ID, exerciseId, startMillis),
                UserSettingsRepository.settings,
            ) { rows, settings ->
                val zone = TimeZone.currentSystemDefault()
                val todayDay = WeekMath.todayEpochDay()
                val sets = rows.map { it.toSetPoint(zone).convertedTo(settings.weightUnit) }

                val oneRm = singleExerciseWeeklyOneRm(sets, HISTORY_WEEKS, todayDay, settings.weekStart, settings.oneRmFormula)
                val volume = singleExerciseWeeklyVolume(sets, HISTORY_WEEKS, todayDay, settings.weekStart)
                val duration = singleExerciseWeeklyDuration(sets, HISTORY_WEEKS, todayDay, settings.weekStart)
                val distance = singleExerciseWeeklyDistance(sets, HISTORY_WEEKS, todayDay, settings.weekStart)

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
