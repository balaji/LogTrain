package dev.balaji.moonrose.logtrain.insights

/**
 * WHO body-mass-index bands. [lowerBound] is the inclusive kg/m² at which the
 * band begins; a BMI belongs to the highest band whose lower bound it clears.
 * The UI layers map each band to a localized label and a color for the
 * spectrum chart; the thresholds live here so the platforms can't disagree.
 */
enum class BmiCategory(val lowerBound: Double) {
    UNDERWEIGHT(0.0),
    NORMAL(18.5),
    OVERWEIGHT(25.0),
    OBESE(30.0),
    ;

    companion object {
        fun of(bmi: Double): BmiCategory = entries.last { bmi >= it.lowerBound }

        /** Display bounds for the horizontal spectrum chart, in kg/m². */
        const val CHART_MIN = 15.0
        const val CHART_MAX = 40.0
    }
}
