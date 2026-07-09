package dev.balaji.moonrose.logtrain.model

import dev.balaji.moonrose.logtrain.calendar.WeekStart
import kotlin.test.Test
import kotlin.test.assertEquals

class UserSettingsTest {
    @Test
    fun weekStartFromValue() {
        assertEquals(WeekStart.MONDAY, WeekStart.fromValue("monday"))
        assertEquals(WeekStart.SUNDAY, WeekStart.fromValue("sunday"))
        assertEquals(WeekStart.SUNDAY, WeekStart.fromValue(null))
        assertEquals(WeekStart.SUNDAY, WeekStart.fromValue("garbage"))
    }

    @Test
    fun appearanceFromValue() {
        assertEquals(Appearance.LIGHT, Appearance.fromValue("light"))
        assertEquals(Appearance.DARK, Appearance.fromValue("dark"))
        assertEquals(Appearance.SYSTEM, Appearance.fromValue("system"))
        assertEquals(Appearance.SYSTEM, Appearance.fromValue(null))
        assertEquals(Appearance.SYSTEM, Appearance.fromValue("sepia"))
    }

    @Test
    fun weightUnitFromValue() {
        assertEquals(WeightUnit.METRIC, WeightUnit.fromValue("metric"))
        assertEquals(WeightUnit.IMPERIAL, WeightUnit.fromValue("imperial"))
        assertEquals(WeightUnit.METRIC, WeightUnit.fromValue(null))
        assertEquals(WeightUnit.METRIC, WeightUnit.fromValue("stone"))
    }

    @Test
    fun everyEnumEntryRoundTripsThroughItsValueKey() {
        WeekStart.entries.forEach { assertEquals(it, WeekStart.fromValue(it.value)) }
        Appearance.entries.forEach { assertEquals(it, Appearance.fromValue(it.value)) }
        WeightUnit.entries.forEach { assertEquals(it, WeightUnit.fromValue(it.value)) }
    }
}
