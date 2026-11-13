package dev.balaji.moonrose.logtrain.navigation

import androidx.compose.runtime.Composable
import dev.balaji.moonrose.logtrain.screens.CalendarScreen
import dev.balaji.moonrose.logtrain.screens.DayDetailScreen
import dev.balaji.moonrose.logtrain.screens.ExerciseCreateScreen
import dev.balaji.moonrose.logtrain.screens.ExerciseDetailScreen
import dev.balaji.moonrose.logtrain.screens.ExercisesScreen
import dev.balaji.moonrose.logtrain.screens.ProfileScreen
import dev.balaji.moonrose.logtrain.screens.ProgramsScreen
import dev.balaji.moonrose.logtrain.screens.ProgressScreen
import dev.balaji.moonrose.logtrain.screens.RoutineDetailScreen
import dev.balaji.moonrose.logtrain.screens.WorkoutSessionScreen

/**
 * Single route-to-screen dispatcher used by every platform entry point:
 * the shared App() scaffold on Android and the per-screen
 * ComposeUIViewControllers on iOS.
 */
@Composable
fun ScreenContent(
    route: Route,
    onNavigate: (Route) -> Unit,
    onGoBack: () -> Unit,
    // Workouts is an app-level overlay (like the iOS sheet), not a route, so
    // opening it is a callback up to the scaffold rather than a navigation.
    onOpenWorkouts: () -> Unit = {},
) {
    when (route) {
        ExercisesRoute -> ExercisesScreen(onNavigate = onNavigate, onOpenWorkouts = onOpenWorkouts)
        WorkoutsRoute -> ProgramsScreen(onNavigate = onNavigate, onGoBack = onGoBack)
        CalendarRoute -> CalendarScreen(
            onDayClick = { epochDay -> onNavigate(DayDetailRoute(epochDay)) },
            onOpenWorkouts = onOpenWorkouts,
        )
        ProgressRoute -> ProgressScreen(onOpenWorkouts = onOpenWorkouts)
        ProfileRoute -> ProfileScreen(onOpenWorkouts = onOpenWorkouts)
        is RoutineDetailRoute -> RoutineDetailScreen(route = route, onNavigate = onNavigate, onGoBack = onGoBack)
        ExerciseCreateRoute -> ExerciseCreateScreen(onGoBack = onGoBack)
        is ExerciseDetailRoute -> ExerciseDetailScreen(route = route, onGoBack = onGoBack)
        is WorkoutSessionRoute -> WorkoutSessionScreen(route = route, onGoBack = onGoBack)
        is DayDetailRoute -> DayDetailScreen(route = route, onGoBack = onGoBack)
    }
}
