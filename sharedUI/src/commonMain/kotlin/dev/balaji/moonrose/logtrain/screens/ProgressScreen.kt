package dev.balaji.moonrose.logtrain.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation

/**
 * Placeholder for the Progress tab: charts and analytical views over the
 * workout history will land here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(onOpenProfile: () -> Unit) {
    if (LocalUseNativeNavigation.current) {
        ProgressContent(Modifier.padding(WindowInsets.safeDrawing.asPaddingValues()))
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Progress") },
                    navigationIcon = { ProfileTopBarButton(onClick = onOpenProfile) },
                )
            },
        ) { padding ->
            ProgressContent(Modifier.padding(padding))
        }
    }
}

@Composable
private fun ProgressContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.TrendingUp,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("No progress yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Charts and analytics over your workouts will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
