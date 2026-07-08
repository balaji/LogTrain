package dev.balaji.moonrose.logtrain.db

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalObjCName::class)
data class ExerciseFormUiState(
    val loading: Boolean = true,
    val name: String = "",
    // "description" collides with NSObject's auto-generated `description` (toString())
    // on the Swift side, so it silently resolved to the wrong value there.
    @ObjCName("descriptionText")
    val description: String = "",
    val exerciseType: String? = null,
    val bodyPart: String? = null,
    val equipment: String? = null,
    val nameError: Boolean = false,
    val duplicateNameError: Boolean = false,
    val saving: Boolean = false,
)

/**
 * Exercise edit form state, shared by the Compose form and (in the future) a
 * native form so validation and save logic live in one place.
 *
 * A null [exerciseId] means the form creates a new exercise on save.
 */
class ExerciseFormState private constructor(private val exerciseId: Long?) {
    constructor(exerciseId: Long) : this(exerciseId as Long?)
    constructor() : this(null)

    private val _uiState = MutableStateFlow(ExerciseFormUiState(loading = exerciseId != null))
    val uiState: StateFlow<ExerciseFormUiState> = _uiState.asStateFlow()

    init {
        if (exerciseId != null) {
            CoroutineScope(Dispatchers.Main).launch {
                val exercise = ExerciseRepository.findById(exerciseId)
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    name = exercise?.name ?: "",
                    description = exercise?.description ?: "",
                    exerciseType = exercise?.exerciseType,
                    bodyPart = exercise?.bodyPart,
                    equipment = exercise?.equipment,
                )
            }
        }
    }

    fun updateName(value: String) {
        _uiState.value = _uiState.value.copy(name = value, nameError = false, duplicateNameError = false)
    }

    fun updateDescription(value: String) {
        _uiState.value = _uiState.value.copy(description = value)
    }

    fun updateExerciseType(value: String) {
        _uiState.value = _uiState.value.copy(exerciseType = value)
    }

    fun updateBodyPart(value: String) {
        _uiState.value = _uiState.value.copy(bodyPart = value)
    }

    fun updateEquipment(value: String) {
        _uiState.value = _uiState.value.copy(equipment = value)
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.value = state.copy(nameError = true)
            return
        }
        _uiState.value = state.copy(saving = true)
        CoroutineScope(Dispatchers.Main).launch {
            val error = if (exerciseId == null) {
                ExerciseRepository.create(
                    name = state.name.trim(),
                    description = state.description.trim().ifBlank { null },
                    exerciseType = state.exerciseType,
                    bodyPart = state.bodyPart,
                    equipment = state.equipment,
                )
            } else {
                ExerciseRepository.update(
                    id = exerciseId,
                    name = state.name.trim(),
                    description = state.description.trim().ifBlank { null },
                    exerciseType = state.exerciseType,
                    bodyPart = state.bodyPart,
                    equipment = state.equipment,
                )
            }
            if (error == ExerciseUpdateError.DuplicateName) {
                _uiState.value = _uiState.value.copy(saving = false, duplicateNameError = true)
            } else {
                _uiState.value = _uiState.value.copy(saving = false)
                onSaved()
            }
        }
    }
}
