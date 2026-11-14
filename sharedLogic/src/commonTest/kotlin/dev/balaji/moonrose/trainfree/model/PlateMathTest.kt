package dev.balaji.moonrose.trainfree.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlateMathTest {
    @Test
    fun plateOptionsOrderedSmallToLargePerUnit() {
        assertEquals(
            listOf(1.25, 2.5, 5.0, 10.0, 15.0, 20.0, 25.0),
            PlateMath.plateOptions(WeightUnit.METRIC).map { it.value },
        )
        assertEquals(
            listOf(2.5, 5.0, 10.0, 25.0, 35.0, 45.0, 55.0),
            PlateMath.plateOptions(WeightUnit.IMPERIAL).map { it.value },
        )
    }

    @Test
    fun barOptionsIncludeStandardBars() {
        assertEquals(
            listOf(25.0, 20.0, 15.0, 10.0),
            PlateMath.barOptions(WeightUnit.METRIC).map { it.value },
        )
        assertEquals(
            listOf(45.0, 35.0, 33.0, 15.0),
            PlateMath.barOptions(WeightUnit.IMPERIAL).map { it.value },
        )
    }

    @Test
    fun defaultBarIsOlympic() {
        assertEquals(20.0, PlateMath.defaultBar(WeightUnit.METRIC))
        assertEquals(45.0, PlateMath.defaultBar(WeightUnit.IMPERIAL))
    }

    @Test
    fun totalCountsPlatesTwice() {
        assertEquals(20.0, PlateMath.total(20.0, emptyList()))
        assertEquals(100.0, PlateMath.total(20.0, listOf(25.0, 15.0)))
        assertEquals(62.5, PlateMath.total(20.0, listOf(20.0, 1.25)))
    }

    @Test
    fun formatWeightDropsTrailingZero() {
        assertEquals("60", PlateMath.formatWeight(60.0))
        assertEquals("62.5", PlateMath.formatWeight(62.5))
        assertEquals("2.5", PlateMath.formatWeight(2.5))
        assertEquals("0", PlateMath.formatWeight(0.0))
    }

    @Test
    fun optionLabelsMatchFormatting() {
        PlateMath.plateOptions(WeightUnit.METRIC).forEach {
            assertEquals(PlateMath.formatWeight(it.value), it.label)
        }
    }

    @Test
    fun decomposeRoundTripsMetric() {
        assertEquals(listOf(25.0, 15.0), PlateMath.decompose(100.0, 20.0, WeightUnit.METRIC))
        assertEquals(listOf(20.0, 1.25), PlateMath.decompose(62.5, 20.0, WeightUnit.METRIC))
        assertEquals(emptyList(), PlateMath.decompose(20.0, 20.0, WeightUnit.METRIC))
    }

    @Test
    fun decomposeRoundTripsImperial() {
        assertEquals(listOf(55.0, 25.0), PlateMath.decompose(205.0, 45.0, WeightUnit.IMPERIAL))
        assertEquals(listOf(45.0, 2.5), PlateMath.decompose(140.0, 45.0, WeightUnit.IMPERIAL))
        assertEquals(listOf(2.5), PlateMath.decompose(50.0, 45.0, WeightUnit.IMPERIAL))
    }

    @Test
    fun decomposeEveryLoadableTotalRoundTrips() {
        // Every multiple of the smallest plate pair up to a heavy load must
        // decompose back to exactly that total.
        val bar = 20.0
        var perSide = 0.0
        while (perSide <= 200.0) {
            val total = bar + 2 * perSide
            val plates = PlateMath.decompose(total, bar, WeightUnit.METRIC)!!
            assertEquals(total, PlateMath.total(bar, plates))
            perSide += 1.25
        }
    }

    @Test
    fun decomposeRejectsUnloadableValues() {
        assertNull(PlateMath.decompose(62.3, 20.0, WeightUnit.METRIC))
        assertNull(PlateMath.decompose(15.0, 20.0, WeightUnit.METRIC)) // below the bar
        assertNull(PlateMath.decompose(46.0, 45.0, WeightUnit.IMPERIAL)) // 0.5/side
    }
}
