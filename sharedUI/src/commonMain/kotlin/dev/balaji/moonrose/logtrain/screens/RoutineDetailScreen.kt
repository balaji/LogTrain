package dev.balaji.moonrose.logtrain.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import dev.balaji.moonrose.logtrain.db.DraftWorkSet
import dev.balaji.moonrose.logtrain.db.RoutineDetailUiState
import dev.balaji.moonrose.logtrain.db.RoutineFormState
import dev.balaji.moonrose.logtrain.db.UserSettingsRepository
import dev.balaji.moonrose.logtrain.model.SetField
import dev.balaji.moonrose.logtrain.model.SetType
import dev.balaji.moonrose.logtrain.model.UserSettings
import dev.balaji.moonrose.logtrain.model.WeightUnit
import dev.balaji.moonrose.logtrain.model.displaySetFields
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation
import dev.balaji.moonrose.logtrain.navigation.Route
import dev.balaji.moonrose.logtrain.navigation.RoutineDetailRoute
import dev.balaji.moonrose.logtrain.navigation.WorkoutSessionRoute
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import dev.balaji.moonrose.logtrain.sharedui.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineDetailScreen(route: RoutineDetailRoute, onNavigate: (Route) -> Unit, onGoBack: () -> Unit) {
    var isEditing by remember { mutableStateOf(route.isNew) }
    var formStateKey by remember { mutableIntStateOf(0) }
    val formState = remember(formStateKey) { RoutineFormState(route.routineId, route.title) }
    val uiState by formState.uiState.collectAsState()

    if (LocalUseNativeNavigation.current) {
        if (isEditing) {
            RoutineDetailBody(
                formState = formState,
                uiState = uiState,
                modifier = Modifier.padding(WindowInsets.safeDrawing.asPaddingValues()),
            )
        } else {
            RoutineReadOnlyBody(
                uiState = uiState,
                modifier = Modifier.padding(WindowInsets.safeDrawing.asPaddingValues()),
            )
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(uiState.name.ifBlank { route.title }) },
                    navigationIcon = {
                        IconButton(onClick = onGoBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                        }
                    },
                    actions = {
                        if (isEditing) {
                            IconButton(
                                onClick = {
                                    formState.save {
                                        formStateKey++
                                        isEditing = false
                                    }
                                },
                                enabled = !uiState.saving && !uiState.loading,
                            ) {
                                if (uiState.saving) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                } else {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = stringResource(Res.string.save),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        } else {
                            IconButton(onClick = { isEditing = true }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.edit))
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                if (!isEditing && uiState.workSets.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = {
                            onNavigate(WorkoutSessionRoute(
                                routineId = route.routineId,
                                title = uiState.name.ifBlank { route.title },
                            ))
                        },
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(Res.string.start_workout))
                    }
                }
            },
        ) { padding ->
            if (isEditing) {
                RoutineDetailBody(
                    formState = formState,
                    uiState = uiState,
                    modifier = Modifier.padding(padding),
                )
            } else {
                RoutineReadOnlyBody(
                    uiState = uiState,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineDetailBody(
    formState: RoutineFormState,
    uiState: RoutineDetailUiState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }
    val pickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val reorder = remember { ReorderState() }
    val rowHeight = 56.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    val haptics = LocalHapticFeedback.current
    val settings by UserSettingsRepository.settings.collectAsState(initial = UserSettings())

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                itemsIndexed(uiState.workSets, key = { _, ws -> ws.id }) { wsIndex, workSet ->
                    ExerciseCard(
                        workSet = workSet,
                        wsIndex = wsIndex,
                        weightUnit = settings.weightUnit,
                        onAddSet = { formState.addSet(wsIndex) },
                        onRemoveSet = { setIdx -> formState.removeSet(wsIndex, setIdx) },
                        onUpdateWeight = { setIdx, v -> formState.updateWeight(wsIndex, setIdx, v) },
                        onUpdateReps = { setIdx, v -> formState.updateReps(wsIndex, setIdx, v) },
                        onUpdateDuration = { setIdx, v -> formState.updateDuration(wsIndex, setIdx, v) },
                        onUpdateDistance = { setIdx, v -> formState.updateDistance(wsIndex, setIdx, v) },
                        onSetTypeChange = { setIdx, setType -> formState.setSetType(wsIndex, setIdx, setType) },
                        onDelete = { formState.removeExercise(wsIndex) },
                        onReorderStart = { startRootY ->
                            reorder.activeIndex = wsIndex
                            reorder.lastActiveIndex = wsIndex
                            reorder.fingerRootY = startRootY
                            reorder.startFingerY = startRootY
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onReorderDrag = { dy -> reorder.fingerRootY += dy },
                        onReorderEnd = {
                            val from = reorder.activeIndex
                            val to = reorder.targetIndex(rowHeightPx, uiState.workSets.size)
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
        }

        // Reorder overlay drawn in the same hierarchy so the ongoing long-press
        // drag on the exercise header keeps streaming events into it.
        AnimatedVisibility(
            visible = reorder.activeIndex != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ReorderOverlay(
                workSets = uiState.workSets,
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

@Composable
private fun RoutineReadOnlyBody(
    uiState: RoutineDetailUiState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            uiState.workSets.isEmpty() -> Text(
                stringResource(Res.string.routine_no_exercises),
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                uiState.workSets.forEach { workSet ->
                    item(key = workSet.id) {
                        Text(
                            text = stringResource(Res.string.sets_by_exercise, workSet.sets.size, workSet.exercise.name),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * State for the continuous long-press reorder gesture. The finger position is
 * tracked in root coordinates so the header gesture (in the LazyColumn) and the
 * overlay rows agree on geometry.
 */
private class ReorderState {
    var activeIndex by mutableStateOf<Int?>(null)
    var fingerRootY by mutableFloatStateOf(0f)
    var startFingerY by mutableFloatStateOf(0f)
    var naturalListTop by mutableFloatStateOf(0f)
    var overlayBottom by mutableFloatStateOf(0f)

    // Keeps the anchor stable while the overlay fades out after activeIndex
    // is cleared.
    var lastActiveIndex = 0

    /**
     * Top of the compact list, positioned so the pressed exercise's slot sits
     * directly under the finger at activation, clamped so the whole list stays
     * between the overlay title and the bottom of the screen.
     */
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

/**
 * Full-screen compact reorder view shown while an exercise header is being
 * long-press-dragged: names only, uniform rows, the dragged one floating
 * under the finger while the rest shift out of the way.
 */
@Composable
private fun ReorderOverlay(
    workSets: List<DraftWorkSet>,
    reorder: ReorderState,
    rowHeight: Dp,
) {
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    // activeIndex goes null the moment the finger lifts, but the overlay is
    // still on screen during the exit fade — render the committed order then.
    val activeIndex = reorder.activeIndex
    val targetIndex = activeIndex?.let { reorder.targetIndex(rowHeightPx, workSets.size) ?: it }
    val anchoredTop = reorder.anchoredListTop(rowHeightPx, workSets.size)
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
                    workSets.forEachIndexed { index, workSet ->
                        val shift by animateFloatAsState(
                            when {
                                activeIndex == null || targetIndex == null || index == activeIndex -> 0f
                                activeIndex < index && index <= targetIndex -> -rowHeightPx
                                targetIndex <= index && index < activeIndex -> rowHeightPx
                                else -> 0f
                            },
                        )
                        ReorderRow(
                            name = workSet.exercise.name,
                            rowHeight = rowHeight,
                            modifier = Modifier.graphicsLayer {
                                translationY = shift
                                alpha = if (index == activeIndex) 0f else 1f
                            },
                        )
                    }
                }

                // Floating copy of the dragged row, riding under the finger but
                // clamped to the list bounds so the rows always read as one group.
                if (activeIndex != null) {
                    ReorderRow(
                        name = workSets[activeIndex].exercise.name,
                        rowHeight = rowHeight,
                        modifier = Modifier
                            .offset {
                                val maxY = (workSets.size - 1) * rowHeightPx
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
private fun ReorderRow(name: String, rowHeight: Dp, modifier: Modifier = Modifier) {
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
private fun ExerciseCard(
    workSet: DraftWorkSet,
    wsIndex: Int,
    weightUnit: WeightUnit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onUpdateWeight: (Int, String) -> Unit,
    onUpdateReps: (Int, String) -> Unit,
    onUpdateDuration: (Int, String) -> Unit,
    onUpdateDistance: (Int, String) -> Unit,
    onSetTypeChange: (Int, SetType) -> Unit,
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
            ExerciseSwipeAnchor.Closed at 0f
            ExerciseSwipeAnchor.Open at -actionWidthPx
        }
    }
    val swipeState = remember(wsIndex) {
        AnchoredDraggableState(initialValue = ExerciseSwipeAnchor.Closed, anchors = anchors)
    }
    var headerRootY by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Delete action revealed on swipe left
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(actionWidth)
                .matchParentSize()
                .background(MaterialTheme.colorScheme.error),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = {
                scope.launch { swipeState.animateTo(ExerciseSwipeAnchor.Closed) }
                onDelete()
            }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(Res.string.delete_exercise),
                    tint = MaterialTheme.colorScheme.onError,
                )
            }
        }

        // Exercise card slides horizontally for swipe-to-delete
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(swipeState.requireOffset().roundToInt(), 0) }
                .anchoredDraggable(swipeState, Orientation.Horizontal)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            // Header: long-press and keep holding to reorder — the same gesture
            // continues into the reorder overlay without lifting the finger.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { headerRootY = it.positionInRoot().y }
                    .pointerInput(wsIndex) {
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
                    text = workSet.exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }

            // Column headers: weight always, then the exercise type's fields.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.column_set),
                    modifier = Modifier.width(40.dp),
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
                workSet.type.displaySetFields().forEach { field ->
                    Text(
                        setFieldColumnLabel(field, weightUnit),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.width(40.dp))
            }

            // Set rows. Only normal sets take a working-set number; warm-up and
            // drop sets show a marker, and the cell opens the set-type menu.
            var workingSetNumber = 0
            workSet.sets.forEachIndexed { setIdx, set ->
                if (set.setType == SetType.NORMAL) workingSetNumber++
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SetTypeCell(
                        setType = set.setType,
                        workingSetNumber = workingSetNumber,
                        onSetTypeChange = { onSetTypeChange(setIdx, it) },
                        cellWidth = 40.dp,
                    )
                    CompactInput(
                        value = set.weight,
                        onValueChange = { onUpdateWeight(setIdx, it) },
                        placeholder = "0",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    )
                    workSet.type.displaySetFields().forEach { field ->
                        when (field) {
                            SetField.REPS -> CompactInput(
                                value = set.reps,
                                onValueChange = { onUpdateReps(setIdx, it) },
                                placeholder = "0",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                            )
                            SetField.DURATION -> CompactInput(
                                value = set.duration,
                                onValueChange = { onUpdateDuration(setIdx, it) },
                                placeholder = stringResource(Res.string.duration_placeholder),
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                            )
                            SetField.DISTANCE -> CompactInput(
                                value = set.distance,
                                onValueChange = { onUpdateDistance(setIdx, it) },
                                placeholder = "0",
                                keyboardType = KeyboardType.Decimal,
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                            )
                        }
                    }
                    IconButton(
                        onClick = { onRemoveSet(setIdx) },
                        modifier = Modifier.size(40.dp),
                        enabled = workSet.sets.size > 1,
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(Res.string.remove_set),
                            modifier = Modifier.size(16.dp),
                            tint = if (workSet.sets.size > 1) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            },
                        )
                    }
                }
            }

            // Add set
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
}

@Composable
private fun CompactInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
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
                        placeholder,
                        // Never wrap: narrow cells (the hh/mm pair) would
                        // otherwise stack a two-digit placeholder vertically.
                        maxLines = 1,
                        softWrap = false,
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

private enum class ExerciseSwipeAnchor { Closed, Open }
