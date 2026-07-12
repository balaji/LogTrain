package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.resttimer.RestTimerAlerts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

private class FakeAlerts : RestTimerAlerts {
    var finishedCount = 0
    var deliverInApp = true
    val scheduled = mutableListOf<Long>()
    var cancelCount = 0

    override fun timerFinished(): Boolean {
        finishedCount++
        return deliverInApp
    }

    override fun scheduleExpiryNotification(endsAtMillis: Long) {
        scheduled.add(endsAtMillis)
    }

    override fun cancelExpiryNotification() {
        cancelCount++
    }
}

/**
 * Drives the engine with a fake clock and calls [RestTimerEngine.onTick]
 * directly instead of waiting out the real 1s tick loop. The Unconfined scope
 * runs the engine's internal launches (persistence, notification scheduling)
 * inline, so effects are observable immediately after each call.
 */
class RestTimerEngineTest {
    private var now = 1_000_000L
    private var notificationsEnabled = true
    private val persisted = mutableListOf<Pair<Long, Long?>>()
    private val alerts = FakeAlerts()
    private val engine = RestTimerEngine(
        scope = CoroutineScope(Dispatchers.Unconfined),
        clock = { now },
        persistEndsAt = { sessionId, endsAt -> persisted.add(sessionId to endsAt) },
        notificationsEnabled = { notificationsEnabled },
    ).also { it.alerts = alerts }

    @Test
    fun startRunsPersistsAndSchedules() {
        engine.start(sessionId = 7L, anchorExerciseId = 3L, anchorSetIndex = 1, seconds = 90)

        val state = engine.uiState.value
        assertTrue(state.running)
        assertEquals(7L, state.sessionId)
        assertEquals(3L, state.anchorExerciseId)
        assertEquals(1, state.anchorSetIndex)
        assertEquals(now + 90_000, state.endsAtMillis)
        assertEquals(90, state.totalSeconds)
        assertEquals(90, state.remainingSeconds)
        assertEquals(listOf<Pair<Long, Long?>>(7L to now + 90_000), persisted)
        assertEquals(listOf(now + 90_000), alerts.scheduled)
    }

    @Test
    fun startWithZeroSecondsIsOff() {
        engine.start(7L, 3L, 1, seconds = 0)
        assertFalse(engine.uiState.value.running)
        assertTrue(persisted.isEmpty())
    }

    @Test
    fun secondStartReplacesTheFirst() {
        engine.start(7L, 3L, 1, seconds = 90)
        now += 20_000
        engine.start(7L, 5L, 0, seconds = 60)

        val state = engine.uiState.value
        assertEquals(5L, state.anchorExerciseId)
        assertEquals(0, state.anchorSetIndex)
        assertEquals(now + 60_000, state.endsAtMillis)
        assertEquals(60, state.remainingSeconds)
    }

    @Test
    fun adjustMovesEndAndTotalAndReschedules() {
        engine.start(7L, 3L, 1, seconds = 90)
        engine.adjust(10)

        val state = engine.uiState.value
        assertEquals(now + 100_000, state.endsAtMillis)
        assertEquals(100, state.totalSeconds)
        assertEquals(100, state.remainingSeconds)
        assertEquals(listOf(now + 90_000, now + 100_000), alerts.scheduled)

        engine.adjust(-10)
        assertEquals(now + 90_000, engine.uiState.value.endsAtMillis)
        assertEquals(90, engine.uiState.value.totalSeconds)
    }

    @Test
    fun adjustingBelowZeroEndsSilently() {
        engine.start(7L, 3L, 1, seconds = 30)
        now += 25_000
        engine.adjust(-10)

        assertFalse(engine.uiState.value.running)
        assertEquals(0, alerts.finishedCount)
        assertTrue(alerts.cancelCount > 0)
        assertEquals(7L to null, persisted.last())
    }

    @Test
    fun cancelIfAnchoredOnlyCancelsMatchingSet() {
        engine.start(7L, 3L, 1, seconds = 90)
        engine.cancelIfAnchored(exerciseId = 3L, setIndex = 0)
        assertTrue(engine.uiState.value.running)
        engine.cancelIfAnchored(exerciseId = 4L, setIndex = 1)
        assertTrue(engine.uiState.value.running)

        engine.cancelIfAnchored(exerciseId = 3L, setIndex = 1)
        assertFalse(engine.uiState.value.running)
        assertEquals(0, alerts.finishedCount)
    }

    @Test
    fun freshExpiryRingsOnceAndCancelsNotification() {
        engine.start(7L, 3L, 1, seconds = 30)
        now += 29_000
        engine.onTick()
        assertEquals(1, engine.uiState.value.remainingSeconds)
        assertEquals(0, alerts.finishedCount)

        now += 2_000
        val cancelsBefore = alerts.cancelCount
        engine.onTick()
        assertEquals(1, alerts.finishedCount)
        assertTrue(alerts.cancelCount > cancelsBefore)
        assertFalse(engine.uiState.value.running)
        assertEquals(7L to null, persisted.last())

        engine.onTick()
        assertEquals(1, alerts.finishedCount)
    }

    @Test
    fun suppressedInAppAlertLeavesNotificationScheduled() {
        // Android backgrounded: the process keeps ticking but the impl
        // suppresses the bell — the scheduled notification must survive to
        // be the alert.
        alerts.deliverInApp = false
        engine.start(7L, 3L, 1, seconds = 30)
        now += 31_000
        val cancelsBefore = alerts.cancelCount
        engine.onTick()

        assertEquals(1, alerts.finishedCount)
        assertEquals(cancelsBefore, alerts.cancelCount)
        assertFalse(engine.uiState.value.running)
    }

    @Test
    fun staleExpiryDoesNotRing() {
        engine.start(7L, 3L, 1, seconds = 30)
        // Clock jumps far past the end, as after a background suspension: the
        // OS notification already alerted the user.
        now += 120_000
        val cancelsBefore = alerts.cancelCount
        engine.onTick()

        assertEquals(0, alerts.finishedCount)
        assertEquals(cancelsBefore, alerts.cancelCount)
        assertFalse(engine.uiState.value.running)
        assertEquals(7L to null, persisted.last())
    }

    @Test
    fun restoreResumesFutureCountdown() {
        engine.restore(
            sessionId = 7L,
            endsAtMillis = now + 45_000,
            totalSeconds = 30,
            anchorExerciseId = 2L,
            anchorSetIndex = 0,
        )

        val state = engine.uiState.value
        assertTrue(state.running)
        assertEquals(45, state.remainingSeconds)
        // Total is clamped up so progress stays within 0..1.
        assertEquals(45, state.totalSeconds)
        assertEquals(listOf(now + 45_000), alerts.scheduled)
    }

    @Test
    fun restoreOfExpiredCountdownJustCleansUp() {
        engine.restore(7L, endsAtMillis = now - 1, totalSeconds = 90, anchorExerciseId = 2L, anchorSetIndex = 0)
        assertFalse(engine.uiState.value.running)
        assertEquals(0, alerts.finishedCount)
        assertEquals(listOf<Pair<Long, Long?>>(7L to null), persisted)
    }

    @Test
    fun notificationsDisabledSchedulesNothing() {
        notificationsEnabled = false
        engine.start(7L, 3L, 1, seconds = 90)
        assertTrue(alerts.scheduled.isEmpty())
        assertTrue(engine.uiState.value.running)
    }
}
