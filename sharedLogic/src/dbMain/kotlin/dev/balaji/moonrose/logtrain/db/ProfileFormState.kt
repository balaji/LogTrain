package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.insights.InsightMath
import dev.balaji.moonrose.logtrain.model.FeetInches
import dev.balaji.moonrose.logtrain.model.HeightConversion
import dev.balaji.moonrose.logtrain.model.Profile
import dev.balaji.moonrose.logtrain.model.WeightUnit
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
    /** In the form's display unit (kg or lb); [ProfileFormState.save] converts back. */
    val weight: String = "",
    /** Centimeters; only used when the form's unit is metric. */
    val height: String = "",
    /** Wheel-picker height; only used when the form's unit is imperial. */
    val heightFeet: Int = 5,
    val heightInches: Int = 7,
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

/** Wheel starting point when the profile has no height yet: 170 cm ≈ 5 ft 7 in. */
private val DEFAULT_FEET_INCHES = HeightConversion.cmToFeetInches(170.0)

/**
 * Profile create/edit form state, shared by the Compose form and the native
 * SwiftUI form so validation and save logic live in one place.
 *
 * Weight and height are edited in [unit]'s display units but stored
 * canonically in kg/cm. To keep repeated edit/save round-trips from drifting
 * the stored values through rounded display text, [save] persists the
 * original stored value whenever a field still shows its prefill.
 */
class ProfileFormState(initial: Profile? = null, private val unit: WeightUnit = WeightUnit.METRIC) {
    private val initialWeightKg = initial?.weight
    private val initialHeightCm = initial?.height
    private val initialWeightText =
        initialWeightKg?.let { InsightMath.formatNumber(unit.fromKilograms(it)) } ?: ""
    private val initialHeightText = initialHeightCm?.let { InsightMath.formatNumber(it) } ?: ""
    private val initialFeetInches = initialHeightCm?.let { HeightConversion.cmToFeetInches(it) }
    private var heightWheelTouched = false

    private val _uiState = MutableStateFlow(
        ProfileFormUiState(
            name = initial?.name ?: "",
            age = initial?.age?.toString() ?: "",
            gender = initial?.gender,
            weight = initialWeightText,
            height = initialHeightText,
            heightFeet = (initialFeetInches ?: DEFAULT_FEET_INCHES).feet,
            heightInches = (initialFeetInches ?: DEFAULT_FEET_INCHES).inches,
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

    fun updateHeightFeetInches(feet: Int, inches: Int) {
        heightWheelTouched = true
        _uiState.value = _uiState.value.copy(heightFeet = feet, heightInches = inches)
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
                weight = weightKgToSave(state),
                height = heightCmToSave(state),
            )
            _uiState.value = _uiState.value.copy(saving = false)
            onSaved()
        }
    }

    /** Prefill text still showing → keep the exact stored kilograms. */
    private fun weightKgToSave(state: ProfileFormUiState): Double? =
        if (state.weight == initialWeightText) {
            initialWeightKg
        } else {
            state.weight.toDoubleOrNull()?.let { unit.toKilograms(it) }
        }

    /**
     * Untouched wheels — or wheels put back on the prefill's feet/inches —
     * keep the exact stored centimeters; same for unchanged metric text.
     */
    private fun heightCmToSave(state: ProfileFormUiState): Double? = when (unit) {
        WeightUnit.METRIC ->
            if (state.height == initialHeightText) initialHeightCm else state.height.toDoubleOrNull()
        WeightUnit.IMPERIAL -> when {
            !heightWheelTouched -> initialHeightCm
            initialFeetInches == FeetInches(state.heightFeet, state.heightInches) -> initialHeightCm
            else -> HeightConversion.feetInchesToCm(state.heightFeet, state.heightInches)
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

