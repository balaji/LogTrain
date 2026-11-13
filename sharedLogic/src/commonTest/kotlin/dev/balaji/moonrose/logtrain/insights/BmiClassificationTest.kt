package dev.balaji.moonrose.logtrain.insights

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BmiClassificationTest {
    @Test
    fun `who boundaries are inclusive at 18_5 and 25 and 30`() {
        assertEquals(BmiCategory.UNDERWEIGHT, BmiClassification.WHO.of(18.49))
        assertEquals(BmiCategory.NORMAL, BmiClassification.WHO.of(18.5))
        assertEquals(BmiCategory.NORMAL, BmiClassification.WHO.of(24.99))
        assertEquals(BmiCategory.OVERWEIGHT, BmiClassification.WHO.of(25.0))
        assertEquals(BmiCategory.OVERWEIGHT, BmiClassification.WHO.of(29.99))
        assertEquals(BmiCategory.OBESE, BmiClassification.WHO.of(30.0))
    }

    @Test
    fun `asian pacific boundaries are inclusive at 18_5 and 23 and 27_5`() {
        assertEquals(BmiCategory.UNDERWEIGHT, BmiClassification.ASIAN_PACIFIC.of(18.49))
        assertEquals(BmiCategory.NORMAL, BmiClassification.ASIAN_PACIFIC.of(18.5))
        assertEquals(BmiCategory.NORMAL, BmiClassification.ASIAN_PACIFIC.of(22.99))
        assertEquals(BmiCategory.OVERWEIGHT, BmiClassification.ASIAN_PACIFIC.of(23.0))
        assertEquals(BmiCategory.OVERWEIGHT, BmiClassification.ASIAN_PACIFIC.of(27.49))
        assertEquals(BmiCategory.OBESE, BmiClassification.ASIAN_PACIFIC.of(27.5))
    }

    @Test
    fun `same bmi can classify differently between cutoff sets`() {
        assertEquals(BmiCategory.NORMAL, BmiClassification.WHO.of(24.0))
        assertEquals(BmiCategory.OVERWEIGHT, BmiClassification.ASIAN_PACIFIC.of(24.0))
        assertEquals(BmiCategory.OVERWEIGHT, BmiClassification.WHO.of(28.0))
        assertEquals(BmiCategory.OBESE, BmiClassification.ASIAN_PACIFIC.of(28.0))
    }

    @Test
    fun `lower bounds are strictly increasing within each classification`() {
        for (classification in BmiClassification.entries) {
            BmiCategory.entries.zipWithNext { lower, upper ->
                assertTrue(
                    classification.lowerBound(lower) < classification.lowerBound(upper),
                    "${classification.name}: ${lower.name} vs ${upper.name}",
                )
            }
        }
    }

    @Test
    fun `fromValue round-trips every classification and falls back to who`() {
        for (classification in BmiClassification.entries) {
            assertEquals(classification, BmiClassification.fromValue(classification.value))
        }
        assertEquals(BmiClassification.WHO, BmiClassification.fromValue(null))
        assertEquals(BmiClassification.WHO, BmiClassification.fromValue("cdc"))
    }
}
