package dev.balaji.moonrose.trainfree.insights

/**
 * BMI classification cutoffs. Persisted as the lowercase [value] string in
 * user_settings. The bands are always the four [BmiCategory] entries; only
 * the boundaries between them differ.
 */
enum class BmiClassification(val value: String) {
    /** WHO international cutoffs: 18.5 / 25 / 30. */
    WHO("who"),

    /** WHO Asian-Pacific cutoffs: 18.5 / 23 / 27.5. */
    ASIAN_PACIFIC("asian_pacific"),
    ;

    /** Inclusive BMI at which [category] begins under these cutoffs. */
    fun lowerBound(category: BmiCategory): Double = when (category) {
        BmiCategory.UNDERWEIGHT -> 0.0
        BmiCategory.NORMAL -> 18.5
        BmiCategory.OVERWEIGHT -> if (this == WHO) 25.0 else 23.0
        BmiCategory.OBESE -> if (this == WHO) 30.0 else 27.5
    }

    /** The band [bmi] falls in: the highest category whose lower bound it clears. */
    fun of(bmi: Double): BmiCategory = BmiCategory.entries.last { bmi >= lowerBound(it) }

    companion object {
        fun fromValue(value: String?): BmiClassification =
            entries.firstOrNull { it.value == value } ?: WHO
    }
}
