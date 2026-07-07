package dev.balaji.moonrose.logtrain.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import dev.balaji.moonrose.logtrain.db.DraftSessionExercise
import dev.balaji.moonrose.logtrain.db.WorkoutSessionFormState
import dev.balaji.moonrose.logtrain.db.WorkoutSessionRepository
import dev.balaji.moonrose.logtrain.db.WorkoutSessionUiState
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation
import dev.balaji.moonrose.logtrain.navigation.WorkoutSessionRoute
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    val elapsedSeconds = rememberElapsedSeconds(uiState.startedAt)

    WorkoutSessionShell(
        title = route.title,
        elapsedSeconds = elapsedSeconds.takeIf { uiState.startedAt > 0 },
        onGoBack = onGoBack,
        onDiscard = { showDiscardConfirm = true },
        onFinish = { formState.finish() },
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
            title = { Text("Discard workout?") },
            text = { Text("This workout and all logged sets will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    formState.discard()
                }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text("Cancel")
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (onDiscard != null) {
                            IconButton(onClick = onDiscard) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Discard workout",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (onFinish != null) {
                            IconButton(onClick = onFinish, enabled = finishEnabled) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Finish workout",
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

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                itemsIndexed(uiState.exercises, key = { _, ex -> ex.id }) { exIndex, exercise ->
                    SessionExerciseCard(
                        exercise = exercise,
                        exIndex = exIndex,
                        onAddSet = { formState.addSet(exIndex) },
                        onRemoveSet = { setIdx -> formState.removeSet(exIndex, setIdx) },
                        onUpdateWeight = { setIdx, v -> formState.updateActualWeight(exIndex, setIdx, v) },
                        onUpdateReps = { setIdx, v -> formState.updateActualReps(exIndex, setIdx, v) },
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
                        Text("ADD EXERCISE")
                    }
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
                "Reorder exercises",
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
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onUpdateWeight: (Int, String) -> Unit,
    onUpdateReps: (Int, String) -> Unit,
    onToggleCompleted: (Int) -> Unit,
    onDelete: () -> Unit,
    onReorderStart: (Float) -> Unit,
    onReorderDrag: (Float) -> Unit,
    onReorderEnd: () -> Unit,
    onReorderCancel: () -> Unit,
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
    val swipeState = remember(exIndex) {
        AnchoredDraggableState(initialValue = SessionSwipeAnchor.Closed, anchors = anchors)
    }
    var headerRootY by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(actionWidth)
                .matchParentSize()
                .background(MaterialTheme.colorScheme.error),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = {
                scope.launch { swipeState.animateTo(SessionSwipeAnchor.Closed) }
                onDelete()
            }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete exercise",
                    tint = MaterialTheme.colorScheme.onError,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(swipeState.requireOffset().roundToInt(), 0) }
                .anchoredDraggable(swipeState, Orientation.Horizontal)
                .background(MaterialTheme.colorScheme.surface),
        ) {
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = exercise.exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "SET",
                    modifier = Modifier.width(32.dp),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "KG",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "REPS",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.width(40.dp))
                Spacer(Modifier.width(40.dp))
            }

            exercise.sets.forEachIndexed { setIdx, set ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${setIdx + 1}",
                        modifier = Modifier.width(32.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    CompactActualInput(
                        value = set.actualWeight,
                        target = set.targetWeight,
                        onValueChange = { onUpdateWeight(setIdx, it) },
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    )
                    CompactActualInput(
                        value = set.actualReps,
                        target = set.targetReps,
                        onValueChange = { onUpdateReps(setIdx, it) },
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    )
                    Checkbox(
                        checked = set.completed,
                        onCheckedChange = { onToggleCompleted(setIdx) },
                        modifier = Modifier.size(40.dp),
                    )
                    IconButton(
                        onClick = { onRemoveSet(setIdx) },
                        modifier = Modifier.size(40.dp),
                        enabled = exercise.sets.size > 1,
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Remove set",
                            modifier = Modifier.size(16.dp),
                            tint = if (exercise.sets.size > 1) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            },
                        )
                    }
                }
            }

            TextButton(
                onClick = onAddSet,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    "ADD SET",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun CompactActualInput(
    value: String,
    target: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
    // Target shows as the placeholder so the user sees what they were aiming
    // for without it being submitted as their actual value.
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
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
