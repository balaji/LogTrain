package dev.balaji.moonrose.logtrain

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.logtrain.db.ActiveWorkoutSession
import dev.balaji.moonrose.logtrain.db.ProfileRepository
import dev.balaji.moonrose.logtrain.db.REST_TIMER_DURATION_SECONDS
import dev.balaji.moonrose.logtrain.db.UserSettingsRepository
import dev.balaji.moonrose.logtrain.db.WorkoutSessionRepository
import dev.balaji.moonrose.logtrain.model.UserSettings
import dev.balaji.moonrose.logtrain.navigation.CalendarRoute
import dev.balaji.moonrose.logtrain.navigation.ExercisesRoute
import dev.balaji.moonrose.logtrain.navigation.NavStack
import dev.balaji.moonrose.logtrain.navigation.PlatformBackHandler
import dev.balaji.moonrose.logtrain.navigation.ProfileRoute
import dev.balaji.moonrose.logtrain.navigation.ProgressRoute
import dev.balaji.moonrose.logtrain.navigation.ScreenContent
import dev.balaji.moonrose.logtrain.navigation.TopLevelRoute
import dev.balaji.moonrose.logtrain.navigation.WorkoutSessionRoute
import dev.balaji.moonrose.logtrain.navigation.WorkoutsRoute
import dev.balaji.moonrose.logtrain.notifications.rememberRestTimerNotifier
import dev.balaji.moonrose.logtrain.screens.ProfileSetupFlow
import dev.balaji.moonrose.logtrain.screens.formatElapsed
import dev.balaji.moonrose.logtrain.screens.remainingSecondsUntil
import dev.balaji.moonrose.logtrain.screens.rememberElapsedSeconds
import dev.balaji.moonrose.logtrain.screens.rememberStartWorkoutHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import dev.balaji.moonrose.logtrain.sharedui.resources.*

private data class TabSpec(val route: TopLevelRoute, val icon: ImageVector)

/**
 * Full app for platforms where Compose owns navigation (Android).
 * iOS does not call this — its SwiftUI shell hosts screens individually.
 */
@Composable
@Preview
fun App() {
    val settings by UserSettingsRepository.settings.collectAsState(initial = UserSettings())
    PlatformTheme(appearance = settings.appearance) {
        val hasProfile by produceState<Boolean?>(initialValue = null) {
            ProfileRepository.profile.collect { value = it != null }
        }

        when (hasProfile) {
            null -> Box(Modifier.fillMaxSize()) // first emission arrives near-instantly
            false -> ProfileSetupFlow()
            true -> MainTabs()
        }
    }
}

