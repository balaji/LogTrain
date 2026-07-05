package dev.balaji.moonrose.logtrain.navigation

import androidx.compose.runtime.Composable
import dev.balaji.moonrose.logtrain.screens.ExerciseDetailScreen
import dev.balaji.moonrose.logtrain.screens.ExercisesScreen
import dev.balaji.moonrose.logtrain.screens.ProgramsScreen
import dev.balaji.moonrose.logtrain.screens.ProfileScreen
import dev.balaji.moonrose.logtrain.screens.RoutineDetailScreen
import dev.balaji.moonrose.logtrain.screens.WorkoutSessionScreen

/**
 * Single route-to-screen dispatcher used by every platform entry point:
 * the shared App() scaffold on Android/Desktop and the per-screen
 * ComposeUIViewControllers on iOS.
 */
@Composable
fun ScreenContent(
    route: Route,
    onNavigate: (Route) -> Unit,
    onGoBack: () -> Unit,
) {
    when (route) {
        ExercisesRoute -> ExercisesScreen(onNavigate = onNavigate)
        WorkoutsRoute -> ProgramsScreen(onNavigate = onNavigate)
        ProfileRoute -> ProfileScreen()
        is RoutineDetailRoute -> RoutineDetailScreen(route = route, onNavigate = onNavigate, onGoBack = onGoBack)
        is ExerciseDetailRoute -> ExerciseDetailScreen(route = route, onGoBack = onGoBack)
        is WorkoutSessionRoute -> WorkoutSessionScreen(route = route, onGoBack = onGoBack)
    }
}
