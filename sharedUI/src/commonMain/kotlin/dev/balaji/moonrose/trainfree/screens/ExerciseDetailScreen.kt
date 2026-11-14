package dev.balaji.moonrose.trainfree.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.OutlinedButton
import dev.balaji.moonrose.trainfree.chat.ChatSeedContext
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.trainfree.db.ExerciseFormState
import dev.balaji.moonrose.trainfree.db.ExerciseFormUiState
import dev.balaji.moonrose.trainfree.db.ExerciseHistoryState
import dev.balaji.moonrose.trainfree.insights.HistoryWindow
import dev.balaji.moonrose.trainfree.insights.ExerciseHistoryUiState
import dev.balaji.moonrose.trainfree.insights.SparkPoint
import dev.balaji.moonrose.trainfree.insights.SparkSeries
import dev.balaji.moonrose.trainfree.model.ExerciseType
import dev.balaji.moonrose.trainfree.navigation.ExerciseDetailRoute
import dev.balaji.moonrose.trainfree.navigation.LocalBottomFabPadding
import dev.balaji.moonrose.trainfree.navigation.LocalUseNativeNavigation
import org.jetbrains.compose.resources.stringResource
import dev.balaji.moonrose.trainfree.sharedui.resources.*

private val EXERCISE_TYPE_OPTIONS = ExerciseType.entries.map { it.value }

private val BODY_PART_OPTIONS = listOf(
    "back", "biceps", "calves", "chest", "core", "forearms",
    "full body", "glutes", "hamstrings", "shoulders", "thighs", "triceps",
)

private val EQUIPMENT_OPTIONS = listOf(
    "machine", "body weight", "cable", "barbell", "smith machine",
    "dumbbell", "kettlebell", "other", "band",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    route: ExerciseDetailRoute,
    onGoBack: () -> Unit,
    onOpenChat: ((ChatSeedContext) -> Unit)? = null,
) {
    if (LocalUseNativeNavigation.current) {
        ExerciseDetailContent(
            route = route,
            onOpenChat = onOpenChat,
            modifier = Modifier.padding(WindowInsets.safeDrawing.asPaddingValues()),
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(route.title) },
                    navigationIcon = {
                        IconButton(onClick = onGoBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                        }
                    },
                )
            },
        ) { padding ->
            ExerciseDetailContent(
                route = route,
                onOpenChat = onOpenChat,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseCreateScreen(onGoBack: () -> Unit) {
    if (LocalUseNativeNavigation.current) {
        ExerciseCreateContent(
            onSaved = onGoBack,
            modifier = Modifier.padding(WindowInsets.safeDrawing.asPaddingValues()),
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.new_exercise)) },
                    navigationIcon = {
                        IconButton(onClick = onGoBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                        }
                    },
                )
            },
        ) { padding ->
            ExerciseCreateContent(onSaved = onGoBack, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun ExerciseCreateContent(onSaved: () -> Unit, modifier: Modifier = Modifier) {
    val formState = remember { ExerciseFormState() }
    val uiState by formState.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 24.dp,
                top = 24.dp,
                end = 24.dp,
                bottom = 24.dp + LocalBottomFabPadding.current,
            ),
    ) {
        ExerciseEditForm(uiState = uiState, formState = formState, onDone = onSaved)
    }
}

