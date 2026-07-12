package dev.balaji.moonrose.logtrain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RestTimerOptionTest {
    @Test
    fun `fromValue round-trips every option`() {
        RestTimerOption.entries.forEach { option ->
            assertEquals(option, RestTimerOption.fromValue(option.value))
        }
    }

    @Test
    fun `fromValue defaults to 90s for absent or unknown values`() {
        assertEquals(RestTimerOption.S90, RestTimerOption.fromValue(null))
        assertEquals(RestTimerOption.S90, RestTimerOption.fromValue(""))
        assertEquals(RestTimerOption.S90, RestTimerOption.fromValue("45"))
    }

    @Test
    fun `fromSeconds snaps to the nearest option`() {
        assertNull(RestTimerOption.fromSeconds(null))
        assertEquals(RestTimerOption.OFF, RestTimerOption.fromSeconds(0))
        assertEquals(RestTimerOption.S30, RestTimerOption.fromSeconds(30))
        assertEquals(RestTimerOption.S30, RestTimerOption.fromSeconds(40))
        assertEquals(RestTimerOption.S60, RestTimerOption.fromSeconds(50))
        assertEquals(RestTimerOption.S180, RestTimerOption.fromSeconds(9999))
    }
}
