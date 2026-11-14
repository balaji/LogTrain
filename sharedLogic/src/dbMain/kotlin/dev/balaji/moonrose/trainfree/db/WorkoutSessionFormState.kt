package dev.balaji.moonrose.trainfree.db

import dev.balaji.moonrose.trainfree.model.Exercise
import dev.balaji.moonrose.trainfree.model.ExerciseType
import dev.balaji.moonrose.trainfree.model.SetField
import dev.balaji.moonrose.trainfree.model.SetType
import dev.balaji.moonrose.trainfree.model.WeightUnit
import dev.balaji.moonrose.trainfree.model.isSetComplete
import dev.balaji.moonrose.trainfree.model.requiredSetFields
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class DraftSessionSet(
    // Snapshots shown as placeholder to the user (never mutated by the UI).
    val targetWeight: String = "",
    val targetReps: String = "",
    // Single h:mm box text, e.g. "1:30" or bare minutes "45".
    val targetDuration: String = "",
    val targetDistance: String = "",
    val actualWeight: String = "",
    val actualReps: String = "",
    val actualDuration: String = "",
    val actualDistance: String = "",
    val setType: SetType = SetType.NORMAL,
    val completed: Boolean = false,
    // Recorded numeric values + units so toggling units mid-workout converts
    // display strings uniformly without losing precision on untouched sets.
    internal val rawTargetWeight: Double? = null,
    internal val rawTargetDistance: Double? = null,
    internal val targetUnit: WeightUnit = WeightUnit.METRIC,
    internal val rawActualWeight: Double? = null,
    internal val rawActualDistance: Double? = null,
    internal val actualUnit: WeightUnit = WeightUnit.METRIC,
)

internal fun DraftSessionSet.formattedFor(unit: WeightUnit): DraftSessionSet = copy(
    targetWeight = rawTargetWeight?.let { WeightUnit.formatWeight(it, targetUnit, unit) } ?: targetWeight,
    targetDistance = rawTargetDistance?.let { WeightUnit.formatDistance(it, targetUnit, unit) } ?: targetDistance,
    actualWeight = rawActualWeight?.let { WeightUnit.formatWeight(it, actualUnit, unit) } ?: actualWeight,
    actualDistance = rawActualDistance?.let { WeightUnit.formatDistance(it, actualUnit, unit) } ?: actualDistance,
)

/** Whether the logged values satisfy every field [type] requires. */
private fun DraftSessionSet.meetsRequirements(type: ExerciseType): Boolean =
    type.isSetComplete(
        reps = actualReps.toIntOrNull(),
        durationSeconds = parseDurationSeconds(actualDuration),
        distance = rawActualDistance ?: actualDistance.toDoubleOrNull(),
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
    internal val previousSetRows: List<PreviousSetRow> = emptyList(),
) {
    /**
     * Whether weight cells offer the plate keyboard. Barbell only for now —
     * deliberately excludes "smith machine", whose bars vary too much.
     */
    val usesPlateKeyboard: Boolean get() = exercise.equipment == "barbell"

    /** Which measured fields this exercise's sets take; unrecognized types fall back to strength (reps). */
    val type: ExerciseType get() = ExerciseType.fromValue(exercise.exerciseType) ?: ExerciseType.STRENGTH

    /**
     * Whether the set at [setIndex] holds everything [type] requires and may
     * be marked completed. Shared by the Compose and SwiftUI checkboxes so
     * the gating rule lives in one place.
     */
    fun isSetReadyToComplete(setIndex: Int): Boolean =
        sets.getOrNull(setIndex)?.meetsRequirements(type) ?: false
}

/**
 * Tracks the rest timer running under the most recently completed set.
 * Only one rest timer runs at a time; checking another set restarts it under
 * the new set, and unchecking or deleting the owning set cancels it.
 */
data class RestTimerTarget(
    val exerciseId: Long,
    val setIndex: Int,
    val startedAtMillis: Long,
    val endsAtMillis: Long,
) {
    /** Total duration of this timer run in seconds, derived from its endpoints. */
    val durationSeconds: Long
        get() = ((endsAtMillis - startedAtMillis) / 1000L).coerceAtLeast(1L)
}

const val REST_TIMER_DURATION_SECONDS = 120L
const val REST_TIMER_DURATION_MS = REST_TIMER_DURATION_SECONDS * 1000L