@Composable
private fun ExerciseDetailContent(
    route: ExerciseDetailRoute,
    onOpenChat: ((ChatSeedContext) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val formState = remember(route.exerciseId) { ExerciseFormState(route.exerciseId) }
    val uiState by formState.uiState.collectAsState()
    val historyState = remember(route.exerciseId) { ExerciseHistoryState(route.exerciseId) }
    val historyUiState by historyState.uiState.collectAsState()
    var editing by remember(route.exerciseId) { mutableStateOf(false) }

    if (uiState.loading) {
        CenteredMessage(PaddingValues(0.dp)) { Text(stringResource(Res.string.loading)) }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 24.dp,
                top = 24.dp,
                end = 24.dp,
                bottom = 24.dp + LocalBottomFabPadding.current,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (editing) {
            ExerciseEditForm(uiState = uiState, formState = formState, onDone = { editing = false })
        } else {
            ExerciseReadOnlyDetails(
                exerciseId = route.exerciseId,
                uiState = uiState,
                onEdit = { editing = true },
                onOpenChat = onOpenChat,
            )
            if (!historyUiState.loading) {
                if (!uiState.builtin) {
                    HorizontalDivider()
                }
                ExerciseHistorySection(
                    historyUiState = historyUiState,
                    exerciseType = ExerciseType.fromValue(uiState.exerciseType),
                )
            }
        }
    }
}

@Composable
private fun ExerciseReadOnlyDetails(
    exerciseId: Long,
    uiState: ExerciseFormUiState,
    onEdit: () -> Unit,
    onOpenChat: ((ChatSeedContext) -> Unit)?,
) {
    val uriHandler = LocalUriHandler.current
    val searchQuery = stringResource(Res.string.describe_exercise_query, uiState.name)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onOpenChat != null && uiState.name.isNotBlank()) {
            OutlinedButton(
                onClick = {
                    onOpenChat(
                        ChatSeedContext(
                            kind = "exercise",
                            exerciseId = exerciseId,
                            exerciseName = uiState.name,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.chat_ask_exercise))
            }
        }
        Text(
            stringResource(Res.string.search_in_google),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable {
                uriHandler.openUri(
                    "https://google.com/search?q=" + searchQuery.replace(" ", "%20"),
                )
            },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        ExerciseField(stringResource(Res.string.exercise_type), uiState.exerciseType?.let { exerciseCatalogLabel(it) })
        HorizontalDivider()
        ExerciseField(stringResource(Res.string.body_part), uiState.bodyPart?.let { exerciseCatalogLabel(it) })
        HorizontalDivider()
        ExerciseField(stringResource(Res.string.equipment), uiState.equipment?.let { exerciseCatalogLabel(it) })
        HorizontalDivider()
        if (!uiState.builtin) {
            TextButton(onClick = onEdit, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(Res.string.edit))
            }
        }
    }
}

@Composable
private fun ExerciseField(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            value ?: "—",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseEditForm(
    uiState: ExerciseFormUiState,
    formState: ExerciseFormState,
    onDone: () -> Unit,
) {
    var typeExpanded by remember { mutableStateOf(false) }
    var bodyPartExpanded by remember { mutableStateOf(false) }
    var equipmentExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = uiState.name,
            onValueChange = formState::updateName,
            label = { Text(stringResource(Res.string.name)) },
            isError = uiState.nameError || uiState.duplicateNameError,
            supportingText = when {
                uiState.nameError -> {
                    { Text(stringResource(Res.string.name_required)) }
                }
                uiState.duplicateNameError -> {
                    { Text(stringResource(Res.string.duplicate_exercise_name)) }
                }
                else -> null
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        ExposedDropdownMenuBox(
            expanded = typeExpanded,
            onExpandedChange = { typeExpanded = it },
        ) {
            OutlinedTextField(
                value = uiState.exerciseType?.let { exerciseCatalogLabel(it) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(Res.string.exercise_type)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = typeExpanded,
                onDismissRequest = { typeExpanded = false },
            ) {
                EXERCISE_TYPE_OPTIONS.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(exerciseCatalogLabel(option)) },
                        onClick = {
                            formState.updateExerciseType(option)
                            typeExpanded = false
                        },
                    )
                }
            }
        }

        ExposedDropdownMenuBox(
            expanded = bodyPartExpanded,
            onExpandedChange = { bodyPartExpanded = it },
        ) {
            OutlinedTextField(
                value = uiState.bodyPart?.let { exerciseCatalogLabel(it) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(Res.string.body_part)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bodyPartExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = bodyPartExpanded,
                onDismissRequest = { bodyPartExpanded = false },
            ) {
                BODY_PART_OPTIONS.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(exerciseCatalogLabel(option)) },
                        onClick = {
                            formState.updateBodyPart(option)
                            bodyPartExpanded = false
                        },
                    )
                }
            }
        }

        ExposedDropdownMenuBox(
            expanded = equipmentExpanded,
            onExpandedChange = { equipmentExpanded = it },
        ) {
            OutlinedTextField(
                value = uiState.equipment?.let { exerciseCatalogLabel(it) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(Res.string.equipment)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = equipmentExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = equipmentExpanded,
                onDismissRequest = { equipmentExpanded = false },
            ) {
                EQUIPMENT_OPTIONS.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(exerciseCatalogLabel(option)) },
                        onClick = {
                            formState.updateEquipment(option)
                            equipmentExpanded = false
                        },
                    )
                }
            }
        }

        Button(
            onClick = { formState.save(onSaved = onDone) },
            enabled = !uiState.saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.save))
        }
    }
}

