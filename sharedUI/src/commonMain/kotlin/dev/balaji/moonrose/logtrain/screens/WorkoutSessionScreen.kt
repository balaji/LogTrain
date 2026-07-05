package dev.balaji.moonrose.logtrain.screens

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation
import dev.balaji.moonrose.logtrain.navigation.WorkoutSessionRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSessionScreen(route: WorkoutSessionRoute, onGoBack: () -> Unit) {
    if (LocalUseNativeNavigation.current) {
        CenteredMessage(WindowInsets.safeDrawing.asPaddingValues()) {
            Text("Workout logging is coming soon.", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(route.title) },
                    navigationIcon = {
                        IconButton(onClick = onGoBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            CenteredMessage(padding) {
                Text("Workout logging is coming soon.", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
