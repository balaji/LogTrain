package dev.balaji.moonrose.trainfree.db

import dev.balaji.moonrose.trainfree.insights.InsightMath
import dev.balaji.moonrose.trainfree.model.FeetInches
import dev.balaji.moonrose.trainfree.model.HeightConversion
import dev.balaji.moonrose.trainfree.model.Profile
import dev.balaji.moonrose.trainfree.model.WeightUnit
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
    val unit: WeightUnit = WeightUnit.METRIC,
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
private const val MIN_WHEEL_FEET = 3
private const val MAX_WHEEL_FEET = 8

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
 * Weight and height are edited in [ProfileFormUiState.unit]'s display units
 * but stored canonically in kg/cm; [save] also persists the chosen unit in
 * user settings. To keep repeated edit/save round-trips or unit toggles from
 * drifting stored values through rounded display text, [save] and [updateUnit]
 * preserve the original stored values until a field is actually edited.
 */
class ProfileFormState internal constructor(
    initial: Profile? = null,
    unit: WeightUnit = WeightUnit.METRIC,
    private val saveProfile: suspend (
        name: String,
        age: Int?,
        gender: String?,
        weight: Double?,
        height: Double?,
    ) -> Unit,
    private val saveUnit: suspend (WeightUnit) -> Unit,
    private val scope: CoroutineScope,
) {
    constructor(initial: Profile? = null, unit: WeightUnit = WeightUnit.METRIC) : this(
        initial = initial,
        unit = unit,
        saveProfile = { name, age, gender, weight, height ->
            ProfileRepository.save(
                name = name,
                age = age,
                gender = gender,
                weight = weight,
                height = height,
            )
        },
        saveUnit = { UserSettingsRepository.setWeightUnit(it) },
        scope = CoroutineScope(Dispatchers.Main),
    )

    private val initialWeightKg = initial?.weight
    private val initialHeightCm = initial?.height
    private val initialFeetInches = initialHeightCm?.let { HeightConversion.cmToFeetInches(it) }
    private var weightPrefilled = initialWeightKg != null
    private var heightPrefilled = initialHeightCm != null
    private var heightWheelSelected = false

    private val _uiState = MutableStateFlow(
        ProfileFormUiState(
            name = initial?.name ?: "",
            age = initial?.age?.toString() ?: "",
            gender = initial?.gender,
            unit = unit,
            weight = initialWeightKg?.let { InsightMath.formatNumber(unit.fromKilograms(it)) } ?: "",
            height = initialHeightCm?.let { InsightMath.formatNumber(it) } ?: "",
            heightFeet = (initialFeetInches ?: DEFAULT_FEET_INCHES).feet.coerceIn(MIN_WHEEL_FEET, MAX_WHEEL_FEET),
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

    fun updateUnit(unit: WeightUnit) {
        val state = _uiState.value
        if (state.unit == unit) return
        val currentWeightKg = weightKgToSave(state)
        val currentHeightCm = heightCmToSave(state)
        if (unit == WeightUnit.IMPERIAL && currentHeightCm != null && !heightPrefilled) {
            heightWheelSelected = true
        }
        if (unit == WeightUnit.METRIC && currentHeightCm == null) {
            heightWheelSelected = false
        }
        val feetInches = currentHeightCm?.let { HeightConversion.cmToFeetInches(it) } ?: DEFAULT_FEET_INCHES
        _uiState.value = state.copy(
            unit = unit,
            weight = currentWeightKg?.let { InsightMath.formatNumber(unit.fromKilograms(it)) } ?: "",
            height = currentHeightCm?.let { InsightMath.formatNumber(it) } ?: "",
            heightFeet = feetInches.feet.coerceIn(MIN_WHEEL_FEET, MAX_WHEEL_FEET),
            heightInches = feetInches.inches,
        )
    }

    fun updateWeight(value: String) {
        val filtered = value.filterDecimal()
        if (filtered != _uiState.value.weight) {
            weightPrefilled = false
        }
        _uiState.value = _uiState.value.copy(weight = filtered)
    }

    fun updateHeight(value: String) {
        val filtered = value.filterDecimal()
        if (filtered != _uiState.value.height) {
            heightPrefilled = false
            heightWheelSelected = false
        }
        _uiState.value = _uiState.value.copy(height = filtered)
    }

    fun updateHeightFeetInches(feet: Int, inches: Int) {
        val state = _uiState.value
        if (feet == state.heightFeet && inches == state.heightInches) return
        heightPrefilled = false
        heightWheelSelected = true
        _uiState.value = state.copy(heightFeet = feet, heightInches = inches)
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
        scope.launch {
            // Persist the unit preference before saving the profile so the
            // profile observer doesn't transition to tabs with a stale unit.
            saveUnit(state.unit)
            saveProfile(
                state.name.trim(),
                state.age.toIntOrNull(),
                state.gender,
                weightKgToSave(state),
                heightCmToSave(state),
            )
            _uiState.value = _uiState.value.copy(saving = false)
            onSaved()
        }
    }

    /** Untouched prefill keeps the exact stored kilograms without rounding drift. */
    private fun weightKgToSave(state: ProfileFormUiState): Double? =
        if (weightPrefilled) {
            initialWeightKg
        } else {
            state.weight.toDoubleOrNull()?.let { state.unit.toKilograms(it) }
        }

    /**
     * Untouched prefill — or wheels put back on the prefill's feet/inches —
     * keeps the exact stored centimeters.
     */
    private fun heightCmToSave(state: ProfileFormUiState): Double? {
        if (heightPrefilled) return initialHeightCm
        return when (state.unit) {
            WeightUnit.METRIC -> state.height.toDoubleOrNull()
            WeightUnit.IMPERIAL -> when {
                !heightWheelSelected -> null
                initialFeetInches == FeetInches(state.heightFeet, state.heightInches) -> initialHeightCm
                else -> HeightConversion.feetInchesToCm(state.heightFeet, state.heightInches)
            }
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

