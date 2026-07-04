package dev.balaji.moonrose.logtrain.navigation

import androidx.compose.runtime.Composable
import dev.balaji.moonrose.logtrain.screens.PlansScreen
import dev.balaji.moonrose.logtrain.screens.ProfileScreen
import dev.balaji.moonrose.logtrain.screens.RoutineDetailScreen
import dev.balaji.moonrose.logtrain.screens.WorkoutDetailScreen
import dev.balaji.moonrose.logtrain.screens.WorkoutsScreen

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
        WorkoutsRoute -> WorkoutsScreen(onNavigate = onNavigate)
        PlansRoute -> PlansScreen(onNavigate = onNavigate)
        ProfileRoute -> ProfileScreen()
        is WorkoutDetailRoute -> WorkoutDetailScreen(route = route, onGoBack = onGoBack)
        is RoutineDetailRoute -> RoutineDetailScreen(route = route, onGoBack = onGoBack)
    }
}
