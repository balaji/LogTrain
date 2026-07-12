package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.resttimer.RestTimerAlerts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class RestTimerUiState(
    val running: Boolean = false,
    val sessionId: Long = 0L,
    // Which row the countdown renders under: the DraftSessionExercise.id
    // (stable per form-state instance, unlike the list index) + set index.
    val anchorExerciseId: Long = -1L,
    val anchorSetIndex: Int = -1,
    val endsAtMillis: Long = 0L,
    // Denominator for the progress bar; shifts with ±10s adjustments so the
    // fraction stays within 0..1.
    val totalSeconds: Int = 0,
    val remainingSeconds: Int = 0,
)

/**
 * The app-wide rest countdown. A singleton on purpose: completing any set
 * replaces whatever countdown was running, so two timers can never coexist.
 * Both the Compose and SwiftUI session screens observe [uiState]; the platform
 * shells inject [alerts] at startup for bell/vibration/notification delivery.
 */
object RestTimerController {
    private val engine = RestTimerEngine(
        scope = CoroutineScope(Dispatchers.Main),
        clock = ::nowMillis,
        persistEndsAt = { sessionId, endsAt ->
            AppDb.db.workoutSessionDao().setRestTimerEndsAt(sessionId, endsAt, nowMillis())
        },
        notificationsEnabled = { UserSettingsRepository.settings.first().notificationsEnabled },
    )

    var alerts: RestTimerAlerts?
        get() = engine.alerts
        set(value) {
            engine.alerts = value
        }

    val uiState: StateFlow<RestTimerUiState> get() = engine.uiState

    /** Starts (or replaces) the countdown. No-op when [seconds] is zero (Off). */
    fun start(sessionId: Long, anchorExerciseId: Long, anchorSetIndex: Int, seconds: Int) =
        engine.start(sessionId, anchorExerciseId, anchorSetIndex, seconds)

    /** Nudges the running countdown; reducing it to zero ends it silently, like Skip. */
    fun adjust(deltaSeconds: Int) = engine.adjust(deltaSeconds)

    /** Ends the countdown without the bell (Skip, session finished/discarded). */
    fun cancel() = engine.cancel()

    /** Cancels only if the countdown is anchored to this set (it was unchecked). */
    fun cancelIfAnchored(exerciseId: Long, setIndex: Int) =
        engine.cancelIfAnchored(exerciseId, setIndex)

    /** Resumes a countdown found mid-rest when a session (re)loads. */
    fun restore(
        sessionId: Long,
        endsAtMillis: Long,
        totalSeconds: Int,
        anchorExerciseId: Long,
        anchorSetIndex: Int,
    ) = engine.restore(sessionId, endsAtMillis, totalSeconds, anchorExerciseId, anchorSetIndex)
}

/**
 * The controller's logic, separated so tests can inject a fake clock and
 * persistence. All entry points are main-thread only, matching how the form
 * states are used.
 */
