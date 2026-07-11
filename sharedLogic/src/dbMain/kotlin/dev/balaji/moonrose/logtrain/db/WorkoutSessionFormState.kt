package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.model.Exercise
import dev.balaji.moonrose.logtrain.model.PlateMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class DraftSessionSet(
    // Snapshots shown as placeholder to the user (never mutated by the UI).
    val targetWeight: String = "",
    val targetReps: String = "",
    val actualWeight: String = "",
    val actualReps: String = "",
    val warmup: Boolean = false,
    val completed: Boolean = false,
)

data class DraftSessionExercise(
    val id: Long,
    val exercise: Exercise,
    val sets: List<DraftSessionSet> = listOf(DraftSessionSet()),
    /**
     * What the user logged the last time they did this exercise, formatted
     * ("50kg x 5") and in set order. Row N of the current session shows entry
     * N; rows beyond the list show a placeholder.
     */
    val previousSets: List<String> = emptyList(),
) {
    /**
     * Whether weight cells offer the plate keyboard. Barbell only for now —
     * deliberately excludes "smith machine", whose bars vary too much.
     */
    val usesPlateKeyboard: Boolean get() = exercise.equipment == "barbell"
}

data class WorkoutSessionUiState(
    val loading: Boolean = true,
    val exercises: List<DraftSessionExercise> = emptyList(),
    val saving: Boolean = false,
    val finished: Boolean = false,
    val discarded: Boolean = false,
    // Epoch millis the session was started; 0 while still loading.
    val startedAt: Long = 0L,
) {
    /** Finishing is only allowed once at least one set is checked off. */
    val hasCompletedSets: Boolean
        get() = exercises.any { ex -> ex.sets.any { it.completed } }
}

class WorkoutSessionFormState(private val sessionId: Long) {
    private val _uiState = MutableStateFlow(WorkoutSessionUiState())
    val uiState: StateFlow<WorkoutSessionUiState> = _uiState.asStateFlow()
    private var nextId = 0L
    private val scope = CoroutineScope(Dispatchers.Main)

    // Serializes persist() runs so a debounced autosave and an explicit
    // save/finish can never interleave their delete+insert rewrites.
    private val persistMutex = Mutex()
    private var autosaveJob: Job? = null

    // Set once the session is finished or discarded; stops any still-pending
    // autosave from resurrecting rows for a session that no longer exists.
    private var closed = false