private data class HistoryMetricEntry(val label: String, val unitLabel: String, val points: List<SparkPoint>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseHistorySection(
    historyUiState: ExerciseHistoryUiState,
    exerciseType: ExerciseType?,
) {
    val showOneRmVolume = exerciseType == null ||
        exerciseType in listOf(ExerciseType.STRENGTH, ExerciseType.REPS_DURATION, ExerciseType.REPS_DISTANCE)
    val showDuration = exerciseType == null ||
        exerciseType in listOf(ExerciseType.DURATION, ExerciseType.REPS_DURATION, ExerciseType.DISTANCE_DURATION)
    val showDistance = exerciseType == null ||
        exerciseType in listOf(ExerciseType.DISTANCE, ExerciseType.REPS_DISTANCE, ExerciseType.DISTANCE_DURATION)

    val weightUnitText = weightUnitLabel(historyUiState.weightUnit)
    val distanceUnitText = distanceUnitLabel(historyUiState.weightUnit)
    val durationUnitText = stringResource(Res.string.unit_s)

    val metrics = buildList {
        if (showOneRmVolume) {
            add(HistoryMetricEntry(stringResource(Res.string.exercise_history_metric_1rm), weightUnitText, historyUiState.weeklyOneRm))
            add(HistoryMetricEntry(stringResource(Res.string.exercise_history_metric_volume), weightUnitText, historyUiState.weeklyVolume))
        }
        if (showDuration) add(HistoryMetricEntry(stringResource(Res.string.exercise_history_metric_duration), durationUnitText, historyUiState.weeklyDuration))
        if (showDistance) add(HistoryMetricEntry(stringResource(Res.string.exercise_history_metric_distance), distanceUnitText, historyUiState.weeklyDistance))
    }
    if (metrics.isEmpty()) return

    var selectedLabel by remember(metrics.first().label) { mutableStateOf(metrics.first().label) }
    // true = most recent weeks (HistoryWindow.RECENT_WEEKS); false = all history.
    var zoomedIn by remember { mutableStateOf(true) }
    val selectedEntry = metrics.firstOrNull { it.label == selectedLabel } ?: metrics.first()
    val selectedPoints = selectedEntry.points

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Res.string.exercise_history_section),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (metrics.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(metrics) { entry ->
                    FilterChip(
                        selected = selectedLabel == entry.label,
                        onClick = { selectedLabel = entry.label },
                        label = { Text(entry.label) },
                    )
                }
            }
        }

        if (!historyUiState.hasAnyData) {
            Text(
                stringResource(Res.string.exercise_history_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val allPoints = selectedPoints.filter { it.value > 0.0 }
            val canZoom = HistoryWindow.canZoom(allPoints)
            val effectiveZoomedIn = zoomedIn || !canZoom
            val nonZeroPoints = if (effectiveZoomedIn) HistoryWindow.recent(allPoints) else allPoints
            if (nonZeroPoints.size >= 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = selectedEntry.unitLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = effectiveZoomedIn,
                            onClick = { zoomedIn = true },
                            enabled = canZoom,
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = {},
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = stringResource(Res.string.exercise_history_zoom_in),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        SegmentedButton(
                            selected = !effectiveZoomedIn,
                            onClick = { zoomedIn = false },
                            enabled = canZoom,
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = {},
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomOut,
                                contentDescription = stringResource(Res.string.exercise_history_zoom_out),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                VicoHistoryChart(
                    points = nonZeroPoints,
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
            } else {
                Text(
                    stringResource(Res.string.exercise_history_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
