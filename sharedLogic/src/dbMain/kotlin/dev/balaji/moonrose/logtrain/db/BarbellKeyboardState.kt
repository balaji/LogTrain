package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.model.PlateMath
import dev.balaji.moonrose.logtrain.model.PlateOption
import dev.balaji.moonrose.logtrain.model.WeightUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * What the plate keyboard renders. [platesPerSide] is in insertion order so
 * the breakdown chips read in the order the user tapped them.
 */
data class BarbellKeyboardUiState(
    val visible: Boolean = false,
    /** True while the numeric fallback keypad replaces the plate buttons. */
    val numericMode: Boolean = false,
    val unit: WeightUnit = WeightUnit.METRIC,
    val barWeight: Double = 20.0,
    val platesPerSide: List<PlateOption> = emptyList(),
    /** Plate denominations for [unit], small to large. */
    val plateOptions: List<PlateOption> = emptyList(),
    val barOptions: List<PlateOption> = emptyList(),
    /** Which weight cell the keyboard is editing; -1 when closed. */
    val exerciseIndex: Int = -1,
    val setIndex: Int = -1,
) {
    val total: Double get() = PlateMath.total(barWeight, platesPerSide.map { it.value })
    val totalLabel: String get() = PlateMath.formatWeight(total)

    /**
     * [platesPerSide] grouped by denomination, largest first, for display.
     * [count] is the total physical plates across both sides — double the
     * number of taps for that denomination, since each tap loads one plate
     * per side.
     */
    val plateBreakdown: List<PlateBreakdownEntry>
        get() = platesPerSide
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.key.value }
            .map { PlateBreakdownEntry(option = it.key, count = it.value * 2) }
}

data class PlateBreakdownEntry(val option: PlateOption, val count: Int)

/** Where the keyboard writes weight strings; the form state in production. */
fun interface WeightSink {
    fun update(exerciseIndex: Int, setIndex: Int, value: String)
}

/** Per-exercise last-used bar weight; user_settings in production. */
interface BarWeightStore {
    suspend fun get(exerciseId: Long, unit: WeightUnit): Double?
    suspend fun set(exerciseId: Long, unit: WeightUnit, weight: Double)
}

private object SettingsBarWeightStore : BarWeightStore {
    override suspend fun get(exerciseId: Long, unit: WeightUnit): Double? =
        UserSettingsRepository.barWeightFor(exerciseId, unit)

    override suspend fun set(exerciseId: Long, unit: WeightUnit, weight: Double) =
        UserSettingsRepository.setBarWeight(exerciseId, unit, weight)
}

/**
 * Drives the barbell plate keyboard on both the Compose and SwiftUI session
 * screens. Every mutation writes the recomputed total through [sink] as a
 * formatted string, riding the form state's existing sanitize/autosave path —
 * opening alone never writes, so open-then-dismiss can't fill an empty field.
 *
 * The display unit is read once per [open]; changing units while the keyboard
 * is up keeps the old plate set until it is reopened.
 */
