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

// Not a tab: reached from the floating workout menu's "Choose Program" option.
data object WorkoutsRoute : Route {
    override val title get() = "Workouts"
}

data object CalendarRoute : TopLevelRoute {
    override val title get() = "History"
}

data object ProgressRoute : TopLevelRoute {
    override val title get() = "Progress"
}

data object ProfileRoute : TopLevelRoute {
    override val title get() = "Profile"
}

data class RoutineDetailRoute(
    val routineId: Long,
    override val title: String = "Routine",
    val isNew: Boolean = false,
) : Route

data object ExerciseCreateRoute : Route {
    override val title get() = "New Exercise"
}

data class ExerciseDetailRoute(
    val exerciseId: Long,
    override val title: String = "Exercise",
) : Route

data class WorkoutSessionRoute(
    val routineId: Long,
    override val title: String = "Workout",
) : Route

data class DayDetailRoute(
    val epochDay: Long,
    override val title: String = "Workout",
) : Route
