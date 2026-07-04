package dev.balaji.moonrose.logtrain.navigation

/**
 * Navigation routes shared by all platforms. Titles live on the routes so the
 * iOS SwiftUI shell can render native navigation bars without calling back
 * into Kotlin. Keep this file free of Compose imports.
 */
sealed interface Route {
    val title: String get() = ""
    val subtitle: String? get() = null
}

sealed interface TopLevelRoute : Route

data object WorkoutsRoute : TopLevelRoute {
    override val title get() = "Workouts"
}

data object PlansRoute : TopLevelRoute {
    override val title get() = "Plans"
}

data object ProfileRoute : TopLevelRoute {
    override val title get() = "Profile"
}

data class PlanDetailRoute(
    val planId: Long,
    override val title: String = "Plan",
) : Route

data class WorkoutDetailRoute(
    val workoutId: Long,
    override val title: String = "Workout",
) : Route
