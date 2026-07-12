package dev.balaji.moonrose.logtrain.resttimer

/**
 * Platform delivery for rest-timer expiry. Implemented natively per shell
 * (Compose expect/actual on Android, a Swift class on iOS) and injected into
 * `RestTimerController`. Carries no display strings — notification text is
 * owned and localized by each implementation.
 */
interface RestTimerAlerts {
    /**
     * The countdown just reached zero while the app was observing it: play
     * the bell and vibrate. Returns whether the alert was actually delivered —
     * an implementation that suppresses it (e.g. Android backgrounded, where
     * the process keeps ticking) returns false so the controller leaves the
     * scheduled OS notification to fire instead of cancelling it.
     */
    fun timerFinished(): Boolean

    /**
     * Schedule (or replace) the OS notification that fires at [endsAtMillis]
     * even if the app is backgrounded or the device is locked. Implementations
     * check OS-level notification permission themselves and no-op without it.
     */
    fun scheduleExpiryNotification(endsAtMillis: Long)

    /** Remove any scheduled expiry notification. Safe to call when none is pending. */
    fun cancelExpiryNotification()
}
