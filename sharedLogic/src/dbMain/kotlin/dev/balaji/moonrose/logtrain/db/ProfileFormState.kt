package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.model.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileFormUiState(
    val name: String = "",
    val age: String = "",
    val gender: String? = null,
    val weight: String = "",
    val height: String = "",
    val weekStartsMonday: Boolean = false,
    val nameError: String? = null,
    val ageError: String? = null,
    val saving: Boolean = false,
)

const val MAX_NAME_LENGTH = 50
const val MIN_AGE = 1
const val MAX_AGE = 120

/**
 * Profile create/edit form state, shared by the Compose form and the native
 * SwiftUI form so validation and save logic live in one place.
 */
class ProfileFormState(initial: Profile? = null) {
    private val _uiState = MutableStateFlow(
        ProfileFormUiState(
            name = initial?.name ?: "",
            age = initial?.age?.toString() ?: "",
            gender = initial?.gender,
            weight = initial?.weight?.toString() ?: "",
            height = initial?.height?.toString() ?: "",
            weekStartsMonday = initial?.weekStartsMonday ?: false,
        ),
    )
    val uiState: StateFlow<ProfileFormUiState> = _uiState.asStateFlow()

    fun updateName(value: String) {
        val filtered = value.filter { it.isLetter() || it == ' ' }.take(MAX_NAME_LENGTH)
        _uiState.value = _uiState.value.copy(name = filtered, nameError = null)
    }

    fun updateAge(value: String) {
        _uiState.value = _uiState.value.copy(age = value.filterDigits(), ageError = null)
    }

    fun updateGender(value: String) {
        _uiState.value = _uiState.value.copy(gender = value)
    }

    fun updateWeight(value: String) {
        _uiState.value = _uiState.value.copy(weight = value.filterDecimal())
    }

    fun updateHeight(value: String) {
        _uiState.value = _uiState.value.copy(height = value.filterDecimal())
    }

    fun updateWeekStartsMonday(value: Boolean) {
        _uiState.value = _uiState.value.copy(weekStartsMonday = value)
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        val nameError = validateName(state.name)
        val ageError = validateAge(state.age)
        if (nameError != null || ageError != null) {
            _uiState.value = state.copy(nameError = nameError, ageError = ageError)
            return
        }
        _uiState.value = state.copy(saving = true)
        CoroutineScope(Dispatchers.Main).launch {
            ProfileRepository.save(
                name = state.name.trim(),
                age = state.age.toIntOrNull(),
                gender = state.gender,
                weight = state.weight.toDoubleOrNull(),
                height = state.height.toDoubleOrNull(),
                weekStartsMonday = state.weekStartsMonday,
            )
            _uiState.value = _uiState.value.copy(saving = false)
            onSaved()
        }
    }
}

private fun validateName(name: String): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "Name is required"
        trimmed.length > MAX_NAME_LENGTH -> "Name must be at most $MAX_NAME_LENGTH characters"
        !trimmed.all { it.isLetter() || it == ' ' } -> "Name must contain only letters"
        else -> null
    }
}

private fun validateAge(age: String): String? {
    if (age.isBlank()) return null
    val parsed = age.toIntOrNull()
    return if (parsed == null || parsed < MIN_AGE || parsed > MAX_AGE) {
        "Age must be between $MIN_AGE and $MAX_AGE"
    } else {
        null
    }
}

