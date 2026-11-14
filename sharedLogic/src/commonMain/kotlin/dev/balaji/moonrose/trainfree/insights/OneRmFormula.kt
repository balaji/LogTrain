package dev.balaji.moonrose.trainfree.insights

import kotlin.math.min
import kotlin.math.pow

/**
 * One-rep-max estimation formula. Persisted as the lowercase [value] string
 * in user_settings; the user picks one in the 1RM calculator and every
 * insight that shows or ranks an estimated 1RM uses it.
 */
enum class OneRmFormula(val value: String) {
    EPLEY("epley"),
    BRZYCKI("brzycki"),
    LOMBARDI("lombardi"),
    OCONNER("oconner"),
    ;

    /**
     * Estimated one-rep max; the weight itself for singles (and non-positive
     * reps). Brzycki's denominator (37 - reps) hits zero at 37 reps, so reps
     * are clamped to 36 — deterministic and monotonic for absurd rep counts.
     */
    fun estimate(weight: Double, reps: Int): Double {
        if (reps <= 1) return weight
        return when (this) {
            EPLEY -> weight * (1.0 + reps / 30.0)
            BRZYCKI -> weight * 36.0 / (37.0 - min(reps, 36))
            LOMBARDI -> weight * reps.toDouble().pow(0.1)
            OCONNER -> weight * (1.0 + reps / 40.0)
        }
    }

    companion object {
        fun fromValue(value: String?): OneRmFormula = entries.firstOrNull { it.value == value } ?: EPLEY
    }
}
