package dev.balaji.moonrose.trainfree.db

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Headless bootstrap for automated test runs (scripts/run-*.sh --seed):
 * creates a default profile and generates workout history without driving the
 * UI. No-op when a profile already exists, so a stale launch argument on a
 * non-clean install can't pile more data on top. Debug shells invoke it at
 * startup from a launch argument; fire-and-forget because both shells render
 * reactively off the profile flow.
 */
object DevSeed {
    fun seedIfNoProfile(days: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            if (ProfileRepository.profile.first() != null) return@launch
            ProfileRepository.save(
                name = "Test User",
                age = 35,
                gender = GENDER_KEYS.first(),
                // Display unit defaults to metric on a fresh install.
                weight = 82.0,
                height = 180.0,
            )
            TestDataRepository.generate(days)
        }
    }
}
