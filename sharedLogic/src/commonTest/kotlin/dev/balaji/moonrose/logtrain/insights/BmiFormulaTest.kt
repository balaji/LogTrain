package dev.balaji.moonrose.logtrain.insights

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BmiFormulaTest {
    @Test
    fun `formulas match their published forms at 70kg 175cm`() {
        assertTrue(abs(BmiFormula.STANDARD.bmi(70.0, 175.0) - 22.8571) < 0.001)
        assertTrue(abs(BmiFormula.TREFETHEN.bmi(70.0, 175.0) - 22.4615) < 0.001)
    }

    @Test
    fun `trefethen is exact at one meter where the power term is 1`() {
        assertTrue(abs(BmiFormula.TREFETHEN.bmi(70.0, 100.0) - 91.0) < 0.001)
    }

    @Test
    fun `trefethen reads lower than standard for tall and higher for short`() {
        assertTrue(BmiFormula.TREFETHEN.bmi(70.0, 190.0) < BmiFormula.STANDARD.bmi(70.0, 190.0))
        assertTrue(BmiFormula.TREFETHEN.bmi(70.0, 150.0) > BmiFormula.STANDARD.bmi(70.0, 150.0))
    }

    @Test
    fun `fromValue round-trips every formula and falls back to standard`() {
        for (formula in BmiFormula.entries) {
            assertEquals(formula, BmiFormula.fromValue(formula.value))
        }
        assertEquals(BmiFormula.STANDARD, BmiFormula.fromValue(null))
        assertEquals(BmiFormula.STANDARD, BmiFormula.fromValue("quetelet"))
    }
}
