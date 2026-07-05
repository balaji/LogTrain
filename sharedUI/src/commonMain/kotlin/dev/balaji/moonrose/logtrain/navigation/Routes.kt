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

data object ExercisesRoute : TopLevelRoute {
    override val title get() = "Exercises"
}

data object PlansRoute : TopLevelRoute {
    override val title get() = "Plans"
}

data object ProfileRoute : TopLevelRoute {
    override val title get() = "Profile"
}

data class RoutineDetailRoute(
    val routineId: Long,
    override val title: String = "Routine",
) : Route

data class ExerciseDetailRoute(
    val exerciseId: Long,
    override val title: String = "Exercise",
) : Route

