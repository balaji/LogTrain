package dev.balaji.moonrose.logtrain.model

import kotlin.math.roundToInt

/** A height split into whole feet and the remaining whole inches. */
data class FeetInches(val feet: Int, val inches: Int)

/**
 * Height is stored in centimeters everywhere; this converts to and from the
 * feet/inches the BMI calculator shows when the user picks imperial units. It
 * is the length-unit counterpart to [WeightUnit]'s kilogram conversions.
 */
object HeightConversion {
    const val CM_PER_INCH = 2.54
    private const val INCHES_PER_FOOT = 12

    fun cmToFeetInches(cm: Double): FeetInches {
        val totalInches = (cm / CM_PER_INCH).roundToInt()
        return FeetInches(totalInches / INCHES_PER_FOOT, totalInches % INCHES_PER_FOOT)
    }

    fun feetInchesToCm(feet: Int, inches: Int): Double =
        (feet * INCHES_PER_FOOT + inches) * CM_PER_INCH
}
