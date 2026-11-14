package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.model.Profile
import dev.balaji.moonrose.logtrain.model.WeightUnit
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

class ProfileFormStateTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val events = mutableListOf<String>()
    private var savedUnit: WeightUnit? = null
    private var savedProfile: SavedProfile? = null

    private data class SavedProfile(
        val name: String,
        val age: Int?,
        val gender: String?,
        val weight: Double?,
        val height: Double?,
    )

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun newForm(
        initial: Profile? = null,
        unit: WeightUnit = WeightUnit.METRIC,
    ) = ProfileFormState(
        initial = initial,
        unit = unit,
        saveProfile = { name, age, gender, weight, height ->
            events.add("profile")
            savedProfile = SavedProfile(name, age, gender, weight, height)
        },
        saveUnit = { chosen ->
            events.add("unit")
            savedUnit = chosen
        },
        scope = scope,
    )

    @Test
    fun switchingUnitsOnEmptyFormKeepsWeightBlankAndUntouchedHeightNullOnSave() {
        val form = newForm()
        form.updateName("Alex")
        form.updateUnit(WeightUnit.IMPERIAL)

        val ui = form.uiState.value
        assertEquals(WeightUnit.IMPERIAL, ui.unit)
        assertEquals("", ui.weight)
        assertEquals(5, ui.heightFeet)
        assertEquals(7, ui.heightInches)

        var onSavedCalled = false
        form.save { onSavedCalled = true }

        assertTrue(onSavedCalled)
        assertEquals(listOf("unit", "profile"), events)
        assertEquals(WeightUnit.IMPERIAL, savedUnit)
        assertNull(savedProfile?.weight)
        assertNull(savedProfile?.height)
    }

    @Test
    fun switchingToImperialConvertsUserEnteredWeightAndHeightAndPersistsUnit() {
        val form = newForm()
        form.updateName("Alex")
        form.updateWeight("80")
        form.updateHeight("180")
        form.updateUnit(WeightUnit.IMPERIAL)

        val ui = form.uiState.value
        assertEquals(WeightUnit.IMPERIAL, ui.unit)
        assertEquals("176.4", ui.weight)
        assertEquals(5, ui.heightFeet)
        assertEquals(11, ui.heightInches)

        form.save {}

        assertEquals(WeightUnit.IMPERIAL, savedUnit)
        assertTrue(abs(savedProfile!!.weight!! - 80.0) < 0.05)
        assertTrue(abs(savedProfile!!.height!! - 180.34) < 0.01)
    }

    @Test
    fun switchingUnitsWithImperialInputConvertsToMetricAndSavesMetric() {
        val form = newForm(unit = WeightUnit.IMPERIAL)
        form.updateName("Sam")
        form.updateWeight("150")
        form.updateHeightFeetInches(feet = 5, inches = 10)
        form.updateUnit(WeightUnit.METRIC)

        val ui = form.uiState.value
        assertEquals(WeightUnit.METRIC, ui.unit)
        assertEquals("68", ui.weight)
        assertEquals("177.8", ui.height)

        form.save {}

        assertEquals(WeightUnit.METRIC, savedUnit)
        assertEquals(68.0, savedProfile?.weight)
        assertEquals(177.8, savedProfile?.height)
    }

    @Test
    fun togglingUnitsWithoutEditingPrefilledProfilePreservesExactStoredNumbers() {
        val initial = Profile(
            id = 1L,
            name = "Jordan",
            age = 30,
            gender = "other",
            weight = 80.0,
            height = 180.0,
        )
        val form = newForm(initial = initial, unit = WeightUnit.METRIC)

        form.updateUnit(WeightUnit.IMPERIAL)
        assertEquals("176.4", form.uiState.value.weight)
        assertEquals(5, form.uiState.value.heightFeet)
        assertEquals(11, form.uiState.value.heightInches)

        form.save {}

        assertEquals(WeightUnit.IMPERIAL, savedUnit)
        assertEquals(80.0, savedProfile?.weight)
        assertEquals(180.0, savedProfile?.height)
    }

    @Test
    fun clearingPrefilledMetricHeightThenSwitchingToImperialSavesNull() {
        val initial = Profile(
            id = 1L,
            name = "Jordan",
            age = null,
            gender = null,
            weight = 80.0,
            height = 180.0,
        )
        val form = newForm(initial = initial, unit = WeightUnit.METRIC)
        form.updateHeight("")
        form.updateUnit(WeightUnit.IMPERIAL)
        form.save {}

        assertEquals(WeightUnit.IMPERIAL, savedUnit)
        assertEquals(80.0, savedProfile?.weight)
        assertNull(savedProfile?.height)
    }

    @Test
    fun noopWheelUpdateDoesNotMarkHeightEdited() {
        val form = newForm(unit = WeightUnit.IMPERIAL)
        form.updateName("Alex")
        // Compose WheelPicker fires onSelectedIndex for the initial 5 ft 7 in on open.
        form.updateHeightFeetInches(feet = 5, inches = 7)
        form.save {}

        assertNull(savedProfile?.height)
    }

    @Test
    fun invalidNamePreventsSavingProfileOrUnit() {
        val form = newForm()
        form.updateUnit(WeightUnit.IMPERIAL)
        form.save {}

        assertEquals(ProfileNameError.REQUIRED, form.uiState.value.nameError)
        assertTrue(events.isEmpty())
        assertNull(savedUnit)
        assertNull(savedProfile)
    }
}
