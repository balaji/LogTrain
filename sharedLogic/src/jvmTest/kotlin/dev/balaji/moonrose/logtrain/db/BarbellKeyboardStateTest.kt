package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.model.WeightUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

private class FakeBarWeightStore : BarWeightStore {
    val stored = mutableMapOf<Pair<Long, WeightUnit>, Double>()

    override suspend fun get(exerciseId: Long, unit: WeightUnit): Double? =
        stored[exerciseId to unit]

    override suspend fun set(exerciseId: Long, unit: WeightUnit, weight: Double) {
        stored[exerciseId to unit] = weight
    }
}

class BarbellKeyboardStateTest {
    private val writes = mutableListOf<Triple<Int, Int, String>>()
    private val store = FakeBarWeightStore()
    private var unit = WeightUnit.METRIC
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun state() = BarbellKeyboardState(
        sink = { ex, set, value -> writes.add(Triple(ex, set, value)) },
        store = store,
        unitProvider = { unit },
        scope = scope,
    )

    @Test
    fun `opening an empty field shows plate mode without writing`() {
        val kb = state()
        kb.open(exerciseIndex = 0, setIndex = 1, exerciseId = 5L, currentValue = "")

        val s = kb.uiState.value
        assertTrue(s.visible)
        assertFalse(s.numericMode)
        assertEquals(20.0, s.barWeight)
        assertTrue(s.platesPerSide.isEmpty())
        assertEquals(listOf(1.25, 2.5, 5.0, 10.0, 15.0, 20.0, 25.0), s.plateOptions.map { it.value })
        assertTrue(writes.isEmpty())
    }

    @Test
    fun `opening a loadable value seeds the plate breakdown`() {
        val kb = state()
        kb.open(0, 0, 5L, "100")

        val s = kb.uiState.value
        assertFalse(s.numericMode)
        assertEquals(listOf(25.0, 15.0), s.platesPerSide.map { it.value })
        assertEquals("100", s.totalLabel)
        assertTrue(writes.isEmpty())
    }

    @Test
    fun `opening an unloadable value falls back to numeric mode`() {
        val kb = state()
        kb.open(0, 0, 5L, "62.3")
        assertTrue(kb.uiState.value.numericMode)

        kb.open(0, 1, 5L, "15") // below the bar
        assertTrue(kb.uiState.value.numericMode)
    }

    @Test
    fun `plate taps write bar plus twice the plates`() {
        val kb = state()
        kb.open(2, 3, 5L, "")
        kb.addPlate(20.0)
        assertEquals(Triple(2, 3, "60"), writes.last())
        kb.addPlate(5.0)
        assertEquals(Triple(2, 3, "70"), writes.last())

        kb.removePlateAt(1)
        assertEquals(Triple(2, 3, "60"), writes.last())
        kb.removeLastPlate()
        assertEquals(Triple(2, 3, "20"), writes.last())
    }

    @Test
    fun `selecting a bar weight recomputes and is remembered per exercise and unit`() {
        val kb = state()
        kb.open(0, 0, 5L, "")
        kb.addPlate(10.0)
        kb.selectBarWeight(15.0)
        assertEquals("35", writes.last().third)
        assertEquals(15.0, store.stored[5L to WeightUnit.METRIC])

        // Reopening the same exercise restores the remembered bar.
        kb.open(0, 1, 5L, "")
        assertEquals(15.0, kb.uiState.value.barWeight)

        // A different exercise still gets the default.
        kb.open(1, 0, 9L, "")
        assertEquals(20.0, kb.uiState.value.barWeight)
    }

    @Test
    fun `imperial unit swaps plate set and default bar`() {
        unit = WeightUnit.IMPERIAL
        val kb = state()
        kb.open(0, 0, 5L, "")

        val s = kb.uiState.value
        assertEquals(45.0, s.barWeight)
        assertEquals(listOf(2.5, 5.0, 10.0, 25.0, 35.0, 45.0, 55.0), s.plateOptions.map { it.value })
        kb.addPlate(45.0)
        assertEquals("135", writes.last().third)
    }

    @Test
    fun `numeric keypad edits share the decimal sanitization`() {
        val kb = state()
        kb.open(0, 0, 5L, "62.3")
        assertTrue(kb.uiState.value.numericMode)

        kb.appendNumericChar("5")
        assertEquals("62.35", writes.last().third)
        kb.deleteNumericChar()
        assertEquals("62.3", writes.last().third)
        // A second dot is filtered out.
        kb.appendNumericChar(".")
        assertEquals("62.3", writes.last().third)
    }

    @Test
    fun `leaving numeric mode re-decomposes a loadable value`() {
        val kb = state()
        kb.open(0, 0, 5L, "62.3")
        kb.deleteNumericChar() // "62."
        kb.deleteNumericChar() // "62"
        kb.deleteNumericChar() // "6"
        kb.appendNumericChar("0") // "60"
        kb.setNumericMode(false)

        val s = kb.uiState.value
        assertFalse(s.numericMode)
        assertEquals(listOf(20.0), s.platesPerSide.map { it.value })
        assertEquals("60", s.totalLabel)
    }

    @Test
    fun `leaving numeric mode with an unloadable value resets to bar only without writing`() {
        val kb = state()
        kb.open(0, 0, 5L, "62.3")
        writes.clear()
        kb.setNumericMode(false)

        val s = kb.uiState.value
        assertFalse(s.numericMode)
        assertTrue(s.platesPerSide.isEmpty())
        // Field text untouched until the next plate tap.
        assertTrue(writes.isEmpty())
        kb.addPlate(5.0)
        assertEquals("30", writes.last().third)
    }

    @Test
    fun `close hides the keyboard and clears the target cell`() {
        val kb = state()
        kb.open(0, 0, 5L, "")
        kb.close()

        val s = kb.uiState.value
        assertFalse(s.visible)
        assertEquals(-1, s.exerciseIndex)
        assertEquals(-1, s.setIndex)
    }

    @Test
    fun `plate breakdown groups by denomination, largest first, counting both sides`() {
        val kb = state()
        kb.open(0, 0, 5L, "")
        kb.addPlate(2.5)
        kb.addPlate(20.0)
        kb.addPlate(2.5)
        kb.addPlate(2.5)

        val breakdown = kb.uiState.value.plateBreakdown
        assertEquals(listOf(20.0, 2.5), breakdown.map { it.option.value })
        assertEquals(listOf(2, 6), breakdown.map { it.count })
    }

    @Test
    fun `removePlate removes one tap of that denomination regardless of order`() {
        val kb = state()
        kb.open(0, 0, 5L, "")
        kb.addPlate(2.5)
        kb.addPlate(20.0)
        kb.addPlate(2.5)

        kb.removePlate(2.5)
        val breakdown = kb.uiState.value.plateBreakdown
        assertEquals(listOf(20.0, 2.5), breakdown.map { it.option.value })
        assertEquals(listOf(2, 2), breakdown.map { it.count })
        assertEquals("65", writes.last().third) // bar 20 + 2*(20+2.5)

        // Removing a denomination that isn't loaded is a no-op.
        kb.removePlate(15.0)
        assertEquals("65", writes.last().third)
    }

    @Test
    fun `reopening on another set retargets writes`() {
        val kb = state()
        kb.open(0, 0, 5L, "")
        kb.addPlate(10.0)
        kb.open(1, 2, 9L, "")
        kb.addPlate(5.0)
        assertEquals(Triple(1, 2, "30"), writes.last())
    }
}
