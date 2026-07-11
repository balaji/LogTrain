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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import dev.balaji.moonrose.logtrain.db.ExerciseFormState
import dev.balaji.moonrose.logtrain.db.ExerciseFormUiState
import dev.balaji.moonrose.logtrain.model.ExerciseType
import dev.balaji.moonrose.logtrain.navigation.ExerciseDetailRoute
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation
import org.jetbrains.compose.resources.stringResource
import dev.balaji.moonrose.logtrain.sharedui.resources.*

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
fun ExerciseDetailScreen(route: ExerciseDetailRoute, onGoBack: () -> Unit) {
    if (LocalUseNativeNavigation.current) {
        ExerciseDetailContent(
            route = route,
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
            ExerciseDetailContent(route = route, modifier = Modifier.padding(padding))
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
            .padding(24.dp),
    ) {
        ExerciseEditForm(uiState = uiState, formState = formState, onDone = onSaved)
    }
}

@Composable
private fun ExerciseDetailContent(route: ExerciseDetailRoute, modifier: Modifier = Modifier) {
    val formState = remember(route.exerciseId) { ExerciseFormState(route.exerciseId) }
    val uiState by formState.uiState.collectAsState()
    var editing by remember(route.exerciseId) { mutableStateOf(false) }

    if (uiState.loading) {
        CenteredMessage(PaddingValues(0.dp)) { Text(stringResource(Res.string.loading)) }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (editing) {
            ExerciseEditForm(uiState = uiState, formState = formState, onDone = { editing = false })
        } else {
            ExerciseReadOnlyDetails(uiState = uiState, onEdit = { editing = true })
        }
    }
}

@Composable
private fun ExerciseReadOnlyDetails(uiState: ExerciseFormUiState, onEdit: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val searchQuery = stringResource(Res.string.describe_exercise_query, uiState.name)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