internal class RestTimerEngine(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val persistEndsAt: suspend (sessionId: Long, endsAt: Long?) -> Unit,
    private val notificationsEnabled: suspend () -> Boolean,
) {
    var alerts: RestTimerAlerts? = null

    private val _uiState = MutableStateFlow(RestTimerUiState())
    val uiState: StateFlow<RestTimerUiState> = _uiState.asStateFlow()

    private var tickJob: Job? = null

    fun start(sessionId: Long, anchorExerciseId: Long, anchorSetIndex: Int, seconds: Int) {
        if (seconds <= 0) return
        val endsAt = clock() + seconds * 1000L
        _uiState.value = RestTimerUiState(
            running = true,
            sessionId = sessionId,
            anchorExerciseId = anchorExerciseId,
            anchorSetIndex = anchorSetIndex,
            endsAtMillis = endsAt,
            totalSeconds = seconds,
            remainingSeconds = seconds,
        )
        persistAndSchedule(sessionId, endsAt)
        startTicking()
    }

    fun adjust(deltaSeconds: Int) {
        val state = _uiState.value
        if (!state.running) return
        val endsAt = state.endsAtMillis + deltaSeconds * 1000L
        if (endsAt <= clock()) {
            // Reduced past "now": the user is done resting. End it like Skip
            // rather than ringing a bell they asked to silence.
            cancel()
            return
        }
        _uiState.value = state.copy(
            endsAtMillis = endsAt,
            totalSeconds = (state.totalSeconds + deltaSeconds).coerceAtLeast(1),
            remainingSeconds = remainingAt(endsAt),
        )
        persistAndSchedule(state.sessionId, endsAt)
    }

    fun cancel() {
        val state = _uiState.value
        if (!state.running) return
        clear(state.sessionId, cancelNotification = true)
    }

    fun cancelIfAnchored(exerciseId: Long, setIndex: Int) {
        val state = _uiState.value
        if (state.running && state.anchorExerciseId == exerciseId && state.anchorSetIndex == setIndex) {
            cancel()
        }
    }

    fun restore(
        sessionId: Long,
        endsAtMillis: Long,
        totalSeconds: Int,
        anchorExerciseId: Long,
        anchorSetIndex: Int,
    ) {
        val remaining = remainingAt(endsAtMillis)
        if (remaining <= 0) {
            // Expired while the app was away; the scheduled notification
            // already told the user. Just clean the persisted row.
            scope.launch { persistEndsAt(sessionId, null) }
            return
        }
        _uiState.value = RestTimerUiState(
            running = true,
            sessionId = sessionId,
            anchorExerciseId = anchorExerciseId,
            anchorSetIndex = anchorSetIndex,
            endsAtMillis = endsAtMillis,
            // A pre-relaunch ±10s adjustment is lost here; clamp so the
            // progress fraction stays valid.
            totalSeconds = totalSeconds.coerceAtLeast(remaining),
            remainingSeconds = remaining,
        )
        // Re-scheduling is idempotent (fixed identity replaces) and covers an
        // alarm/notification lost to a reboot.
        persistAndSchedule(sessionId, endsAtMillis)
        startTicking()
    }

    /** One recompute from the wall clock; fires expiry when the countdown hits zero. */
    internal fun onTick() {
        val state = _uiState.value
        if (!state.running) return
        val remaining = remainingAt(state.endsAtMillis)
        if (remaining > 0) {
            if (remaining != state.remainingSeconds) {
                _uiState.value = state.copy(remainingSeconds = remaining)
            }
            return
        }
        // Ring only when expiry is fresh. Past the grace window the app was
        // suspended through the moment of expiry and the OS notification has
        // already alerted the user — a late bell would double up. Cancel the
        // scheduled notification only when the in-app alert really played;
        // when it was suppressed (Android ticking in the background), the
        // notification is the alert.
        val fresh = clock() - state.endsAtMillis <= EXPIRY_GRACE_MS
        val delivered = fresh && alerts?.timerFinished() == true
        clear(state.sessionId, cancelNotification = delivered)
    }

    private fun clear(sessionId: Long, cancelNotification: Boolean) {
        tickJob?.cancel()
        tickJob = null
        _uiState.value = RestTimerUiState()
        if (cancelNotification) alerts?.cancelExpiryNotification()
        scope.launch { persistEndsAt(sessionId, null) }
    }

    private fun remainingAt(endsAtMillis: Long): Int {
        val millisLeft = endsAtMillis - clock()
        return if (millisLeft <= 0) 0 else ((millisLeft + 999) / 1000).toInt()
    }

    private fun persistAndSchedule(sessionId: Long, endsAt: Long) {
        scope.launch {
            persistEndsAt(sessionId, endsAt)
            if (notificationsEnabled()) {
                alerts?.scheduleExpiryNotification(endsAt)
            } else {
                alerts?.cancelExpiryNotification()
            }
        }
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (_uiState.value.running) {
                delay(1000)
                onTick()
            }
        }
    }
}

// How long after the scheduled end an expiry still counts as observed live.
// Ticks run every second, so anything beyond a few seconds means the process
// was suspended through the expiry.
private const val EXPIRY_GRACE_MS = 3_000L
