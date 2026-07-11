package dev.balaji.moonrose.logtrain.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.logtrain.calendar.CalendarDay
import dev.balaji.moonrose.logtrain.calendar.CalendarWeek
import dev.balaji.moonrose.logtrain.calendar.WeekStart
import dev.balaji.moonrose.logtrain.db.CalendarRepository
import dev.balaji.moonrose.logtrain.db.CompletedWorkoutDay
import dev.balaji.moonrose.logtrain.db.UserSettingsRepository
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation
import org.jetbrains.compose.resources.stringResource
import dev.balaji.moonrose.logtrain.sharedui.resources.*

private val WeekNumberWidth = 32.dp

@Composable
private fun weekdayLabels(weekStartsMonday: Boolean): List<String> {
    val sundayFirst = listOf(
        Res.string.weekday_sun_short,
        Res.string.weekday_mon_short,
        Res.string.weekday_tue_short,
        Res.string.weekday_wed_short,
        Res.string.weekday_thu_short,
        Res.string.weekday_fri_short,
        Res.string.weekday_sat_short,
    ).map { stringResource(it) }
    return if (weekStartsMonday) sundayFirst.drop(1) + sundayFirst.first() else sundayFirst
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onDayClick: (epochDay: Long) -> Unit = {},
    onOpenProfile: () -> Unit = {},
) {
    // Local state, not a route: toggling between the calendar grid and the
    // session list must not grow the navigation back stack.
    var showSessionList by rememberSaveable { mutableStateOf(false) }
    if (LocalUseNativeNavigation.current) {
        // The native (SwiftUI) shell renders its own calendar screen with the
        // toggle in its navigation bar, so no top-bar action is needed here.
        CalendarContent(Modifier.padding(WindowInsets.safeDrawing.asPaddingValues()), onDayClick)
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.tab_history)) },
                    navigationIcon = { ProfileTopBarButton(onClick = onOpenProfile) },
                    actions = {
                        IconButton(onClick = { showSessionList = !showSessionList }) {
                            Icon(
                                if (showSessionList) Icons.Filled.CalendarMonth else Icons.AutoMirrored.Filled.List,
                                contentDescription = if (showSessionList) stringResource(Res.string.show_calendar) else stringResource(Res.string.show_workout_history),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            if (showSessionList) {
                CompletedSessionList(Modifier.padding(padding), onDayClick)
            } else {
                CalendarContent(Modifier.padding(padding), onDayClick)
            }
        }
    }
}

@Composable
private fun CalendarContent(modifier: Modifier, onDayClick: (Long) -> Unit) {
    val model by CalendarRepository.calendar.collectAsState(initial = null)
    val settings by UserSettingsRepository.settings.collectAsState(initial = null)
    // Early-return until the first emission so the list state below is created
    // with the current week index already known.
    val current = model ?: return
    val weekStartsMonday = settings?.weekStart == WeekStart.MONDAY

    Column(modifier.fillMaxSize()) {
        WeekdayHeader(weekStartsMonday)
        HorizontalDivider()
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = (current.currentWeekIndex - 2).coerceAtLeast(0),
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(current.weeks, key = { it.weekStartEpochDay }) { week ->
                WeekRow(week, onDayClick)
            }
        }
    }
}

@Composable
private fun WeekdayHeader(weekStartsMonday: Boolean) {
    val labels = weekdayLabels(weekStartsMonday)
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        HeaderLabel(stringResource(Res.string.week_number_abbrev), Modifier.width(WeekNumberWidth))
        labels.forEach { label ->
            HeaderLabel(label, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeaderLabel(text: String, modifier: Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun WeekRow(week: CalendarWeek, onDayClick: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            week.weekNumber.toString(),
            modifier = Modifier.width(WeekNumberWidth),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        week.days.forEach { day ->
            DayCell(day, onDayClick, Modifier.weight(1f))
        }
    }
}

/**
 * Flat, most-recent-first list of days with a completed workout. Rows
 * navigate to the existing day detail screen.
 */
@Composable
private fun CompletedSessionList(modifier: Modifier, onDayClick: (Long) -> Unit) {
    val days by CalendarRepository.completedWorkoutDays.collectAsState(initial = null)
    val current = days ?: return

    if (current.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.no_completed_workouts), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(modifier.fillMaxSize()) {
        items(current, key = { it.epochDay }) { day ->
            CompletedSessionRow(day, onDayClick)
            HorizontalDivider()
        }
    }
}

@Composable
private fun CompletedSessionRow(day: CompletedWorkoutDay, onDayClick: (Long) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onDayClick(day.epochDay) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(formatDayTitle(day.epochDay), style = MaterialTheme.typography.bodyLarge)
        if (day.routineNames.isNotEmpty()) {
            Text(
                day.routineNames.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayCell(day: CalendarDay, onDayClick: (Long) -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(
                if (day.isWeekend) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color.Transparent,
            )
            .clickable(enabled = day.hasCompletedWorkout) { onDayClick(day.epochDay) }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Always rendered (blank off the 1st) to keep row heights uniform.
        Text(
            if (day.dayOfMonth == 1) monthAbbreviation(day.monthOfYear) else " ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (day.isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    day.isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                    day.isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(
                    if (day.hasCompletedWorkout) MaterialTheme.colorScheme.primary else Color.Transparent,
                ),
        )
    }
}
