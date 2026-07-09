package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.model.Exercise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DraftSet(val weight: String = "", val reps: String = "", val warmup: Boolean = false)

data class DraftWorkSet(
    val id: Long,
    val exercise: Exercise,
    val sets: List<DraftSet> = listOf(DraftSet()),
)

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

    init {
        CoroutineScope(Dispatchers.Main).launch {
            val workSets = AppDb.db.workSetDao().getForRoutine(routineId)
            val draftWorkSets = workSets.mapNotNull { ws ->
                val exercise = ExerciseRepository.findById(ws.exerciseId) ?: return@mapNotNull null
                val details = AppDb.db.workSetDetailDao().getForWorkSet(ws.id)
                DraftWorkSet(
                    id = nextId++,
                    exercise = exercise,
                    sets = if (details.isEmpty()) {
                        listOf(DraftSet())
                    } else {
                        details.map { d ->
                            DraftSet(
                                weight = d.targetWeight?.formatWeight() ?: "",
                                reps = d.targetReps?.toString() ?: "",
                                warmup = d.warmup,
                            )
                        }
                    },
                )
            }
            _uiState.value = _uiState.value.copy(loading = false, workSets = draftWorkSets)
        }
    }

    fun updateName(value: String) {
        _uiState.value = _uiState.value.copy(name = filterProgramName(value))
    }

    fun addExercise(exercise: Exercise) {
        val current = _uiState.value
        _uiState.value = current.copy(
            workSets = current.workSets + DraftWorkSet(id = nextId++, exercise = exercise),
        )
    }

    fun addSet(workSetIndex: Int) {
        updateWorkSet(workSetIndex) { ws -> ws.copy(sets = ws.sets + DraftSet()) }
    }

    fun updateWeight(workSetIndex: Int, setIndex: Int, value: String) {
        val weight = value.filterDecimal()
        updateWorkSet(workSetIndex) { ws ->
            ws.copy(sets = ws.sets.mapIndexed { i, s -> if (i == setIndex) s.copy(weight = weight) else s })
        }
    }

    fun updateReps(workSetIndex: Int, setIndex: Int, value: String) {
        val reps = value.filterDigits()
        updateWorkSet(workSetIndex) { ws ->
            ws.copy(sets = ws.sets.mapIndexed { i, s -> if (i == setIndex) s.copy(reps = reps) else s })
        }
    }

    fun toggleWarmup(workSetIndex: Int, setIndex: Int) {
        updateWorkSet(workSetIndex) { ws ->
            ws.copy(sets = ws.sets.mapIndexed { i, s -> if (i == setIndex) s.copy(warmup = !s.warmup) else s })
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
                            targetWeight = set.weight.toDoubleOrNull(),
                            targetReps = set.reps.toIntOrNull(),
                            targetDuration = null,
                            warmup = set.warmup,
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

private fun Double.formatWeight(): String =
    if (this == toLong().toDouble()) toLong().toString() else toString()
