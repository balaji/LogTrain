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
    val nameError: Boolean = false,
    val saving: Boolean = false,
)

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
        _uiState.value = _uiState.value.copy(name = value, nameError = false)
    }

    fun updateAge(value: String) {
        _uiState.value = _uiState.value.copy(age = value.filter(Char::isDigit))
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
        if (state.name.isBlank()) {
            _uiState.value = state.copy(nameError = true)
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

private fun String.filterDecimal(): String {
    val filtered = filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    return if (firstDot == -1) {
        filtered
    } else {
        filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
    }
}
