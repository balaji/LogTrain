package dev.balaji.moonrose.logtrain.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.logtrain.calendar.WeekMath
import dev.balaji.moonrose.logtrain.db.DayDetailState
import dev.balaji.moonrose.logtrain.db.DayDetailUiState
import dev.balaji.moonrose.logtrain.db.DaySession
import dev.balaji.moonrose.logtrain.db.DaySessionExercise
import dev.balaji.moonrose.logtrain.navigation.DayDetailRoute
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(route: DayDetailRoute, onGoBack: () -> Unit) {
    val title = remember(route.epochDay) { WeekMath.formatDayTitle(route.epochDay) }
    if (LocalUseNativeNavigation.current) {
        DayDetailContent(
            route = route,
            modifier = Modifier.padding(WindowInsets.safeDrawing.asPaddingValues()),
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onGoBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            DayDetailContent(route = route, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun DayDetailContent(route: DayDetailRoute, modifier: Modifier = Modifier) {
    val state = remember(route.epochDay) { DayDetailState(route.epochDay) }
    val uiState by state.uiState.collectAsState(initial = DayDetailUiState())

    if (uiState.loading) {
        CenteredMessage(PaddingValues(0.dp)) { CircularProgressIndicator() }
        return
    }

    if (uiState.sessions.isEmpty()) {
        CenteredMessage(PaddingValues(0.dp)) { Text("No completed workouts on this day") }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(uiState.sessions, key = { it.id }) { session ->
            SessionSummaryCard(session)
        }
    }
}

@Composable
private fun SessionSummaryCard(session: DaySession) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            session.routineName ?: "Workout",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        session.exercises.forEach { exercise ->
            ExerciseSummaryRow(exercise)
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ExerciseSummaryRow(exercise: DaySessionExercise) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(exercise.name, style = MaterialTheme.typography.bodyLarge)
        var workingSetNumber = 0
        exercise.sets.forEach { set ->
            if (!set.warmup) workingSetNumber++
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (set.warmup) "Warm-up" else "Set $workingSetNumber",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                val weight = set.actualWeight.ifBlank { set.targetWeight }
                val reps = set.actualReps.ifBlank { set.targetReps }
                Text(
                    listOfNotNull(
                        weight.takeIf { it.isNotBlank() }?.let { "$it kg" },
                        reps.takeIf { it.isNotBlank() }?.let { "$it reps" },
                    ).joinToString(" · ").ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
