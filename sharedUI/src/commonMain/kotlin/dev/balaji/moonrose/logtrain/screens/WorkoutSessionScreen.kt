package dev.balaji.moonrose.logtrain.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.onFocusChanged
import dev.balaji.moonrose.logtrain.db.BarbellKeyboardState
import dev.balaji.moonrose.logtrain.db.DraftSessionExercise
import dev.balaji.moonrose.logtrain.db.DraftSessionSet
import dev.balaji.moonrose.logtrain.db.UserSettingsRepository
import dev.balaji.moonrose.logtrain.db.WorkoutSessionFormState
import dev.balaji.moonrose.logtrain.db.WorkoutSessionRepository
import dev.balaji.moonrose.logtrain.db.WorkoutSessionUiState
import dev.balaji.moonrose.logtrain.model.UserSettings
import dev.balaji.moonrose.logtrain.model.WeightUnit
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation
import dev.balaji.moonrose.logtrain.navigation.WorkoutSessionRoute
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import dev.balaji.moonrose.logtrain.sharedui.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSessionScreen(route: WorkoutSessionRoute, onGoBack: () -> Unit) {
    // "Resume or start" happens once per navigation entry; the resulting id
    // stays stable across recompositions so the FormState is only created once.
    var sessionId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(route.routineId) {
        sessionId = WorkoutSessionRepository.resumeOrStart(routineId = route.routineId)
    }

    val currentId = sessionId
    if (currentId == null) {
        WorkoutSessionShell(title = route.title, onGoBack = onGoBack) { padding ->
            CenteredMessage(padding) { CircularProgressIndicator() }
        }
        return
    }

    val formState = remember(currentId) { WorkoutSessionFormState(currentId) }
    val uiState by formState.uiState.collectAsState()

    if (uiState.finished || uiState.discarded) {
        LaunchedEffect(Unit) { onGoBack() }
        return
    }

    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showNothingToSave by remember { mutableStateOf(false) }
    val elapsedSeconds = rememberElapsedSeconds(uiState.startedAt)

    WorkoutSessionShell(
        title = route.title,
        elapsedSeconds = elapsedSeconds.takeIf { uiState.startedAt > 0 },
        onGoBack = onGoBack,
        onDiscard = { showDiscardConfirm = true },
        onFinish = {
            if (uiState.hasCompletedSets) formState.finish() else showNothingToSave = true
        },
        finishEnabled = !uiState.saving && !uiState.loading,
    ) { padding ->
        WorkoutSessionBody(
            formState = formState,
            uiState = uiState,
            modifier = Modifier.padding(padding),
        )
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(Res.string.discard_workout_title)) },
            text = { Text(stringResource(Res.string.discard_workout_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    formState.discard()
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
        AlertDialog(
            onDismissRequest = { showNothingToSave = false },
            title = { Text(stringResource(Res.string.nothing_to_save_title)) },
            text = { Text(stringResource(Res.string.nothing_to_save_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showNothingToSave = false
                    formState.discard()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutSessionShell(
    title: String,
    elapsedSeconds: Long? = null,
    onGoBack: () -> Unit,
    onDiscard: (() -> Unit)? = null,
    onFinish: (() -> Unit)? = null,
    finishEnabled: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (LocalUseNativeNavigation.current) {
        content(WindowInsets.safeDrawing.asPaddingValues())
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(title)
                            if (elapsedSeconds != null) {
                                Text(
                                    formatElapsed(elapsedSeconds),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onGoBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                        }
                    },
                    actions = {
                        if (onDiscard != null) {
                            IconButton(onClick = onDiscard) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(Res.string.discard_workout),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (onFinish != null) {
                            IconButton(onClick = onFinish, enabled = finishEnabled) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = stringResource(Res.string.finish_workout),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                )
            },
        ) { padding -> content(padding) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutSessionBody(
    formState: WorkoutSessionFormState,
    uiState: WorkoutSessionUiState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }
    val pickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val reorder = remember { SessionReorderState() }
    val rowHeight = 56.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    val haptics = LocalHapticFeedback.current
    val plateKeyboard = remember(formState) { BarbellKeyboardState(formState) }
    val plateState by plateKeyboard.uiState.collectAsState()
    val settings by UserSettingsRepository.settings.collectAsState(initial = UserSettings())

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            else -> Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    itemsIndexed(uiState.exercises, key = { _, ex -> ex.id }) { exIndex, exercise ->
                        SessionExerciseCard(
                            exercise = exercise,
                            exIndex = exIndex,
                            weightUnit = settings.weightUnit,
                            // Which of this card's sets the plate keypad is
                            // editing, or null when it targets another card.
                            plateKeypadSetIndex = plateState.setIndex.takeIf {
                                plateState.visible && plateState.exerciseIndex == exIndex
                            },
                            onOpenPlateKeypad = { setIdx, currentValue ->
                                plateKeyboard.open(exIndex, setIdx, exercise.exercise.id, currentValue)
                            },
                            // A focused text field brings up the system IME;
                            // never stack it under the plate panel.
                            onFieldFocused = { plateKeyboard.close() },
                            onAddSet = { formState.addSet(exIndex) },
                            onRemoveSet = { setIdx -> formState.removeSet(exIndex, setIdx) },
                            onUpdateWeight = { setIdx, v -> formState.updateActualWeight(exIndex, setIdx, v) },
                            onUpdateReps = { setIdx, v -> formState.updateActualReps(exIndex, setIdx, v) },
                            onToggleWarmup = { setIdx -> formState.toggleWarmup(exIndex, setIdx) },
                            onToggleCompleted = { setIdx -> formState.toggleCompleted(exIndex, setIdx) },
                            onDelete = { formState.removeExercise(exIndex) },
                            onReorderStart = { startRootY ->
                                reorder.activeIndex = exIndex
                                reorder.lastActiveIndex = exIndex
                                reorder.fingerRootY = startRootY
                                reorder.startFingerY = startRootY
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onReorderDrag = { dy -> reorder.fingerRootY += dy },
                            onReorderEnd = {
                                val from = reorder.activeIndex
                                val to = reorder.targetIndex(rowHeightPx, uiState.exercises.size)
                                reorder.activeIndex = null
                                if (from != null && to != null && from != to) {
                                    formState.moveExercise(from, to)
                                }
                            },
                            onReorderCancel = { reorder.activeIndex = null },
                        )
                    }

                    item {
                        TextButton(
                            onClick = { showPicker = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(Res.string.add_exercise_caps))
                        }
                    }
                }

                AnimatedVisibility(visible = plateState.visible) {
                    BarbellPlateKeypad(state = plateState, keyboard = plateKeyboard)
                }
            }
        }

        AnimatedVisibility(
            visible = reorder.activeIndex != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            SessionReorderOverlay(
                exercises = uiState.exercises,
                reorder = reorder,
                rowHeight = rowHeight,
            )
        }
    }

    if (showPicker) {
        ModalBottomSheet(
            onDismissRequest = { showPicker = false },
            sheetState = pickerSheetState,
        ) {
            ExercisesScreen(
                onNavigate = {},
                onExerciseSelected = { exercise ->
                    formState.addExercise(exercise)
                    scope.launch { pickerSheetState.hide() }.invokeOnCompletion { showPicker = false }
                },
            )
        }
    }
}

/** Mirrors ReorderState in RoutineDetailScreen — duplicated to keep this change scoped. */
private class SessionReorderState {
    var activeIndex by mutableStateOf<Int?>(null)
    var fingerRootY by mutableFloatStateOf(0f)
    var startFingerY by mutableFloatStateOf(0f)
    var naturalListTop by mutableFloatStateOf(0f)
    var overlayBottom by mutableFloatStateOf(0f)
    var lastActiveIndex = 0

    fun anchoredListTop(rowHeightPx: Float, count: Int): Float {
        val active = activeIndex ?: lastActiveIndex
        val maxTop = (overlayBottom - count * rowHeightPx).coerceAtLeast(naturalListTop)
        return (startFingerY - (active + 0.5f) * rowHeightPx).coerceIn(naturalListTop, maxTop)
    }

    fun targetIndex(rowHeightPx: Float, count: Int): Int? = activeIndex?.let {
        ((fingerRootY - anchoredListTop(rowHeightPx, count)) / rowHeightPx)
            .toInt()
            .coerceIn(0, count - 1)
    }
}

@Composable
private fun SessionReorderOverlay(
    exercises: List<DraftSessionExercise>,
    reorder: SessionReorderState,
    rowHeight: Dp,
) {
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    val activeIndex = reorder.activeIndex
    val targetIndex = activeIndex?.let { reorder.targetIndex(rowHeightPx, exercises.size) ?: it }
    val anchoredTop = reorder.anchoredListTop(rowHeightPx, exercises.size)
    val columnShift = anchoredTop - reorder.naturalListTop

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { reorder.overlayBottom = it.boundsInRoot().bottom },
    ) {
        Column {
            Text(
                stringResource(Res.string.reorder_exercises),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Box(
                modifier = Modifier.onGloballyPositioned {
                    reorder.naturalListTop = it.positionInRoot().y
                },
            ) {
                Column(
                    modifier = Modifier.offset { IntOffset(0, columnShift.roundToInt()) },
                ) {
                    exercises.forEachIndexed { index, exercise ->
                        val shift by animateFloatAsState(
                            when {
                                activeIndex == null || targetIndex == null || index == activeIndex -> 0f
                                activeIndex < index && index <= targetIndex -> -rowHeightPx
                                targetIndex <= index && index < activeIndex -> rowHeightPx
                                else -> 0f
                            },
                        )
                        SessionReorderRow(
                            name = exercise.exercise.name,
                            rowHeight = rowHeight,
                            modifier = Modifier.graphicsLayer {
                                translationY = shift
                                alpha = if (index == activeIndex) 0f else 1f
                            },
                        )
                    }
                }

                if (activeIndex != null) {
                    SessionReorderRow(
                        name = exercises[activeIndex].exercise.name,
                        rowHeight = rowHeight,
                        modifier = Modifier
                            .offset {
                                val maxY = (exercises.size - 1) * rowHeightPx
                                val yInList = (reorder.fingerRootY - anchoredTop - rowHeightPx / 2)
                                    .coerceIn(0f, maxY)
                                IntOffset(0, (yInList + columnShift).roundToInt())
                            }
                            .graphicsLayer { shadowElevation = 12f }
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionReorderRow(name: String, rowHeight: Dp, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.Menu,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionExerciseCard(
    exercise: DraftSessionExercise,
    exIndex: Int,
    weightUnit: WeightUnit,
    plateKeypadSetIndex: Int?,
    onOpenPlateKeypad: (Int, String) -> Unit,
    onFieldFocused: () -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onUpdateWeight: (Int, String) -> Unit,
    onUpdateReps: (Int, String) -> Unit,
    onToggleWarmup: (Int) -> Unit,
    onToggleCompleted: (Int) -> Unit,
    onDelete: () -> Unit,
    onReorderStart: (Float) -> Unit,
    onReorderDrag: (Float) -> Unit,
    onReorderEnd: () -> Unit,
    onReorderCancel: () -> Unit,
) {
    var headerRootY by remember { mutableFloatStateOf(0f) }
    var showMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { headerRootY = it.positionInRoot().y }
                .pointerInput(exIndex) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset -> onReorderStart(headerRootY + offset.y) },
                        onDrag = { change, delta ->
                            change.consume()
                            onReorderDrag(delta.y)
                        },
                        onDragEnd = { onReorderEnd() },
                        onDragCancel = { onReorderCancel() },
                    )
                }
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = exercise.exercise.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(Res.string.exercise_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(Res.string.delete_exercise),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.column_set),
                modifier = Modifier.width(32.dp),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(Res.string.column_previous),
                modifier = Modifier.weight(1.2f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(
                    when (weightUnit) {
                        WeightUnit.METRIC -> Res.string.column_kg
                        WeightUnit.IMPERIAL -> Res.string.column_lb
                    },
                ),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(Res.string.column_reps),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(40.dp))
        }

        // Warm-up sets show "W" and don't consume a working-set number;
        // tapping the number cell toggles warm-up on the fly.
        var workingSetNumber = 0
        exercise.sets.forEachIndexed { setIdx, set ->
            if (!set.warmup) workingSetNumber++
            SessionSetRow(
                set = set,
                setLabel = if (set.warmup) stringResource(Res.string.warmup_marker) else "$workingSetNumber",
                previous = exercise.previousSets.getOrNull(setIdx)?.takeIf { it.isNotBlank() },
                canDelete = exercise.sets.size > 1,
                usesPlateKeypad = exercise.usesPlateKeyboard,
                plateKeypadActive = plateKeypadSetIndex == setIdx,
                onOpenPlateKeypad = { onOpenPlateKeypad(setIdx, set.actualWeight) },
                onFieldFocused = onFieldFocused,
                onDelete = { onRemoveSet(setIdx) },
                onToggleWarmup = { onToggleWarmup(setIdx) },
                onUpdateWeight = { onUpdateWeight(setIdx, it) },
                onUpdateReps = { onUpdateReps(setIdx, it) },
                onToggleCompleted = { onToggleCompleted(setIdx) },
            )
        }

        TextButton(
            onClick = onAddSet,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        ) {
            Text(
                stringResource(Res.string.add_set_caps),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SessionSetRow(
    set: DraftSessionSet,
    setLabel: String,
    previous: String?,
    canDelete: Boolean,
    usesPlateKeypad: Boolean,
    plateKeypadActive: Boolean,
    onOpenPlateKeypad: () -> Unit,
    onFieldFocused: () -> Unit,
    onDelete: () -> Unit,
    onToggleWarmup: () -> Unit,
    onUpdateWeight: (String) -> Unit,
    onUpdateReps: (String) -> Unit,
    onToggleCompleted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val actionWidth = 72.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
    val anchors = remember(actionWidthPx) {
        DraggableAnchors {
            SessionSwipeAnchor.Closed at 0f
            SessionSwipeAnchor.Open at -actionWidthPx
        }
    }
    val swipeState = remember {
        AnchoredDraggableState(initialValue = SessionSwipeAnchor.Closed, anchors = anchors)
    }

    val rowTint by animateColorAsState(
        if (set.completed) CompletedGreen.copy(alpha = 0.20f) else Color.Transparent,
    )
    // Brief scale pop when the row transitions to completed, as a visual cue.
    val popScale = remember { Animatable(1f) }
    var completedSeen by remember { mutableStateOf(set.completed) }
    LaunchedEffect(set.completed) {
        if (set.completed && !completedSeen) {
            popScale.animateTo(1.04f, tween(durationMillis = 110))
            popScale.animateTo(1f, tween(durationMillis = 160))
        }
        completedSeen = set.completed
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.error),
            contentAlignment = Alignment.CenterEnd,
        ) {
            IconButton(
                onClick = {
                    scope.launch { swipeState.animateTo(SessionSwipeAnchor.Closed) }
                    onDelete()
                },
                modifier = Modifier.width(actionWidth).fillMaxHeight(),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(Res.string.remove_set),
                    tint = MaterialTheme.colorScheme.onError,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(swipeState.requireOffset().roundToInt(), 0) }
                .then(if (canDelete) Modifier.anchoredDraggable(swipeState, Orientation.Horizontal) else Modifier)
                .graphicsLayer {
                    scaleX = popScale.value
                    scaleY = popScale.value
                }
                .background(MaterialTheme.colorScheme.surface)
                .background(rowTint)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                setLabel,
                modifier = Modifier
                    .width(32.dp)
                    .clickable { onToggleWarmup() }
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = if (set.warmup) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                previous ?: "—",
                modifier = Modifier.weight(1.2f).padding(horizontal = 4.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (usesPlateKeypad) {
                // Not a text field: the plate keypad drives the value, so
                // there is no system IME to fight with (and desktop behaves
                // identically).
                PlateWeightCell(
                    value = set.actualWeight,
                    target = set.targetWeight,
                    active = plateKeypadActive,
                    onClick = onOpenPlateKeypad,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )
            } else {
                CompactActualInput(
                    value = set.actualWeight,
                    target = set.targetWeight,
                    onValueChange = onUpdateWeight,
                    keyboardType = KeyboardType.Decimal,
                    onFocused = onFieldFocused,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )
            }
            CompactActualInput(
                value = set.actualReps,
                target = set.targetReps,
                onValueChange = onUpdateReps,
                keyboardType = KeyboardType.Number,
                onFocused = onFieldFocused,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            Checkbox(
                checked = set.completed,
                onCheckedChange = { onToggleCompleted() },
                // A set needs at least one rep before it can be
                // completed; completed stays enabled so stale rows can
                // still be unchecked.
                enabled = set.completed || (set.actualReps.toIntOrNull() ?: 0) > 0,
                colors = CheckboxDefaults.colors(checkedColor = CompletedGreen),
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

/** Green shared by the completed-row tint and the checked checkbox. */
private val CompletedGreen = Color(0xFF43A047)

/**
 * Weight cell for barbell exercises. Styled like [CompactActualInput] but a
 * plain clickable — tapping it opens the plate keypad instead of an IME.
 */
@Composable
private fun PlateWeightCell(
    value: String,
    target: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .border(
                width = 1.dp,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            value.ifEmpty { target.ifBlank { "0" } },
            style = TextStyle(
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = if (value.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            ),
        )
    }
}

@Composable
private fun CompactActualInput(
    value: String,
    target: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    onFocused: (() -> Unit)? = null,
) {
    // Target shows as the placeholder so the user sees what they were aiming
    // for without it being submitted as their actual value.
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = if (onFocused == null) {
            modifier
        } else {
            modifier.onFocusChanged { if (it.isFocused) onFocused() }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = TextStyle(
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (value.isEmpty()) {
                    Text(
                        target.ifBlank { "0" },
                        style = TextStyle(
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
                innerTextField()
            }
        },
    )
}

private enum class SessionSwipeAnchor { Closed, Open }
