package dev.balaji.moonrose.logtrain.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.logtrain.db.ExerciseDeleteError
import dev.balaji.moonrose.logtrain.db.ExerciseRepository
import dev.balaji.moonrose.logtrain.db.InsightsRepository
import dev.balaji.moonrose.logtrain.model.Exercise
import dev.balaji.moonrose.logtrain.navigation.ExerciseCreateRoute
import dev.balaji.moonrose.logtrain.navigation.ExerciseDetailRoute
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation
import dev.balaji.moonrose.logtrain.navigation.Route
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import dev.balaji.moonrose.logtrain.sharedui.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(
    onNavigate: (Route) -> Unit,
    onExerciseSelected: ((Exercise) -> Unit)? = null,
    onOpenWorkouts: () -> Unit = {},
) {
    val onOpen: (Exercise) -> Unit = { exercise ->
        if (onExerciseSelected != null) {
            onExerciseSelected(exercise)
        } else {
            onNavigate(ExerciseDetailRoute(exerciseId = exercise.id, title = exercise.name))
        }
    }
    when {
        onExerciseSelected != null -> ExercisesBody(contentPadding = PaddingValues(0.dp), onOpen = onOpen, allowDelete = false)
        LocalUseNativeNavigation.current -> ExercisesBody(contentPadding = WindowInsets.safeDrawing.asPaddingValues(), onOpen = onOpen)
        else -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.tab_exercises)) },
                    actions = { WorkoutsTopBarButton(onClick = onOpenWorkouts) },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { onNavigate(ExerciseCreateRoute) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.add_exercise_cd))
                }
            },
        ) { padding ->
            ExercisesBody(contentPadding = padding, onOpen = onOpen)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisesBody(
    contentPadding: PaddingValues,
    onOpen: (Exercise) -> Unit,
    allowDelete: Boolean = true,
) {
    val exercises by ExerciseRepository.exercises.collectAsState(initial = null)
    val setCountRows by InsightsRepository.observeCompletedSetCountsByExercise()
        .collectAsState(initial = emptyList())
    val setCounts = remember(setCountRows) { setCountRows.associate { it.exerciseId to it.setCount } }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var selectedBodyPart by remember { mutableStateOf<String?>(null) }
    var selectedEquipment by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<Exercise?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    val bodyParts = remember(exercises) {
        exercises.orEmpty().mapNotNull { it.bodyPart }.distinct().sorted()
    }
    val equipmentOptions = remember(exercises) {
        exercises.orEmpty().mapNotNull { it.equipment }.distinct().sorted()
    }
    val filtered = remember(exercises, query, selectedBodyPart, selectedEquipment) {
        exercises.orEmpty().filter { exercise ->
            (selectedBodyPart == null || exercise.bodyPart == selectedBodyPart) &&
                (selectedEquipment == null || exercise.equipment == selectedEquipment) &&
                (query.isBlank() || exercise.name.contains(query, ignoreCase = true))
        }
    }
    val (customExercises, builtinExercises) = remember(filtered) {
        filtered.partition { !it.builtin }
    }

    Column(modifier = Modifier.fillMaxHeight().padding(contentPadding)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(Res.string.search_exercises)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(Res.string.clear_search))
                    }
                }
            },
            singleLine = true,
        )

        if (bodyParts.isNotEmpty()) {
            FilterChipRow(
                label = stringResource(Res.string.body_part),
                options = bodyParts,
                selected = selectedBodyPart,
                onSelect = { selectedBodyPart = if (selectedBodyPart == it) null else it },
            )
        }
        if (equipmentOptions.isNotEmpty()) {
            FilterChipRow(
                label = stringResource(Res.string.equipment),
                options = equipmentOptions,
                selected = selectedEquipment,
                onSelect = { selectedEquipment = if (selectedEquipment == it) null else it },
            )
        }

        when {
            exercises == null -> {}
            filtered.isEmpty() -> Box(modifier = Modifier.weight(1f)) {
                CenteredMessage(PaddingValues(0.dp)) {
                    Text(stringResource(Res.string.no_exercises_match))
                }
            }
            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                if (customExercises.isNotEmpty()) {
                    item(key = "my-exercises-header") { SectionHeader(stringResource(Res.string.my_exercises)) }
                    items(customExercises, key = Exercise::id) { exercise ->
                        if (allowDelete) {
                            SwipeToDeleteRow(key = exercise.id, onDelete = { deleteTarget = exercise }) {
                                ExerciseRow(exercise, setCounts[exercise.id] ?: 0, onOpen)
                            }
                        } else {
                            ExerciseRow(exercise, setCounts[exercise.id] ?: 0, onOpen)
                        }
                    }
                    if (builtinExercises.isNotEmpty()) {
                        item(key = "all-exercises-header") {
                            Spacer(Modifier.height(24.dp))
                            SectionHeader(stringResource(Res.string.all_exercises))
                        }
                    }
                }
                items(builtinExercises, key = Exercise::id) { exercise ->
                    ExerciseRow(exercise, setCounts[exercise.id] ?: 0, onOpen)
                }
            }
        }
    }

    deleteTarget?.let { exercise ->
        val inUseMessage = stringResource(Res.string.exercise_in_use, exercise.name)
        ConfirmDeleteDialog(
            title = stringResource(Res.string.delete_exercise_title),
            text = stringResource(Res.string.delete_exercise_message, exercise.name),
            onConfirm = {
                deleteTarget = null
                scope.launch {
                    if (ExerciseRepository.delete(exercise.id) == ExerciseDeleteError.InUse) {
                        deleteError = inUseMessage
                    }
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }
    deleteError?.let { message ->
        ErrorDialog(message, onDismiss = { deleteError = null })
    }
}

private enum class ExercisesListSwipeAnchor { Closed, Open }

/**
 * Swipe-left-to-reveal delete, following the session screen's anchored-drag
 * pattern (WorkoutSessionScreen's exercise header rows).
 */
@Composable
private fun SwipeToDeleteRow(
    key: Any,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val actionWidth = 72.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
    val anchors = remember(actionWidthPx) {
        DraggableAnchors {
            ExercisesListSwipeAnchor.Closed at 0f
            ExercisesListSwipeAnchor.Open at -actionWidthPx
        }
    }
    val swipeState = remember(key) {
        AnchoredDraggableState(initialValue = ExercisesListSwipeAnchor.Closed, anchors = anchors)
    }

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
                scope.launch { swipeState.animateTo(ExercisesListSwipeAnchor.Closed) }
                onDelete()
            }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(Res.string.delete_exercise),
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
            content()
        }
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise, setCount: Int, onOpen: (Exercise) -> Unit) {
    ListItem(
        headlineContent = { Text(exercise.name) },
        supportingContent = {
            val details = listOfNotNull(exercise.bodyPart, exercise.equipment)
                .map { exerciseCatalogLabel(it) }
            if (details.isNotEmpty()) Text(details.joinToString(" • "))
        },
        trailingContent = if (setCount > 0) {
            {
                Text(
                    pluralStringResource(Res.plurals.exercise_set_count, setCount, setCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else null,
        modifier = Modifier.fillMaxWidth().clickable { onOpen(exercise) },
    )
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipRow(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        items(options) { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(exerciseCatalogLabel(option)) },
            )
        }
    }
}