@Composable
private fun MainTabs() {
    val tabs = remember {
        listOf(
            TabSpec(ExercisesRoute, Icons.Filled.FitnessCenter),
            TabSpec(CalendarRoute, Icons.Filled.CalendarMonth),
            TabSpec(ProgressRoute, Icons.AutoMirrored.Filled.TrendingUp),
            TabSpec(ProfileRoute, Icons.Filled.Settings),
        )
    }
    var currentTab by remember { mutableStateOf<TopLevelRoute>(ExercisesRoute) }
    val stacks = remember { tabs.associate { it.route to NavStack(it.route) } }
    val stack = stacks.getValue(currentTab)

    PlatformBackHandler(enabled = stack.canGoBack) { stack.pop() }

    val activeSession by WorkoutSessionRepository.activeSession.collectAsState()
    val scope = rememberCoroutineScope()
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showNothingToSave by remember { mutableStateOf(false) }
    var workoutMenuExpanded by remember { mutableStateOf(false) }
    var directWorkoutSession by remember { mutableStateOf<WorkoutSessionRoute?>(null) }
    var directWorkoutLaunchId by remember { mutableIntStateOf(0) }
    val emptyWorkoutTitle = stringResource(Res.string.empty_workout_title)

    PlatformBackHandler(enabled = workoutMenuExpanded) { workoutMenuExpanded = false }

    // Workouts is a single app-level overlay (mirroring the iOS sheet) rather
    // than a route pushed onto a tab's stack, so it can't stack per-tab.
    var showWorkouts by remember { mutableStateOf(false) }
    val workoutsStack = remember { NavStack(WorkoutsRoute) }
    val sessionScreenVisible = stack.current is WorkoutSessionRoute ||
        directWorkoutSession != null ||
        (showWorkouts && workoutsStack.current is WorkoutSessionRoute)

    val activeRestTimer by WorkoutSessionRepository.activeRestTimer.collectAsState()
    val restRemainingSeconds = rememberRestTimerRemainingSeconds()
    val restTotalSeconds = activeRestTimer?.durationSeconds ?: REST_TIMER_DURATION_SECONDS

    // Once a session ends from the overlay, any WorkoutSessionRoute still
    // sitting on a stack is stale — revisiting it would immediately
    // resume-or-start a brand new session.
    fun clearSessionRoutes() {
        stacks.values.forEach { s -> s.removeAll { it is WorkoutSessionRoute } }
        workoutsStack.removeAll { it is WorkoutSessionRoute }
        directWorkoutSession = null
    }

    fun openActiveSession() {
        val session = activeSession ?: return
        workoutMenuExpanded = false
        val target = WorkoutSessionRoute(
            routineId = session.routineId,
            title = session.title,
        )
        if (showWorkouts) {
            if (workoutsStack.current !is WorkoutSessionRoute) {
                workoutsStack.push(target)
            }
        } else {
            directWorkoutLaunchId += 1
            directWorkoutSession = target
        }
    }

    fun completeActiveSession() {
        val sessionId = activeSession?.sessionId ?: return
        scope.launch {
            if (WorkoutSessionRepository.hasCompletedSets(sessionId)) {
                clearSessionRoutes()
                WorkoutSessionRepository.finish(sessionId)
            } else {
                showNothingToSave = true
            }
        }
    }

    val startDirectWorkout = rememberStartWorkoutHandler { route ->
        directWorkoutLaunchId += 1
        directWorkoutSession = route
    }

    Scaffold(
        bottomBar = {
            Column {
                if (activeSession != null && !sessionScreenVisible) {
                    ActiveSessionOverlay(
                        session = activeSession!!,
                        restRemainingSeconds = restRemainingSeconds,
                        restTotalSeconds = restTotalSeconds,
                        onOpen = ::openActiveSession,
                        onComplete = ::completeActiveSession,
                        onDiscard = { showDiscardConfirm = true },
                    )
                }
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab.route,
                            onClick = {
                                workoutMenuExpanded = false
                                if (currentTab == tab.route) {
                                    stacks.getValue(tab.route).popToRoot()
                                } else {
                                    currentTab = tab.route
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = topLevelTabTitle(tab.route)) },
                            label = { Text(topLevelTabTitle(tab.route)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!sessionScreenVisible && !showWorkouts) {
                WorkoutFloatingMenu(
                    expanded = workoutMenuExpanded,
                    onToggle = { workoutMenuExpanded = !workoutMenuExpanded },
                    onStartEmptyWorkout = {
                        workoutMenuExpanded = false
                        startDirectWorkout(
                            WorkoutSessionRoute(
                                routineId = 0L,
                                title = emptyWorkoutTitle,
                            ),
                        )
                    },
                    onChooseProgram = {
                        workoutMenuExpanded = false
                        workoutsStack.popToRoot()
                        showWorkouts = true
                    },
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            ScreenContent(
                route = stack.current,
                onNavigate = stack::push,
                onGoBack = stack::pop,
            )
            AnimatedVisibility(
                visible = workoutMenuExpanded && !sessionScreenVisible && !showWorkouts,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { workoutMenuExpanded = false },
                )
            }
        }
    }

    // Registered after the tab stack's handler so it wins while the overlay
    // is up: back navigates within Workouts or closes it, not the tab underneath.
    PlatformBackHandler(enabled = showWorkouts) {
        if (workoutsStack.canGoBack) {
            workoutsStack.pop()
        } else {
            showWorkouts = false
        }
    }
    AnimatedVisibility(
        visible = showWorkouts,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                val showOverlayBar = activeSession != null && workoutsStack.current !is WorkoutSessionRoute
                Box(
                    Modifier
                        .weight(1f)
                        .let { if (showOverlayBar) it.consumeWindowInsets(WindowInsets.navigationBars) else it },
                ) {
                    ScreenContent(
                        route = workoutsStack.current,
                        onNavigate = workoutsStack::push,
                        onGoBack = {
                            if (workoutsStack.canGoBack) {
                                workoutsStack.pop()
                            } else {
                                showWorkouts = false
                            }
                        },
                    )
                }
                if (showOverlayBar) {
                    Box(
                        Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .navigationBarsPadding(),
                    ) {
                        ActiveSessionOverlay(
                            session = activeSession!!,
                            restRemainingSeconds = restRemainingSeconds,
                            restTotalSeconds = restTotalSeconds,
                            onOpen = ::openActiveSession,
                            onComplete = ::completeActiveSession,
                            onDiscard = { showDiscardConfirm = true },
                        )
                    }
                }
            }
        }
    }

    PlatformBackHandler(enabled = directWorkoutSession != null) {
        directWorkoutSession = null
    }
    AnimatedVisibility(
        visible = directWorkoutSession != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        val sessionRoute = directWorkoutSession
        if (sessionRoute != null) {
            Surface(Modifier.fillMaxSize()) {
                key(directWorkoutLaunchId) {
                    ScreenContent(
                        route = sessionRoute,
                        onNavigate = {},
                        onGoBack = { directWorkoutSession = null },
                    )
                }
            }
        }
    }

    if (showDiscardConfirm) {
        val session = activeSession
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(Res.string.discard_workout_title)) },
            text = { Text(stringResource(Res.string.discard_workout_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    if (session != null) {
                        clearSessionRoutes()
                        scope.launch { WorkoutSessionRepository.discard(session.sessionId) }
                    }
                }) {
                    Text(stringResource(Res.string.discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    if (showNothingToSave) {
        val session = activeSession
        AlertDialog(
            onDismissRequest = { showNothingToSave = false },
            title = { Text(stringResource(Res.string.nothing_to_save_title)) },
            text = { Text(stringResource(Res.string.nothing_to_save_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showNothingToSave = false
                    if (session != null) {
                        clearSessionRoutes()
                        scope.launch { WorkoutSessionRepository.discard(session.sessionId) }
                    }
                }) {
                    Text(stringResource(Res.string.yes), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNothingToSave = false }) {
                    Text(stringResource(Res.string.no))
                }
            },
        )
    }
}

/**
 * App-level observer for the active 2-minute rest timer so navigating away from
 * [dev.balaji.moonrose.logtrain.screens.WorkoutSessionScreen] to view other
 * pages still fires the completion bell, vibration, and background notification,
 * and exposes the remaining seconds for [ActiveSessionOverlay]'s bottom progress bar.
 */
@Composable
private fun rememberRestTimerRemainingSeconds(): Long {
    val activeRestTimer by WorkoutSessionRepository.activeRestTimer.collectAsState()
    val settings by UserSettingsRepository.settings.collectAsState(initial = UserSettings())
    val restNotifier = rememberRestTimerNotifier()
    val restOverTitle by rememberUpdatedState(stringResource(Res.string.rest_over_title))
    val restOverMessage by rememberUpdatedState(stringResource(Res.string.rest_over_message))
    val restChannelName by rememberUpdatedState(stringResource(Res.string.rest_timer_channel_name))
    val notificationsEnabled by rememberUpdatedState(settings.notificationsEnabled)
    val restTimerSound by rememberUpdatedState(settings.restTimerSound)

    var remainingSeconds by remember(activeRestTimer) {
        mutableLongStateOf(activeRestTimer?.let { remainingSecondsUntil(it.endsAtMillis) } ?: 0L)
    }
    var lastStartedAtMillis by remember { mutableLongStateOf(0L) }
    var lastEndsAtMillis by remember { mutableLongStateOf(0L) }
    var lastFinishedAtMillis by remember { mutableLongStateOf(0L) }

    LaunchedEffect(
        activeRestTimer,
        settings.notificationsEnabled,
        settings.restTimerSound,
        restOverTitle,
        restOverMessage,
        restChannelName,
    ) {
        val timer = activeRestTimer ?: return@LaunchedEffect
        if (remainingSecondsUntil(timer.endsAtMillis) > 0L) {
            restNotifier.onRestTimerStarted(
                endsAtMillis = timer.endsAtMillis,
                notificationsEnabled = settings.notificationsEnabled,
                sound = settings.restTimerSound,
                title = restOverTitle,
                message = restOverMessage,
                channelName = restChannelName,
            )
        }
    }

    LaunchedEffect(activeRestTimer) {
        val timer = activeRestTimer
        if (timer == null) {
            remainingSeconds = 0L
            if (
                lastStartedAtMillis != 0L &&
                lastStartedAtMillis != lastFinishedAtMillis &&
                remainingSecondsUntil(lastEndsAtMillis) > 0L
            ) {
                restNotifier.onRestTimerCancelled()
            }
            lastStartedAtMillis = 0L
            lastEndsAtMillis = 0L
            return@LaunchedEffect
        }
        lastStartedAtMillis = timer.startedAtMillis
        lastEndsAtMillis = timer.endsAtMillis
        while (true) {
            val remaining = remainingSecondsUntil(timer.endsAtMillis)
            remainingSeconds = remaining
            if (remaining <= 0L) {
                lastFinishedAtMillis = timer.startedAtMillis
                WorkoutSessionRepository.clearActiveRestTimer()
                restNotifier.onRestTimerFinished(
                    notificationsEnabled = notificationsEnabled,
                    sound = restTimerSound,
                    title = restOverTitle,
                    message = restOverMessage,
                    channelName = restChannelName,
                )
                break
            }
            delay(250L)
        }
    }
    return remainingSeconds
}

/**
 * Persistent mini bar shown above the bottom [NavigationBar] whenever a
 * workout session is running but its own screen isn't the one on top —
 * tapping it re-opens the session; the trailing actions mirror the ones on
 * [dev.balaji.moonrose.logtrain.screens.WorkoutSessionScreen] itself.
 */
@Composable
private fun ActiveSessionOverlay(
    session: ActiveWorkoutSession,
    restRemainingSeconds: Long,
    restTotalSeconds: Long,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    onDiscard: () -> Unit,
) {
    val elapsedSeconds = rememberElapsedSeconds(session.startedAt)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    formatElapsed(elapsedSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            IconButton(onClick = onDiscard) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(Res.string.discard_workout),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            IconButton(onClick = onComplete) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(Res.string.finish_workout),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (restRemainingSeconds > 0L) {
            val progress = (restRemainingSeconds.toFloat() / restTotalSeconds.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

/**
 * Bottom-right floating workout button and its two flowing options ("Empty
 * Workout" and "Choose Program").
 */
@Composable
private fun WorkoutFloatingMenu(
    expanded: Boolean,
    onToggle: () -> Unit,
    onStartEmptyWorkout: () -> Unit,
    onChooseProgram: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }) + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }) + scaleOut(targetScale = 0.85f),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ExtendedFloatingActionButton(
                    onClick = onStartEmptyWorkout,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    text = { Text(stringResource(Res.string.empty_workout)) },
                )
                ExtendedFloatingActionButton(
                    onClick = onChooseProgram,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    icon = { Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null) },
                    text = { Text(stringResource(Res.string.choose_program)) },
                )
            }
        }
        FloatingActionButton(onClick = onToggle) {
            AnimatedContent(targetState = expanded) { isExpanded ->
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.Close else Icons.AutoMirrored.Filled.Assignment,
                    contentDescription = stringResource(if (isExpanded) Res.string.close else Res.string.tab_workouts),
                )
            }
        }
    }
}

/**
 * Tab labels resolve through string resources here rather than [Route.title]
 * so they localize; the route titles remain the language-neutral fallback
 * used by the iOS SwiftUI shell.
 */
@Composable
private fun topLevelTabTitle(route: TopLevelRoute): String = stringResource(
    when (route) {
        ExercisesRoute -> Res.string.tab_exercises
        CalendarRoute -> Res.string.tab_history
        ProgressRoute -> Res.string.tab_progress
        ProfileRoute -> Res.string.profile
    },
)