    init {
        scope.launch {
            val session = AppDb.db.workoutSessionDao().findById(sessionId)
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
                        warmup = detail.warmup,
                        completed = detail.completed,
                    ),
                )
            }
            flush()
            val withPrevious = grouped.map { draft ->
                draft.copy(previousSets = loadPreviousSets(draft.exercise.id))
            }
            _uiState.value = _uiState.value.copy(
                loading = false,
                exercises = withPrevious,
                startedAt = session?.performedAt ?: 0L,
            )
        }
    }

    fun addExercise(exercise: Exercise) {
        val draftId = nextId++
        val current = _uiState.value
        _uiState.value = current.copy(
            exercises = current.exercises + DraftSessionExercise(id = draftId, exercise = exercise),
        )
        scheduleAutosave()
        // Previous values come from the db; fill them in once loaded.
        scope.launch {
            val previous = loadPreviousSets(exercise.id)
            if (previous.isEmpty()) return@launch
            val state = _uiState.value
            _uiState.value = state.copy(
                exercises = state.exercises.map {
                    if (it.id == draftId) it.copy(previousSets = previous) else it
                },
            )
        }
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
        scheduleAutosave()
    }

    fun moveExercise(from: Int, to: Int) {
        val current = _uiState.value
        val list = current.exercises.toMutableList()
        val item = list.removeAt(from)
        list.add(to.coerceIn(0, list.size), item)
        _uiState.value = current.copy(exercises = list)
        scheduleAutosave()
    }

    fun updateActualWeight(exerciseIndex: Int, setIndex: Int, value: String) {
        val weight = value.filterDecimal()
        updateSet(exerciseIndex, setIndex) { it.copy(actualWeight = weight) }
    }

    fun updateActualReps(exerciseIndex: Int, setIndex: Int, value: String) {
        val reps = value.filterDigits()
        updateSet(exerciseIndex, setIndex) {
            // A set only counts as completed while it has at least one rep.
            it.copy(actualReps = reps, completed = it.completed && reps.hasReps())
        }
    }

    fun toggleWarmup(exerciseIndex: Int, setIndex: Int) {
        updateSet(exerciseIndex, setIndex) { it.copy(warmup = !it.warmup) }
    }

    fun toggleCompleted(exerciseIndex: Int, setIndex: Int) {
        updateSet(exerciseIndex, setIndex) {
            it.copy(completed = !it.completed && it.actualReps.hasReps())
        }
    }

    /** Rewrite-on-save mirroring RoutineFormState — simpler than diffing. */
    fun save(onSaved: () -> Unit = {}) {
        val state = _uiState.value
        _uiState.value = state.copy(saving = true)
        autosaveJob?.cancel()
        scope.launch {
            persistMutex.withLock { persist(state) }
            _uiState.value = _uiState.value.copy(saving = false)
            onSaved()
        }
    }

    fun finish(onDone: () -> Unit = {}) {
        val state = _uiState.value
        _uiState.value = state.copy(saving = true)
        closed = true
        autosaveJob?.cancel()
        scope.launch {
            persistMutex.withLock {
                persist(state)
                WorkoutSessionRepository.finish(sessionId)
            }
            _uiState.value = _uiState.value.copy(saving = false, finished = true)
            onDone()
        }
    }

    fun discard(onDone: () -> Unit = {}) {
        _uiState.value = _uiState.value.copy(saving = true)
        closed = true
        autosaveJob?.cancel()
        scope.launch {
            persistMutex.withLock { WorkoutSessionRepository.discard(sessionId) }
            _uiState.value = _uiState.value.copy(saving = false, discarded = true)
            onDone()
        }
    }

    /**
     * Edits are flushed to the database shortly after every mutation so they
     * survive navigating away (the screen's remembered state is lost once it
     * leaves composition) and so the mini bar's finish action operates on
     * current data. Debounced to avoid a full rewrite per keystroke; the
     * persist itself is NonCancellable so a newer autosave can only supersede
     * an older one during the delay, never mid-rewrite.
     */
    private fun scheduleAutosave() {
        if (closed || _uiState.value.loading) return
        autosaveJob?.cancel()
        autosaveJob = scope.launch {
            delay(AUTOSAVE_DEBOUNCE_MS)
            val state = _uiState.value
            withContext(NonCancellable) {
                persistMutex.withLock {
                    if (!closed) persist(state)
                }
            }
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
                        warmup = set.warmup,
                        completed = set.completed,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    private suspend fun loadPreviousSets(exerciseId: Long): List<String> =
        AppDb.db.workoutSessionDetailDao()
            .getLatestCompletedSets(PROFILE_ID, exerciseId)
            .map { row ->
                val weight = row.actualWeight?.formatWeight()
                val reps = row.actualReps
                when {
                    weight != null && reps != null -> "${weight}kg x $reps"
                    weight != null -> "${weight}kg"
                    reps != null -> "x $reps"
                    // Blank keeps the list positionally aligned; the UI shows
                    // its placeholder for blank entries.
                    else -> ""
                }
            }

    private fun updateExercise(index: Int, transform: (DraftSessionExercise) -> DraftSessionExercise) {
        val current = _uiState.value
        _uiState.value = current.copy(
            exercises = current.exercises.mapIndexed { i, ex -> if (i == index) transform(ex) else ex },
        )
        scheduleAutosave()
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

private const val AUTOSAVE_DEBOUNCE_MS = 500L

private fun String.hasReps(): Boolean = (toIntOrNull() ?: 0) > 0

private fun Double.formatWeight(): String = PlateMath.formatWeight(this)
