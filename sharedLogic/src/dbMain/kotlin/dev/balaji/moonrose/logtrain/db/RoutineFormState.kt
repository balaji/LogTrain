package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.model.Exercise
import dev.balaji.moonrose.logtrain.model.ExerciseType
import dev.balaji.moonrose.logtrain.model.SetType
import dev.balaji.moonrose.logtrain.model.WeightUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DraftSet(
    val weight: String = "",
    val reps: String = "",
    // Single h:mm box text, e.g. "1:30" or bare minutes "45".
    val duration: String = "",
    val distance: String = "",
    val setType: SetType = SetType.NORMAL,
    internal val rawWeight: Double? = null,
    internal val rawDistance: Double? = null,
    internal val recordedUnit: WeightUnit = WeightUnit.METRIC,
)

internal fun DraftSet.formattedFor(unit: WeightUnit): DraftSet = copy(
    weight = rawWeight?.let { WeightUnit.formatWeight(it, recordedUnit, unit) } ?: weight,
    distance = rawDistance?.let { WeightUnit.formatDistance(it, recordedUnit, unit) } ?: distance,
)

data class DraftWorkSet(
    val id: Long,
    val exercise: Exercise,
    val sets: List<DraftSet> = listOf(DraftSet()),
) {
    /** Which measured fields this exercise's sets take; unrecognized types fall back to strength (reps). */
    val type: ExerciseType get() = ExerciseType.fromValue(exercise.exerciseType) ?: ExerciseType.STRENGTH
}

data class RoutineDetailUiState(
    val loading: Boolean = true,
    val name: String = "",
    val workSets: List<DraftWorkSet> = emptyList(),
    val saving: Boolean = false,
)

class RoutineFormState(private val routineId: Long, initialName: String) {
    private val _uiState = MutableStateFlow(RoutineDetailUiState(name = initialName))
    val uiState: StateFlow<RoutineDetailUiState> = _uiState.asStateFlow()
    private var nextId = 0L
    private var currentWeightUnit: WeightUnit = WeightUnit.METRIC

    init {
        CoroutineScope(Dispatchers.Main).launch {
            currentWeightUnit = UserSettingsRepository.settings.first().weightUnit
            val workSets = AppDb.db.workSetDao().getForRoutine(routineId)
            val draftWorkSets = workSets.mapNotNull { ws ->
                val exercise = ExerciseRepository.findById(ws.exerciseId) ?: return@mapNotNull null
                val details = AppDb.db.workSetDetailDao().getForWorkSet(ws.id)
                DraftWorkSet(
                    id = nextId++,
                    exercise = exercise,
                    sets = if (details.isEmpty()) {
                        listOf(DraftSet(recordedUnit = currentWeightUnit))
                    } else {
                        details.map { d ->
                            DraftSet(
                                reps = d.targetReps?.toString() ?: "",
                                duration = formatDurationText(d.targetDuration),
                                setType = d.setType,
                                rawWeight = d.targetWeight,
                                rawDistance = d.targetDistance,
                                recordedUnit = d.weightUnit,
                            ).formattedFor(currentWeightUnit)
                        }
                    },
                )
            }
            _uiState.value = _uiState.value.copy(loading = false, workSets = draftWorkSets)
        }
    }

    /** Updates the display unit for all unedited sets without losing precision. */
    fun setDisplayUnit(unit: WeightUnit) {
        if (unit == currentWeightUnit) return
        currentWeightUnit = unit
        val current = _uiState.value
        if (current.loading) return
        _uiState.value = current.copy(
            workSets = current.workSets.map { ws ->
                ws.copy(sets = ws.sets.map { it.formattedFor(unit) })
            },
        )
    }

    fun updateName(value: String) {
        _uiState.value = _uiState.value.copy(name = filterProgramName(value))
    }

    fun addExercise(exercise: Exercise) {
        val current = _uiState.value
        _uiState.value = current.copy(
            workSets = current.workSets + DraftWorkSet(
                id = nextId++,
                exercise = exercise,
                sets = listOf(DraftSet(recordedUnit = currentWeightUnit)),
            ),
        )
    }

    fun addSet(workSetIndex: Int) {
        updateWorkSet(workSetIndex) { ws ->
            ws.copy(sets = ws.sets + DraftSet(recordedUnit = currentWeightUnit))
        }
    }

