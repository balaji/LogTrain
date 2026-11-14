package dev.balaji.moonrose.trainfree.db

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Handle to a running observation; call [cancel] when the observer goes away. */
class ProfileObservation internal constructor(private val job: Job) {
    fun cancel() = job.cancel()
}

/**
 * Callback-based bridge for the SwiftUI shell, which can't consume Kotlin Flows.
 */
object ProfileStore {
    fun observeHasProfile(onChange: (Boolean) -> Unit): ProfileObservation {
        val job = CoroutineScope(Dispatchers.Main).launch {
            ProfileRepository.profile.collect { onChange(it != null) }
        }
        return ProfileObservation(job)
    }
}
