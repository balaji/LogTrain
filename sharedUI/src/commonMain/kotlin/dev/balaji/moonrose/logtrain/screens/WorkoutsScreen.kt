package dev.balaji.moonrose.logtrain.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation
import dev.balaji.moonrose.logtrain.navigation.Route
import dev.balaji.moonrose.logtrain.navigation.WorkoutDetailRoute

private data class WorkoutPlaceholder(val id: Long, val name: String, val summary: String)

private val placeholderWorkouts = listOf(
    WorkoutPlaceholder(1, "Push Day", "Bench press, overhead press, dips"),
    WorkoutPlaceholder(2, "Pull Day", "Deadlift, rows, pull-ups"),
    WorkoutPlaceholder(3, "Leg Day", "Squat, lunges, calf raises"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutsScreen(onNavigate: (Route) -> Unit) {
    if (LocalUseNativeNavigation.current) {
        WorkoutsList(
            contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
            onNavigate = onNavigate,
        )
    } else {
        Scaffold(topBar = { TopAppBar(title = { Text("Workouts") }) }) { padding ->
            WorkoutsList(contentPadding = padding, onNavigate = onNavigate)
        }
    }
}

@Composable
private fun WorkoutsList(
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onNavigate: (Route) -> Unit,
) {
    LazyColumn(contentPadding = contentPadding) {
        items(placeholderWorkouts, key = { it.id }) { workout ->
            ListItem(
                headlineContent = { Text(workout.name) },
                supportingContent = { Text(workout.summary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onNavigate(WorkoutDetailRoute(workoutId = workout.id, title = workout.name))
                    },
            )
            HorizontalDivider()
        }
    }
}
