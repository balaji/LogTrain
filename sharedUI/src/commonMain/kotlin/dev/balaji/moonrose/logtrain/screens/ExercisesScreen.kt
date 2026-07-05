package dev.balaji.moonrose.logtrain.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.logtrain.db.ExerciseRepository
import dev.balaji.moonrose.logtrain.model.Exercise
import dev.balaji.moonrose.logtrain.navigation.ExerciseDetailRoute
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation
import dev.balaji.moonrose.logtrain.navigation.Route

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(
    onNavigate: (Route) -> Unit,
    onExerciseSelected: ((Exercise) -> Unit)? = null,
) {
    val onOpen: (Exercise) -> Unit = { exercise ->
        if (onExerciseSelected != null) {
            onExerciseSelected(exercise)
        } else {
            onNavigate(ExerciseDetailRoute(exerciseId = exercise.id, title = exercise.name))
        }
    }
    when {
        onExerciseSelected != null -> ExercisesBody(contentPadding = PaddingValues(0.dp), onOpen = onOpen)
        LocalUseNativeNavigation.current -> ExercisesBody(contentPadding = WindowInsets.safeDrawing.asPaddingValues(), onOpen = onOpen)
        else -> Scaffold(topBar = { TopAppBar(title = { Text("Exercises") }) }) { padding ->
            ExercisesBody(contentPadding = padding, onOpen = onOpen)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisesBody(contentPadding: PaddingValues, onOpen: (Exercise) -> Unit) {
    val exercises by ExerciseRepository.exercises.collectAsState(initial = null)
    var query by remember { mutableStateOf("") }
    var selectedBodyPart by remember { mutableStateOf<String?>(null) }
    var selectedEquipment by remember { mutableStateOf<String?>(null) }

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

    Column(modifier = Modifier.padding(contentPadding)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search exercises") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
        )

        if (bodyParts.isNotEmpty()) {
            FilterChipRow(
                label = "Body part",
                options = bodyParts,
                selected = selectedBodyPart,
                onSelect = { selectedBodyPart = if (selectedBodyPart == it) null else it },
            )
        }
        if (equipmentOptions.isNotEmpty()) {
            FilterChipRow(
                label = "Equipment",
                options = equipmentOptions,
                selected = selectedEquipment,
                onSelect = { selectedEquipment = if (selectedEquipment == it) null else it },
            )
        }

        when {
            exercises == null -> {}
            filtered.isEmpty() -> CenteredMessage(PaddingValues(0.dp)) {
                Text("No exercises match your filters.")
            }
            else -> LazyColumn {
                items(filtered, key = Exercise::id) { exercise ->
                    ListItem(
                        headlineContent = { Text(exercise.name) },
                        supportingContent = {
                            val details = listOfNotNull(exercise.bodyPart, exercise.equipment)
                            if (details.isNotEmpty()) Text(details.joinToString(" • "))
                        },
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(exercise) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
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
                label = { Text(option.replaceFirstChar(Char::uppercase)) },
            )
        }
    }
}
