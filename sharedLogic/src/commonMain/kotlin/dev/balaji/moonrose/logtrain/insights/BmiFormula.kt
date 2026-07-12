package dev.balaji.moonrose.logtrain.insights

import kotlin.math.pow

/**
 * Body-mass-index formula. Persisted as the lowercase [value] string in
 * user_settings; the user picks one in the BMI calculator and everywhere a
 * BMI is computed or shown uses it. Both formulas produce values on the same
 * scale, so the same [BmiClassification] cutoffs apply to either.
 */
enum class BmiFormula(val value: String) {
    /** Quetelet's classic index: kg / m². */
    STANDARD("standard"),

    /**
     * Trefethen's 2013 "New BMI": 1.3 · kg / m^2.5. Scales with height^2.5,
     * so it reads slightly higher than the standard index for short people
     * and lower for tall people.
     */
    TREFETHEN("trefethen"),
    ;

    /** Body mass index from kilograms and centimeters. */
    fun bmi(weightKg: Double, heightCm: Double): Double {
        val heightM = heightCm / 100.0
        return when (this) {
            STANDARD -> weightKg / (heightM * heightM)
            TREFETHEN -> 1.3 * weightKg / heightM.pow(2.5)
        }
    }

    companion object {
        fun fromValue(value: String?): BmiFormula = entries.firstOrNull { it.value == value } ?: STANDARD
    }
}