class BarbellKeyboardState internal constructor(
    private val sink: WeightSink,
    private val store: BarWeightStore,
    private val unitProvider: suspend () -> WeightUnit,
    private val scope: CoroutineScope,
) {
    constructor(formState: WorkoutSessionFormState) : this(
        sink = formState::updateActualWeight,
        store = SettingsBarWeightStore,
        unitProvider = { UserSettingsRepository.settings.first().weightUnit },
        scope = CoroutineScope(Dispatchers.Main),
    )

    private val _uiState = MutableStateFlow(BarbellKeyboardUiState())
    val uiState: StateFlow<BarbellKeyboardUiState> = _uiState.asStateFlow()

    private var exerciseId = -1L

    /**
     * Mirror of the weight cell's text, kept so numeric-mode edits and
     * plates-mode re-decomposition see the current value. UIs whose numeric
     * mode edits the field outside [appendNumericChar]/[deleteNumericChar]
     * (the iOS system decimal pad) must call [syncFieldText] on each change.
     */
    private var fieldText = ""

    /** Called when a barbell weight cell is tapped/focused. */
    fun open(exerciseIndex: Int, setIndex: Int, exerciseId: Long, currentValue: String) {
        scope.launch {
            val unit = unitProvider()
            val bar = store.get(exerciseId, unit) ?: PlateMath.defaultBar(unit)
            this@BarbellKeyboardState.exerciseId = exerciseId
            fieldText = currentValue
            val decomposed = decomposeField(currentValue, bar, unit)
            _uiState.value = BarbellKeyboardUiState(
                visible = true,
                // A value we can't build from plates opens on the fallback
                // keypad instead of silently replacing it.
                numericMode = decomposed == null,
                unit = unit,
                barWeight = bar,
                platesPerSide = (decomposed ?: emptyList()).toOptions(),
                plateOptions = PlateMath.plateOptions(unit),
                barOptions = PlateMath.barOptions(unit),
                exerciseIndex = exerciseIndex,
                setIndex = setIndex,
            )
        }
    }

    fun close() {
        _uiState.value = _uiState.value.copy(visible = false, exerciseIndex = -1, setIndex = -1)
    }

    fun addPlate(value: Double) {
        val state = _uiState.value
        commit(state.copy(platesPerSide = state.platesPerSide + value.toOption()))
    }

    fun removePlateAt(index: Int) {
        val state = _uiState.value
        if (index !in state.platesPerSide.indices) return
        commit(state.copy(platesPerSide = state.platesPerSide.filterIndexed { i, _ -> i != index }))
    }

    /**
     * Removes one tap's worth (one plate per side) of the given denomination —
     * i.e. the action behind tapping a grouped breakdown chip down by one.
     * Plates of the same value are fungible, so which specific tap gets
     * removed doesn't matter.
     */
    fun removePlate(value: Double) {
        val state = _uiState.value
        val index = state.platesPerSide.indexOfFirst { it.value == value }
        if (index == -1) return
        commit(state.copy(platesPerSide = state.platesPerSide.filterIndexed { i, _ -> i != index }))
    }

    fun removeLastPlate() {
        val state = _uiState.value
        if (state.platesPerSide.isEmpty()) return
        commit(state.copy(platesPerSide = state.platesPerSide.dropLast(1)))
    }

    fun clearPlates() = commit(_uiState.value.copy(platesPerSide = emptyList()))

    /** Recomputes the total with the new bar and remembers it for this exercise. */
    fun selectBarWeight(value: Double) {
        val state = _uiState.value
        commit(state.copy(barWeight = value))
        val id = exerciseId
        if (id != -1L) scope.launch { store.set(id, state.unit, value) }
    }

    /**
     * Leaving numeric mode re-decomposes the field's current value; if it
     * can't be loaded from plates, the keypad resets to bar-only but leaves
     * the field text alone until the next plate tap.
     */
    fun setNumericMode(numeric: Boolean) {
        val state = _uiState.value
        if (numeric) {
            _uiState.value = state.copy(numericMode = true)
            return
        }
        val decomposed = decomposeField(fieldText, state.barWeight, state.unit)
        _uiState.value = state.copy(
            numericMode = false,
            platesPerSide = (decomposed ?: emptyList()).toOptions(),
        )
    }

    /** Numeric-mode keypad input, sharing the form state's sanitization. */
    fun appendNumericChar(c: String) {
        writeField(fieldText + c)
    }

    fun deleteNumericChar() {
        if (fieldText.isEmpty()) return
        writeField(fieldText.dropLast(1))
    }

    /** Keeps [fieldText] current when the UI edits the field directly. */
    fun syncFieldText(value: String) {
        fieldText = value
    }

    private fun commit(state: BarbellKeyboardUiState) {
        _uiState.value = state
        fieldText = state.totalLabel
        sink.update(state.exerciseIndex, state.setIndex, fieldText)
    }

    private fun writeField(value: String) {
        val state = _uiState.value
        fieldText = value.filterDecimal()
        sink.update(state.exerciseIndex, state.setIndex, fieldText)
    }

    /** Null when the value isn't blank and isn't loadable from plates. */
    private fun decomposeField(value: String, bar: Double, unit: WeightUnit): List<Double>? {
        if (value.isBlank()) return emptyList()
        val total = value.toDoubleOrNull() ?: return null
        return PlateMath.decompose(total, bar, unit)
    }

    private fun Double.toOption() = PlateOption(this, PlateMath.formatWeight(this))

    private fun List<Double>.toOptions() = map { it.toOption() }
}
