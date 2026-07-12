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
import dev.balaji.moonrose.logtrain.db.DayDetailState
import dev.balaji.moonrose.logtrain.db.DayDetailUiState
import dev.balaji.moonrose.logtrain.db.DaySession
import dev.balaji.moonrose.logtrain.db.DaySessionExercise
import dev.balaji.moonrose.logtrain.model.SetType
import dev.balaji.moonrose.logtrain.navigation.DayDetailRoute
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation
import org.jetbrains.compose.resources.stringResource
import dev.balaji.moonrose.logtrain.sharedui.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(route: DayDetailRoute, onGoBack: () -> Unit) {
    val title = formatDayTitle(route.epochDay)
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
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
        CenteredMessage(PaddingValues(0.dp)) { Text(stringResource(Res.string.no_workouts_this_day)) }
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
            session.routineName ?: stringResource(Res.string.workout),
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
            if (set.setType == SetType.NORMAL) workingSetNumber++
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (set.setType == SetType.NORMAL) {
                        stringResource(Res.string.set_number, workingSetNumber)
                    } else {
                        setTypeLabel(set.setType)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                val weight = set.actualWeight.ifBlank { set.targetWeight }
                val reps = set.actualReps.ifBlank { set.targetReps }
                val duration = set.actualDuration.ifBlank { set.targetDuration }
                val distance = set.actualDistance.ifBlank { set.targetDistance }
                Text(
                    listOfNotNull(
                        weight.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.weight_kg_value, it) },
                        reps.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.reps_value, it) },
                        duration.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.duration_value, it) },
                        distance.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.distance_km_value, it) },
                    ).joinToString(" · ").ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