data class WorkoutSessionUiState(
    val loading: Boolean = true,
    val exercises: List<DraftSessionExercise> = emptyList(),
    val saving: Boolean = false,
    val finished: Boolean = false,
    val discarded: Boolean = false,
    // Epoch millis the session was started; 0 while still loading.
    val startedAt: Long = 0L,
    val restTimer: RestTimerTarget? = null,
) {
    /** Finishing is only allowed once at least one set is checked off. */
    val hasCompletedSets: Boolean
        get() = exercises.any { ex -> ex.sets.any { it.completed } }

    /** Whether the rest timer is currently attached to [exerciseId]'s [setIndex]. */
    fun isRestTimerOn(exerciseId: Long, setIndex: Int): Boolean =
        restTimer?.let { it.exerciseId == exerciseId && it.setIndex == setIndex } == true

    internal fun removeSetAt(exerciseIndex: Int, setIndex: Int): WorkoutSessionUiState {
        val targetExId = exercises.getOrNull(exerciseIndex)?.id
        val nextTimer = restTimer?.let { timer ->
            if (timer.exerciseId != targetExId) {
                timer
            } else when {
                timer.setIndex == setIndex -> null
                timer.setIndex > setIndex -> timer.copy(setIndex = timer.setIndex - 1)
                else -> timer
            }
        }
        return copy(
            exercises = exercises.mapIndexed { i, ex ->
                if (i == exerciseIndex) {
                    ex.copy(sets = ex.sets.filterIndexed { idx, _ -> idx != setIndex })
                } else {
                    ex
                }
            },
            restTimer = nextTimer,
        )
    }

    internal fun removeExerciseAt(exerciseIndex: Int): WorkoutSessionUiState {
        val removedId = exercises.getOrNull(exerciseIndex)?.id
        return copy(
            exercises = exercises.filterIndexed { i, _ -> i != exerciseIndex },
            restTimer = restTimer?.takeIf { it.exerciseId != removedId },
        )
    }

    internal fun updateActualFieldAt(
        exerciseIndex: Int,
        setIndex: Int,
        transform: (DraftSessionSet) -> DraftSessionSet,
    ): WorkoutSessionUiState {
        val exercise = exercises.getOrNull(exerciseIndex) ?: return this
        val set = exercise.sets.getOrNull(setIndex) ?: return this
        val updated = transform(set)
        val nowCompleted = updated.completed && updated.meetsRequirements(exercise.type)
        val nextSet = updated.copy(completed = nowCompleted)
        val nextTimer = if (!nowCompleted && isRestTimerOn(exercise.id, setIndex)) {
            null
        } else {
            restTimer
        }
        return copy(
            exercises = exercises.mapIndexed { i, ex ->
                if (i == exerciseIndex) {
                    ex.copy(sets = ex.sets.mapIndexed { sIdx, s -> if (sIdx == setIndex) nextSet else s })
                } else {
                    ex
                }
            },
            restTimer = nextTimer,
        )
    }

    internal fun toggleCompletedAt(
        exerciseIndex: Int,
        setIndex: Int,
        now: Long = nowMillis(),
        durationSeconds: Long = REST_TIMER_DURATION_SECONDS,
    ): WorkoutSessionUiState {
        val exercise = exercises.getOrNull(exerciseIndex) ?: return this
        val set = exercise.sets.getOrNull(setIndex) ?: return this
        val nowCompleted = !set.completed && set.meetsRequirements(exercise.type)
        val nextTimer = when {
            nowCompleted -> RestTimerTarget(
                exerciseId = exercise.id,
                setIndex = setIndex,
                startedAtMillis = now,
                endsAtMillis = now + durationSeconds * 1000L,
            )
            isRestTimerOn(exercise.id, setIndex) -> null
            else -> restTimer
        }
        return copy(
            exercises = exercises.mapIndexed { i, ex ->
                if (i == exerciseIndex) {
                    ex.copy(
                        sets = ex.sets.mapIndexed { sIdx, s ->
                            if (sIdx == setIndex) s.copy(completed = nowCompleted) else s
                        },
                    )
                } else {
                    ex
                }
            },
            restTimer = nextTimer,
        )
    }

    internal fun withDisplayUnit(unit: WeightUnit): WorkoutSessionUiState = copy(
        exercises = exercises.map { ex ->
            ex.copy(
                sets = ex.sets.map { it.formattedFor(unit) },
                previousSets = if (ex.previousSetRows.isNotEmpty()) {
                    formatPreviousSets(ex.previousSetRows, ex.exercise, unit)
                } else {
                    ex.previousSets
                },
            )
        },
    )
}

