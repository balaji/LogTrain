package dev.balaji.moonrose.logtrain.db

import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

data class DaySessionSet(
    val targetWeight: String,
    val targetReps: String,
    val actualWeight: String,
    val actualReps: String,
    val completed: Boolean,
)

data class DaySessionExercise(
    val name: String,
    val sets: List<DaySessionSet>,
)

data class DaySession(
    val id: Long,
    val routineName: String?,
    val exercises: List<DaySessionExercise>,
)

data class DayDetailUiState(
    val loading: Boolean = true,
    val sessions: List<DaySession> = emptyList(),
)

class DayDetailState(private val epochDay: Long) {
    private val _uiState = MutableStateFlow(DayDetailUiState())
    val uiState: StateFlow<DayDetailUiState> = _uiState.asStateFlow()

    init {
        CoroutineScope(Dispatchers.Main).launch {
            _uiState.value = DayDetailUiState(loading = false, sessions = load())
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun load(): List<DaySession> {
        val zone = TimeZone.currentSystemDefault()
        val date = LocalDate.fromEpochDays(epochDay)
        val startMillis = date.atStartOfDayIn(zone).toEpochMilliseconds()
        val endMillis = LocalDate.fromEpochDays(epochDay + 1).atStartOfDayIn(zone).toEpochMilliseconds()

        val sessions = AppDb.db.workoutSessionDao().getCompletedInRange(PROFILE_ID, startMillis, endMillis)
        return sessions.map { session ->
            val routine = session.routineId?.let { AppDb.db.routineDao().findById(it) }
            val details = AppDb.db.workoutSessionDetailDao().getForSession(session.id)
            DaySession(
                id = session.id,
                routineName = routine?.name,
                exercises = groupByExercise(details),
            )
        }
    }

    /** Details arrive in flat position order with sets of the same exercise adjacent. */
    private suspend fun groupByExercise(details: List<WorkoutSessionDetailEntity>): List<DaySessionExercise> {
        val grouped = mutableListOf<DaySessionExercise>()
        var currentExerciseId = -1L
        var currentName = ""
        var currentSets = mutableListOf<DaySessionSet>()
        fun flush() {
            if (currentSets.isNotEmpty()) grouped.add(DaySessionExercise(currentName, currentSets.toList()))
        }
        for (detail in details) {
            if (detail.exerciseId != currentExerciseId) {
                flush()
                currentExerciseId = detail.exerciseId
                currentName = ExerciseRepository.findById(detail.exerciseId)?.name.orEmpty()
                currentSets = mutableListOf()
            }
            currentSets.add(
                DaySessionSet(
                    targetWeight = detail.targetWeight?.formatDayDetailNumber().orEmpty(),
                    targetReps = detail.targetReps?.toString().orEmpty(),
                    actualWeight = detail.actualWeight?.formatDayDetailNumber().orEmpty(),
                    actualReps = detail.actualReps?.toString().orEmpty(),
                    completed = detail.completed,
                ),
            )
        }
        flush()
        return grouped
    }
}

private fun Double.formatDayDetailNumber(): String =
    if (this == toLong().toDouble()) toLong().toString() else toString()
