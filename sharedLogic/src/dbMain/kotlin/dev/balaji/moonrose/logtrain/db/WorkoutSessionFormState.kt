package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.model.Exercise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DraftSessionSet(
    // Snapshots shown as placeholder to the user (never mutated by the UI).
    val targetWeight: String = "",
    val targetReps: String = "",
    val actualWeight: String = "",
    val actualReps: String = "",
    val completed: Boolean = false,
)

data class DraftSessionExercise(
    val id: Long,
    val exercise: Exercise,
    val sets: List<DraftSessionSet> = listOf(DraftSessionSet()),
)

data class WorkoutSessionUiState(
    val loading: Boolean = true,
    val exercises: List<DraftSessionExercise> = emptyList(),
    val saving: Boolean = false,
    val finished: Boolean = false,
    val discarded: Boolean = false,
)

class WorkoutSessionFormState(private val sessionId: Long) {
    private val _uiState = MutableStateFlow(WorkoutSessionUiState())
    val uiState: StateFlow<WorkoutSessionUiState> = _uiState.asStateFlow()
    private var nextId = 0L

    init {
        CoroutineScope(Dispatchers.Main).launch {
            val details = AppDb.db.workoutSessionDetailDao().getForSession(sessionId)
            // Details arrive in flat position order with sets of the same
            // exercise adjacent (invariant maintained by the creator + saver).
            val grouped = mutableListOf<DraftSessionExercise>()
            var currentExerciseId = -1L
            var currentSets = mutableListOf<DraftSessionSet>()
            var currentExercise: Exercise? = null
            fun flush() {
                val ex = currentExercise ?: return
                grouped.add(
                    DraftSessionExercise(
                        id = nextId++,
                        exercise = ex,
                        sets = currentSets.toList().ifEmpty { listOf(DraftSessionSet()) },
                    ),
                )
            }
            for (detail in details) {
                if (detail.exerciseId != currentExerciseId) {
                    flush()
                    currentExercise = ExerciseRepository.findById(detail.exerciseId)
                    currentExerciseId = detail.exerciseId
                    currentSets = mutableListOf()
                }
                currentSets.add(
                    DraftSessionSet(
                        targetWeight = detail.targetWeight?.formatWeight().orEmpty(),
                        targetReps = detail.targetReps?.toString().orEmpty(),
                        actualWeight = detail.actualWeight?.formatWeight().orEmpty(),
                        actualReps = detail.actualReps?.toString().orEmpty(),
                        completed = detail.completed,
                    ),
                )
            }
            flush()
            _uiState.value = _uiState.value.copy(loading = false, exercises = grouped)
        }
    }

    fun addExercise(exercise: Exercise) {
        val current = _uiState.value
        _uiState.value = current.copy(
            exercises = current.exercises + DraftSessionExercise(id = nextId++, exercise = exercise),
        )
    }

    fun addSet(exerciseIndex: Int) {
        updateExercise(exerciseIndex) { ex -> ex.copy(sets = ex.sets + DraftSessionSet()) }
    }

    fun removeSet(exerciseIndex: Int, setIndex: Int) {
        updateExercise(exerciseIndex) { ex ->
            ex.copy(sets = ex.sets.filterIndexed { i, _ -> i != setIndex })
        }
    }

    fun removeExercise(exerciseIndex: Int) {
        val current = _uiState.value
        _uiState.value = current.copy(
            exercises = current.exercises.filterIndexed { i, _ -> i != exerciseIndex },
        )
    }

    fun moveExercise(from: Int, to: Int) {
        val current = _uiState.value
        val list = current.exercises.toMutableList()
        val item = list.removeAt(from)
        list.add(to.coerceIn(0, list.size), item)
        _uiState.value = current.copy(exercises = list)
    }

    fun updateActualWeight(exerciseIndex: Int, setIndex: Int, value: String) {
        updateSet(exerciseIndex, setIndex) { it.copy(actualWeight = value) }
    }

    fun updateActualReps(exerciseIndex: Int, setIndex: Int, value: String) {
        updateSet(exerciseIndex, setIndex) { it.copy(actualReps = value) }
    }

    fun toggleCompleted(exerciseIndex: Int, setIndex: Int) {
        updateSet(exerciseIndex, setIndex) { it.copy(completed = !it.completed) }
    }

    /** Rewrite-on-save mirroring RoutineFormState — simpler than diffing. */
    fun save(onSaved: () -> Unit = {}) {
        val state = _uiState.value
        _uiState.value = state.copy(saving = true)
        CoroutineScope(Dispatchers.Main).launch {
            persist(state)
            _uiState.value = _uiState.value.copy(saving = false)
            onSaved()
        }
    }

    fun finish(onDone: () -> Unit = {}) {
        val state = _uiState.value
        _uiState.value = state.copy(saving = true)
        CoroutineScope(Dispatchers.Main).launch {
            persist(state)
            WorkoutSessionRepository.finish(sessionId)
            _uiState.value = _uiState.value.copy(saving = false, finished = true)
            onDone()
        }
    }

    fun discard(onDone: () -> Unit = {}) {
        _uiState.value = _uiState.value.copy(saving = true)
        CoroutineScope(Dispatchers.Main).launch {
            WorkoutSessionRepository.discard(sessionId)
            _uiState.value = _uiState.value.copy(saving = false, discarded = true)
            onDone()
        }
    }

    private suspend fun persist(state: WorkoutSessionUiState) {
        val now = nowMillis()
        AppDb.db.workoutSessionDetailDao().deleteForSession(sessionId)
        var position = 1
        state.exercises.forEach { draft ->
            val sets = if (draft.sets.isEmpty()) listOf(DraftSessionSet()) else draft.sets
            sets.forEach { set ->
                AppDb.db.workoutSessionDetailDao().insert(
                    WorkoutSessionDetailEntity(
                        workoutSessionId = sessionId,
                        workSetDetailId = null,
                        exerciseId = draft.exercise.id,
                        position = position++,
                        targetWeight = set.targetWeight.toDoubleOrNull(),
                        targetReps = set.targetReps.toIntOrNull(),
                        targetDuration = null,
                        actualWeight = set.actualWeight.toDoubleOrNull(),
                        actualReps = set.actualReps.toIntOrNull(),
                        actualDuration = null,
                        completed = set.completed,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    private fun updateExercise(index: Int, transform: (DraftSessionExercise) -> DraftSessionExercise) {
        val current = _uiState.value
        _uiState.value = current.copy(
            exercises = current.exercises.mapIndexed { i, ex -> if (i == index) transform(ex) else ex },
        )
    }

    private fun updateSet(
        exerciseIndex: Int,
        setIndex: Int,
        transform: (DraftSessionSet) -> DraftSessionSet,
    ) {
        updateExercise(exerciseIndex) { ex ->
            ex.copy(sets = ex.sets.mapIndexed { i, s -> if (i == setIndex) transform(s) else s })
        }
    }
}

private fun Double.formatWeight(): String =
    if (this == toLong().toDouble()) toLong().toString() else toString()
