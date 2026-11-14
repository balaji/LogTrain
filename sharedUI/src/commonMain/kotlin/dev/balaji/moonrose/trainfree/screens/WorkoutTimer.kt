package dev.balaji.moonrose.trainfree.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Ticks once a second so a running workout timer bound to it recomposes. */
@OptIn(ExperimentalTime::class)
@Composable
fun rememberElapsedSeconds(startedAtMillis: Long): Long {
    var elapsed by remember(startedAtMillis) {
        mutableLongStateOf(elapsedSecondsSince(startedAtMillis))
    }
    LaunchedEffect(startedAtMillis) {
        while (true) {
            delay(1000)
            elapsed = elapsedSecondsSince(startedAtMillis)
        }
    }
    return elapsed
}

@OptIn(ExperimentalTime::class)
private fun elapsedSecondsSince(startedAtMillis: Long): Long =
    ((Clock.System.now().toEpochMilliseconds() - startedAtMillis) / 1000).coerceAtLeast(0)

/** Formats as `m:ss`, or `h:mm:ss` once the workout runs past an hour. */
fun formatElapsed(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.pad()}:${seconds.pad()}"
    } else {
        "${minutes.pad()}:${seconds.pad()}"
    }
}

/** Formats a short countdown (e.g. rest timer) as `m:ss` (e.g. `2:00`, `0:45`). */
fun formatCountdown(totalSeconds: Long): String {
    val clamped = totalSeconds.coerceAtLeast(0)
    val minutes = clamped / 60
    val seconds = clamped % 60
    return "$minutes:${seconds.pad()}"
}

/**
 * Remaining whole seconds until [endsAtMillis], rounded up so the countdown
 * starts at `2:00` immediately and hits `0:00` only when the deadline arrives.
 */
@OptIn(ExperimentalTime::class)
fun remainingSecondsUntil(endsAtMillis: Long, nowMillis: Long = Clock.System.now().toEpochMilliseconds()): Long {
    val remainingMs = endsAtMillis - nowMillis
    if (remainingMs <= 0L) return 0L
    return (remainingMs + 999L) / 1000L
}

private fun Long.pad(): String = if (this < 10) "0$this" else toString()