    fun updateWeight(workSetIndex: Int, setIndex: Int, value: String) {
        val weight = value.filterDecimal()
        val unit = currentWeightUnit
        updateWorkSet(workSetIndex) { ws ->
            ws.copy(
                sets = ws.sets.mapIndexed { i, s ->
                    if (i != setIndex) {
                        s
                    } else {
                        val convertedDistance = if (s.recordedUnit != unit) {
                            s.rawDistance?.let { s.recordedUnit.convertDistance(it, unit) }
                        } else {
                            s.rawDistance
                        }
                        s.copy(
                            weight = weight,
                            rawWeight = weight.toDoubleOrNull(),
                            rawDistance = convertedDistance,
                            recordedUnit = unit,
                        )
                    }
                },
            )
        }
    }

    fun updateReps(workSetIndex: Int, setIndex: Int, value: String) {
        val reps = value.filterDigits()
        updateWorkSet(workSetIndex) { ws ->
            ws.copy(sets = ws.sets.mapIndexed { i, s -> if (i == setIndex) s.copy(reps = reps) else s })
        }
    }

    fun updateDuration(workSetIndex: Int, setIndex: Int, value: String) {
        val duration = value.filterDurationText()
        updateWorkSet(workSetIndex) { ws ->
            ws.copy(sets = ws.sets.mapIndexed { i, s -> if (i == setIndex) s.copy(duration = duration) else s })
        }
    }

    fun updateDistance(workSetIndex: Int, setIndex: Int, value: String) {
        val distance = value.filterDistance()
        val unit = currentWeightUnit
        updateWorkSet(workSetIndex) { ws ->
            ws.copy(
                sets = ws.sets.mapIndexed { i, s ->
                    if (i != setIndex) {
                        s
                    } else {
                        val convertedWeight = if (s.recordedUnit != unit) {
                            s.rawWeight?.let { s.recordedUnit.convertWeight(it, unit) }
                        } else {
                            s.rawWeight
                        }
                        s.copy(
                            distance = distance,
                            rawDistance = distance.toDoubleOrNull(),
                            rawWeight = convertedWeight,
                            recordedUnit = unit,
                        )
                    }
                },
            )
        }
    }

    fun setSetType(workSetIndex: Int, setIndex: Int, setType: SetType) {
        updateWorkSet(workSetIndex) { ws ->
            ws.copy(sets = ws.sets.mapIndexed { i, s -> if (i == setIndex) s.copy(setType = setType) else s })
        }
    }

    fun moveExercise(from: Int, to: Int) {
        val current = _uiState.value
        val list = current.workSets.toMutableList()
        val item = list.removeAt(from)
        list.add(to.coerceIn(0, list.size), item)
        _uiState.value = current.copy(workSets = list)
    }

    fun removeExercise(workSetIndex: Int) {
        val current = _uiState.value
        _uiState.value = current.copy(
            workSets = current.workSets.filterIndexed { i, _ -> i != workSetIndex },
        )
    }

    fun removeSet(workSetIndex: Int, setIndex: Int) {
        updateWorkSet(workSetIndex) { ws ->
            ws.copy(sets = ws.sets.filterIndexed { i, _ -> i != setIndex })
        }
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        _uiState.value = state.copy(saving = true)
        CoroutineScope(Dispatchers.Main).launch {
            val now = nowMillis()
            ProgramRepository.renameRoutine(
                routineId,
                filterProgramName(state.name).trim().ifBlank { "Routine" },
            )
            AppDb.db.workSetDao().deleteForRoutine(routineId)
            state.workSets.forEachIndexed { wsIdx, draft ->
                val wsId = AppDb.db.workSetDao().insert(
                    WorkSetEntity(
                        routineId = routineId,
                        exerciseId = draft.exercise.id,
                        position = wsIdx + 1,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                draft.sets.forEachIndexed { sIdx, set ->
                    AppDb.db.workSetDetailDao().insert(
                        WorkSetDetailEntity(
                            workSetId = wsId,
                            position = sIdx + 1,
                            targetWeight = set.rawWeight ?: set.weight.toDoubleOrNull(),
                            targetReps = set.reps.toIntOrNull(),
                            targetDuration = parseDurationSeconds(set.duration),
                            targetDistance = set.rawDistance ?: set.distance.toDoubleOrNull(),
                            setType = set.setType,
                            weightUnit = set.recordedUnit,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                }
            }
            _uiState.value = _uiState.value.copy(saving = false)
            onSaved()
        }
    }

    private fun updateWorkSet(index: Int, transform: (DraftWorkSet) -> DraftWorkSet) {
        val current = _uiState.value
        _uiState.value = current.copy(
            workSets = current.workSets.mapIndexed { i, ws -> if (i == index) transform(ws) else ws },
        )
    }
}
