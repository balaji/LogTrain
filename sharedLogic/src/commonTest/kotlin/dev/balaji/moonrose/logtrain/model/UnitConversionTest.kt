package dev.balaji.moonrose.logtrain.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnitConversionTest {
    @Test
    fun convertWeightConvertsBetweenMetricAndImperial() {
        assertEquals(100.0, WeightUnit.METRIC.convertWeight(100.0, WeightUnit.METRIC))
        assertEquals(225.0, WeightUnit.IMPERIAL.convertWeight(225.0, WeightUnit.IMPERIAL))

        val lbs = WeightUnit.METRIC.convertWeight(100.0, WeightUnit.IMPERIAL)
        assertTrue(abs(lbs - 220.462262) < 1e-4, "expected ~220.462 lb, got $lbs")

        val kg = WeightUnit.IMPERIAL.convertWeight(lbs, WeightUnit.METRIC)
        assertTrue(abs(kg - 100.0) < 1e-6, "expected 100 kg round-trip, got $kg")
    }

    @Test
    fun convertDistanceConvertsBetweenKilometersAndMiles() {
        assertEquals(5.0, WeightUnit.METRIC.convertDistance(5.0, WeightUnit.METRIC))
        assertEquals(3.1, WeightUnit.IMPERIAL.convertDistance(3.1, WeightUnit.IMPERIAL))

        val miles = WeightUnit.METRIC.convertDistance(5.0, WeightUnit.IMPERIAL)
        assertTrue(abs(miles - 3.106856) < 1e-4, "expected ~3.1069 mi, got $miles")

        val km = WeightUnit.IMPERIAL.convertDistance(miles, WeightUnit.METRIC)
        assertTrue(abs(km - 5.0) < 1e-6, "expected 5 km round-trip, got $km")
    }

    @Test
    fun formatWeightPreservesRecordedPrecisionWhenUnitsMatchAndRoundsCrossUnitToOneDecimal() {
        assertEquals("62.5", WeightUnit.formatWeight(62.5, WeightUnit.METRIC, WeightUnit.METRIC))
        assertEquals("100", WeightUnit.formatWeight(100.0, WeightUnit.METRIC, WeightUnit.METRIC))
        assertEquals("220.5", WeightUnit.formatWeight(100.0, WeightUnit.METRIC, WeightUnit.IMPERIAL))
        assertEquals("100", WeightUnit.formatWeight(220.462, WeightUnit.IMPERIAL, WeightUnit.METRIC))
    }

    @Test
    fun formatDistancePreservesRecordedPrecisionWhenUnitsMatchAndRoundsCrossUnitToOneDecimal() {
        assertEquals("5.25", WeightUnit.formatDistance(5.25, WeightUnit.METRIC, WeightUnit.METRIC))
        assertEquals("3.1", WeightUnit.formatDistance(5.0, WeightUnit.METRIC, WeightUnit.IMPERIAL))
        assertEquals("1.6", WeightUnit.formatDistance(1.0, WeightUnit.IMPERIAL, WeightUnit.METRIC))
    }
}
