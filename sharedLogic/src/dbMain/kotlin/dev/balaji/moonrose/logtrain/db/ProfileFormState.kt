package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.model.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Why the name is invalid; the UI layers supply the localized message
 * (with [MAX_NAME_LENGTH] as the argument for [TOO_LONG]).
 */
enum class ProfileNameError { REQUIRED, TOO_LONG, INVALID_CHARS }

data class ProfileFormUiState(
    val name: String = "",
    val age: String = "",
    val gender: String? = null,
    val weight: String = "",
    val height: String = "",
    val nameError: ProfileNameError? = null,
    /** True when the age is not a number in [MIN_AGE]..[MAX_AGE]; the UI supplies the localized message. */
    val ageError: Boolean = false,
    val saving: Boolean = false,
)

const val MAX_NAME_LENGTH = 50
const val MIN_AGE = 1
const val MAX_AGE = 120

/**
 * Language-neutral keys persisted in profile.gender; the UI layers map them
 * to localized labels (genderLabel in LogicLabels.kt on Compose, and the
 * mirror in ProfileFormView.swift on iOS) and never store display text.
 */
val GENDER_KEYS: List<String> = listOf("male", "female", "other", "prefer_not_to_say")

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
        ),
    )
    val uiState: StateFlow<ProfileFormUiState> = _uiState.asStateFlow()

    fun updateName(value: String) {
        val filtered = value.filter { it.isLetter() || it == ' ' }.take(MAX_NAME_LENGTH)
        _uiState.value = _uiState.value.copy(name = filtered, nameError = null)
    }

    fun updateAge(value: String) {
        _uiState.value = _uiState.value.copy(age = value.filterDigits(), ageError = false)
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

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        val nameError = validateName(state.name)
        val ageError = hasAgeError(state.age)
        if (nameError != null || ageError) {
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
            )
            _uiState.value = _uiState.value.copy(saving = false)
            onSaved()
        }
    }
}

private fun validateName(name: String): ProfileNameError? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> ProfileNameError.REQUIRED
        trimmed.length > MAX_NAME_LENGTH -> ProfileNameError.TOO_LONG
        !trimmed.all { it.isLetter() || it == ' ' } -> ProfileNameError.INVALID_CHARS
        else -> null
    }
}

/** A blank age is allowed; a non-blank one must parse into [MIN_AGE]..[MAX_AGE]. */
private fun hasAgeError(age: String): Boolean {
    if (age.isBlank()) return false
    val parsed = age.toIntOrNull()
    return parsed == null || parsed < MIN_AGE || parsed > MAX_AGE
}

