package dev.balaji.moonrose.trainfree.insights

/**
 * Body-mass-index bands. A BMI is assigned to a band by
 * [BmiClassification.of] using the user's selected cutoffs, so the
 * thresholds live there. The UI layers map each band to a localized label
 * and a color for the spectrum chart.
 */
enum class BmiCategory {
    UNDERWEIGHT,
    NORMAL,
    OVERWEIGHT,
    OBESE,
    ;

    companion object {
        /** Display bounds for the horizontal spectrum chart, in BMI units. */
        const val CHART_MIN = 15.0
        const val CHART_MAX = 40.0
    }
}
