package dev.balaji.moonrose.trainfree.insights

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OneRmFormulaTest {
    @Test
    fun `every formula returns the weight itself for singles and zero reps`() {
        for (formula in OneRmFormula.entries) {
            assertEquals(100.0, formula.estimate(100.0, 1), formula.name)
            assertEquals(100.0, formula.estimate(100.0, 0), formula.name)
        }
    }

    @Test
    fun `formulas match their published forms at 100kg x5`() {
        assertTrue(abs(OneRmFormula.EPLEY.estimate(100.0, 5) - 116.6667) < 0.001)
        assertTrue(abs(OneRmFormula.BRZYCKI.estimate(100.0, 5) - 112.5) < 0.001)
        assertTrue(abs(OneRmFormula.LOMBARDI.estimate(100.0, 5) - 117.4619) < 0.001)
        assertTrue(abs(OneRmFormula.OCONNER.estimate(100.0, 5) - 112.5) < 0.001)
    }

    @Test
    fun `epley scales with reps`() {
        assertTrue(abs(OneRmFormula.EPLEY.estimate(60.0, 10) - 80.0) < 0.001)
    }

    @Test
    fun `brzycki clamps absurd rep counts instead of dividing by zero`() {
        val at36 = OneRmFormula.BRZYCKI.estimate(100.0, 36)
        assertEquals(at36, OneRmFormula.BRZYCKI.estimate(100.0, 37))
        assertEquals(at36, OneRmFormula.BRZYCKI.estimate(100.0, 100))
        assertTrue(at36.isFinite() && at36 > 0)
    }

    @Test
    fun `fromValue round-trips every formula and falls back to epley`() {
        for (formula in OneRmFormula.entries) {
            assertEquals(formula, OneRmFormula.fromValue(formula.value))
        }
        assertEquals(OneRmFormula.EPLEY, OneRmFormula.fromValue(null))
        assertEquals(OneRmFormula.EPLEY, OneRmFormula.fromValue("landers"))
    }
}
