package dev.balaji.moonrose.logtrain.model

import kotlin.math.abs

/**
 * One selectable plate or bar weight. [label] is the bare numeral ("2.5"),
 * locale-neutral; unit suffixes are the UI's job.
 */
data class PlateOption(val value: Double, val label: String)

/**
 * Barbell loading math for the plate keyboard. All weights are in the user's
 * display unit — no kg/lb conversion happens here; [WeightUnit] only selects
 * which plate and bar denominations exist.
 */
object PlateMath {
    private val METRIC_PLATES = listOf(1.25, 2.5, 5.0, 10.0, 15.0, 20.0, 25.0)
    private val IMPERIAL_PLATES = listOf(2.5, 5.0, 10.0, 25.0, 35.0, 45.0, 55.0)

    // Common bars: olympic, women's olympic, technique/EZ variants.
    private val METRIC_BARS = listOf(25.0, 20.0, 15.0, 10.0)
    private val IMPERIAL_BARS = listOf(45.0, 35.0, 33.0, 15.0)

    /** Tolerance for values parsed from user-typed strings. */
    private const val EPSILON = 1e-6

    /** Plate denominations for [unit], ordered small to large. */
    fun plateOptions(unit: WeightUnit): List<PlateOption> =
        platesFor(unit).map { PlateOption(it, formatWeight(it)) }

    /** Bar weights for [unit], ordered large to small (default first-ish). */
    fun barOptions(unit: WeightUnit): List<PlateOption> =
        when (unit) {
            WeightUnit.METRIC -> METRIC_BARS
            WeightUnit.IMPERIAL -> IMPERIAL_BARS
        }.map { PlateOption(it, formatWeight(it)) }

    /** The standard olympic bar: 20 kg / 45 lb. */
    fun defaultBar(unit: WeightUnit): Double =
        when (unit) {
            WeightUnit.METRIC -> 20.0
            WeightUnit.IMPERIAL -> 45.0
        }

    /** Plates load in pairs: bar plus one of each per-side plate on each end. */
    fun total(bar: Double, platesPerSide: List<Double>): Double =
        bar + 2 * platesPerSide.sum()

    /** Renders a weight without a trailing ".0" ("60", "62.5"). */
    fun formatWeight(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    /**
     * Splits an existing [total] back into per-side plates on top of [bar],
     * largest plate first. Returns null when the value can't be loaded from
     * the [unit]'s denominations (total below the bar, or the per-side
     * remainder isn't a multiple of the smallest plate). Greedy large→small is
     * exact here because the smallest plate divides every larger one in both
     * unit sets.
     */
    fun decompose(total: Double, bar: Double, unit: WeightUnit): List<Double>? {
        var perSide = (total - bar) / 2
        if (perSide < -EPSILON) return null
        val plates = mutableListOf<Double>()
        for (plate in platesFor(unit).asReversed()) {
            while (perSide >= plate - EPSILON) {
                plates.add(plate)
                perSide -= plate
            }
        }
        return if (abs(perSide) <= EPSILON) plates else null
    }

    private fun platesFor(unit: WeightUnit): List<Double> =
        when (unit) {
            WeightUnit.METRIC -> METRIC_PLATES
            WeightUnit.IMPERIAL -> IMPERIAL_PLATES
        }
}