class WorkoutSessionFormState(internal val sessionId: Long) {
    private val _uiState = MutableStateFlow(WorkoutSessionUiState())
    val uiState: StateFlow<WorkoutSessionUiState> = _uiState.asStateFlow()
    private var nextId = 0L
    private val scope = CoroutineScope(Dispatchers.Main)
    private var restTimerDurationSeconds: Long = REST_TIMER_DURATION_SECONDS
    private var currentWeightUnit: WeightUnit = WeightUnit.METRIC

    // Serializes persist() runs so a debounced autosave and an explicit
    // save/finish can never interleave their delete+insert rewrites.
    private val persistMutex = Mutex()
    private var autosaveJob: Job? = null
    private var settingsJob: Job? = null

    // Set once the session is finished or discarded; stops any still-pending
    // autosave from resurrecting rows for a session that no longer exists.
    private var closed = false
    internal val isClosed: Boolean get() = closed

    init {
        settingsJob = scope.launch {
            UserSettingsRepository.settings.collect { settings ->
                restTimerDurationSeconds = settings.restTimerDurationSeconds.toLong()
                val newUnit = settings.weightUnit
                if (newUnit != currentWeightUnit) {
                    currentWeightUnit = newUnit
                    if (!_uiState.value.loading) {
                        updateState(_uiState.value.withDisplayUnit(newUnit))
                    }
                }
            }
        }
        scope.launch {
            currentWeightUnit = UserSettingsRepository.settings.first().weightUnit
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
                        sets = currentSets.toList().ifEmpty {
                            listOf(DraftSessionSet(targetUnit = currentWeightUnit, actualUnit = currentWeightUnit))
                        },
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
                        targetReps = detail.targetReps?.toString().orEmpty(),
                        targetDuration = formatDurationText(detail.targetDuration),
                        actualReps = detail.actualReps?.toString().orEmpty(),
                        actualDuration = formatDurationText(detail.actualDuration),
                        setType = detail.setType,
                        completed = detail.completed,
                        rawTargetWeight = detail.targetWeight,
                        rawTargetDistance = detail.targetDistance,
                        targetUnit = detail.weightUnit,
                        rawActualWeight = detail.actualWeight,
                        rawActualDistance = detail.actualDistance,
                        actualUnit = detail.weightUnit,
                    ),
                )
            }
            flush()
            val withPrevious = grouped.map { draft ->
                val rows = loadPreviousSetRows(draft.exercise)
                draft.copy(
                    previousSetRows = rows,
                    previousSets = formatPreviousSets(rows, draft.exercise, currentWeightUnit),
                )
            }
            if (!closed) {
                updateState(
                    _uiState.value.copy(
                        loading = false,
                        exercises = withPrevious,
                        startedAt = session?.performedAt ?: 0L,
                    ).withDisplayUnit(currentWeightUnit),
                )
            }
        }
    }

    private fun updateState(newState: WorkoutSessionUiState) {
        _uiState.value = newState
        WorkoutSessionRepository.updateActiveRestTimer(newState.restTimer)
    }

    internal fun closeQuietly() {
        closed = true
        autosaveJob?.cancel()
        settingsJob?.cancel()
    }

    internal suspend fun flushBeforeExternalFinish() {
        val state = _uiState.value
        val wasLoading = state.loading
        closed = true
        autosaveJob?.cancel()
        settingsJob?.cancel()
        updateState(state.copy(saving = false, finished = true, restTimer = null))
        if (!wasLoading) {
            persistMutex.withLock { persist(state) }
        }
    }

    fun addExercise(exercise: Exercise) {
        val draftId = nextId++
        val current = _uiState.value
        val unit = currentWeightUnit
        updateState(
            current.copy(
                exercises = current.exercises + DraftSessionExercise(
                    id = draftId,
                    exercise = exercise,
                    sets = listOf(DraftSessionSet(targetUnit = unit, actualUnit = unit)),
                ),
            ),
        )
        scheduleAutosave()
        // Previous values come from the db; fill them in once loaded.
        scope.launch {
            val rows = loadPreviousSetRows(exercise)
            if (rows.isEmpty() || closed) return@launch
            val state = _uiState.value
            updateState(
                state.copy(
                    exercises = state.exercises.map {
                        if (it.id == draftId) {
                            it.copy(
                                previousSetRows = rows,
                                previousSets = formatPreviousSets(rows, exercise, currentWeightUnit),
                            )
                        } else {
                            it
                        }
                    },
                ),
            )
        }
    }

    fun addSet(exerciseIndex: Int) {
        val unit = currentWeightUnit
        updateExercise(exerciseIndex) { ex ->
            ex.copy(sets = ex.sets + DraftSessionSet(targetUnit = unit, actualUnit = unit))
        }
    }

    fun removeSet(exerciseIndex: Int, setIndex: Int) {
        updateState(_uiState.value.removeSetAt(exerciseIndex, setIndex))
        scheduleAutosave()
    }

    fun removeExercise(exerciseIndex: Int) {
        updateState(_uiState.value.removeExerciseAt(exerciseIndex))
        scheduleAutosave()
    }

    fun moveExercise(from: Int, to: Int) {
        val current = _uiState.value
        val list = current.exercises.toMutableList()
        val item = list.removeAt(from)
        list.add(to.coerceIn(0, list.size), item)
        updateState(current.copy(exercises = list))
        scheduleAutosave()
    }

    fun updateActualWeight(exerciseIndex: Int, setIndex: Int, value: String) {
        val weight = value.filterDecimal()
        val unit = currentWeightUnit
        updateSet(exerciseIndex, setIndex) { set, _ ->
            val convertedDistance = if (set.actualUnit != unit) {
                set.rawActualDistance?.let { set.actualUnit.convertDistance(it, unit) }
            } else {
                set.rawActualDistance
            }
            set.copy(
                actualWeight = weight,
                rawActualWeight = weight.toDoubleOrNull(),
                rawActualDistance = convertedDistance,
                actualUnit = unit,
            )
        }
    }

    fun updateActualReps(exerciseIndex: Int, setIndex: Int, value: String) {
        updateActualField(exerciseIndex, setIndex) { it.copy(actualReps = value.filterDigits()) }
    }

    fun updateActualDuration(exerciseIndex: Int, setIndex: Int, value: String) {
        updateActualField(exerciseIndex, setIndex) {
            it.copy(actualDuration = value.filterDurationText())
        }
    }

    fun updateActualDistance(exerciseIndex: Int, setIndex: Int, value: String) {
        val distance = value.filterDistance()
        val unit = currentWeightUnit
        updateActualField(exerciseIndex, setIndex) { set ->
            val convertedWeight = if (set.actualUnit != unit) {
                set.rawActualWeight?.let { set.actualUnit.convertWeight(it, unit) }
            } else {
                set.rawActualWeight
            }
            set.copy(
                actualDistance = distance,
                rawActualDistance = distance.toDoubleOrNull(),
                rawActualWeight = convertedWeight,
                actualUnit = unit,
            )
        }
    }

    /**
     * Applies [transform] and keeps `completed` only while the set still has
     * every field the exercise type requires. If the edit invalidates the set
     * that owns the running rest timer, the timer is cancelled too.
     */
    private fun updateActualField(
        exerciseIndex: Int,
        setIndex: Int,
        transform: (DraftSessionSet) -> DraftSessionSet,
    ) {
        updateState(_uiState.value.updateActualFieldAt(exerciseIndex, setIndex, transform))
        scheduleAutosave()
    }

    fun setSetType(exerciseIndex: Int, setIndex: Int, setType: SetType) {
        updateSet(exerciseIndex, setIndex) { set, _ -> set.copy(setType = setType) }
    }

    fun toggleCompleted(exerciseIndex: Int, setIndex: Int) {
        updateState(
            _uiState.value.toggleCompletedAt(
                exerciseIndex = exerciseIndex,
                setIndex = setIndex,
                durationSeconds = restTimerDurationSeconds,
            ),
        )
        scheduleAutosave()
    }

    /** Clears the rest timer once its countdown reaches zero. */
    fun clearRestTimer() {
        val current = _uiState.value
        if (current.restTimer == null) return
        updateState(current.copy(restTimer = null))
    }

    /** Rewrite-on-save mirroring RoutineFormState — simpler than diffing. */
    fun save(onSaved: () -> Unit = {}) {
        val state = _uiState.value
        updateState(state.copy(saving = true))
        autosaveJob?.cancel()
        scope.launch {
            persistMutex.withLock { persist(state) }
            updateState(_uiState.value.copy(saving = false))
            onSaved()
        }
    }

    fun finish(onDone: () -> Unit = {}) {
        val state = _uiState.value
        updateState(state.copy(saving = true, restTimer = null))
        closed = true
        autosaveJob?.cancel()
        settingsJob?.cancel()
        scope.launch {
            persistMutex.withLock {
                persist(state)
                WorkoutSessionRepository.finishPersisted(sessionId)
            }
            updateState(_uiState.value.copy(saving = false, finished = true))
            WorkoutSessionRepository.evictFormState(sessionId)
            onDone()
        }
    }

    fun discard(onDone: () -> Unit = {}) {
        updateState(_uiState.value.copy(saving = true, restTimer = null))
        closed = true
        autosaveJob?.cancel()
        settingsJob?.cancel()
        scope.launch {
            persistMutex.withLock { WorkoutSessionRepository.discard(sessionId) }
            updateState(_uiState.value.copy(saving = false, discarded = true))
            WorkoutSessionRepository.evictFormState(sessionId)
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
                val actualWeight = set.rawActualWeight ?: set.actualWeight.toDoubleOrNull()
                val actualDistance = set.rawActualDistance ?: set.actualDistance.toDoubleOrNull()
                val rawTargetWeight = set.rawTargetWeight ?: set.targetWeight.toDoubleOrNull()
                val rawTargetDistance = set.rawTargetDistance ?: set.targetDistance.toDoubleOrNull()
                val hasActualMeasurement = actualWeight != null || actualDistance != null
                val rowUnit = if (hasActualMeasurement) set.actualUnit else set.targetUnit
                val targetWeight = rawTargetWeight?.let { set.targetUnit.convertWeight(it, rowUnit) }
                val targetDistance = rawTargetDistance?.let { set.targetUnit.convertDistance(it, rowUnit) }
                AppDb.db.workoutSessionDetailDao().insert(
                    WorkoutSessionDetailEntity(
                        workoutSessionId = sessionId,
                        workSetDetailId = null,
                        exerciseId = draft.exercise.id,
                        position = position++,
                        targetWeight = targetWeight,
                        targetReps = set.targetReps.toIntOrNull(),
                        targetDuration = parseDurationSeconds(set.targetDuration),
                        targetDistance = targetDistance,
                        actualWeight = actualWeight,
                        actualReps = set.actualReps.toIntOrNull(),
                        actualDuration = parseDurationSeconds(set.actualDuration),
                        actualDistance = actualDistance,
                        setType = set.setType,
                        weightUnit = rowUnit,
                        completed = set.completed,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    private suspend fun loadPreviousSetRows(exercise: Exercise): List<PreviousSetRow> =
        AppDb.db.workoutSessionDetailDao()
            .getLatestCompletedSets(PROFILE_ID, exercise.id)

    private fun updateExercise(index: Int, transform: (DraftSessionExercise) -> DraftSessionExercise) {
        val current = _uiState.value
        updateState(
            current.copy(
                exercises = current.exercises.mapIndexed { i, ex -> if (i == index) transform(ex) else ex },
            ),
        )
        scheduleAutosave()
    }

    private fun updateSet(
        exerciseIndex: Int,
        setIndex: Int,
        transform: (DraftSessionSet, ExerciseType) -> DraftSessionSet,
    ) {
        updateExercise(exerciseIndex) { ex ->
            ex.copy(sets = ex.sets.mapIndexed { i, s -> if (i == setIndex) transform(s, ex.type) else s })
        }
    }
}

private const val AUTOSAVE_DEBOUNCE_MS = 500L

internal fun formatPreviousSets(
    rows: List<PreviousSetRow>,
    exercise: Exercise,
    displayUnit: WeightUnit,
): List<String> {
    val type = ExerciseType.fromValue(exercise.exerciseType) ?: ExerciseType.STRENGTH
    val fields = type.requiredSetFields()
    val unitSuffix = if (displayUnit == WeightUnit.IMPERIAL) "lb" else "kg"
    return rows.map { row ->
        // Blank entries keep the list positionally aligned; the UI
        // shows its placeholder for them.
        listOfNotNull(
            row.actualWeight?.let { "${WeightUnit.formatWeight(it, row.weightUnit, displayUnit)}$unitSuffix" },
            row.actualReps?.takeIf { SetField.REPS in fields }?.let { "x $it" },
            row.actualDuration?.takeIf { SetField.DURATION in fields }?.let { formatDurationHhMm(it) },
            row.actualDistance?.takeIf { SetField.DISTANCE in fields }?.let {
                WeightUnit.formatDistance(it, row.weightUnit, displayUnit)
            },
        ).joinToString(" ")
    }
}
