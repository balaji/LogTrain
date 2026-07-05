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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.logtrain.db.ProgramRepository
import dev.balaji.moonrose.logtrain.model.Program
import dev.balaji.moonrose.logtrain.model.Routine
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation
import dev.balaji.moonrose.logtrain.navigation.Route
import dev.balaji.moonrose.logtrain.navigation.RoutineDetailRoute
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private sealed interface ProgramDialog {
    data object Create : ProgramDialog
    data class Rename(val program: Program) : ProgramDialog
    data class Delete(val program: Program) : ProgramDialog
    data class CreateRoutine(val program: Program) : ProgramDialog
    data class RenameRoutine(val routine: Routine) : ProgramDialog
    data class DeleteRoutine(val routine: Routine) : ProgramDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramsScreen(onNavigate: (Route) -> Unit) {
    val programs by ProgramRepository.programs.collectAsState(initial = null)
    val routinesByProgram by remember {
        ProgramRepository.routines.map { routines -> routines.groupBy(Routine::programId) }
    }.collectAsState(initial = emptyMap())
    var actionError by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<ProgramDialog?>(null) }
    var expandedProgramId by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    fun toggleExpanded(program: Program) {
        expandedProgramId = if (expandedProgramId == program.id) null else program.id
    }

    fun runMutation(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                dialog = null
            } catch (e: Exception) {
                dialog = null
                actionError = e.message ?: "Could not save the change"
            }
        }
    }

    val body: @Composable (PaddingValues) -> Unit = { contentPadding ->
        ProgramsBody(
            programs = programs,
            expandedProgramId = expandedProgramId,
            routinesByProgram = routinesByProgram,
            contentPadding = contentPadding,
            onToggle = ::toggleExpanded,
            onRename = { dialog = ProgramDialog.Rename(it) },
            onDelete = { dialog = ProgramDialog.Delete(it) },
            onAddRoutine = { dialog = ProgramDialog.CreateRoutine(it) },
            onOpenRoutine = { routine ->
                onNavigate(RoutineDetailRoute(routineId = routine.id, title = routine.name))
            },
            onRenameRoutine = { dialog = ProgramDialog.RenameRoutine(it) },
            onDeleteRoutine = { dialog = ProgramDialog.DeleteRoutine(it) },
        )
    }

    if (LocalUseNativeNavigation.current) {
        body(WindowInsets.safeDrawing.asPaddingValues())
    } else {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Programs") }) },
            floatingActionButton = {
                FloatingActionButton(onClick = { dialog = ProgramDialog.Create }) {
                    Icon(Icons.Filled.Add, contentDescription = "New Program")
                }
            },
        ) { padding ->
            body(padding)
        }
    }

    when (val current = dialog) {
        ProgramDialog.Create -> NameEditDialog(
            title = "New program",
            confirmLabel = "Create",
            onConfirm = { name -> runMutation { ProgramRepository.createProgram(name) } },
            onDismiss = { dialog = null },
        )
        is ProgramDialog.Rename -> NameEditDialog(
            title = "Rename program",
            initialValue = current.program.name,
            onConfirm = { name -> runMutation { ProgramRepository.renameProgram(current.program.id, name) } },
            onDismiss = { dialog = null },
        )
        is ProgramDialog.Delete -> ConfirmDeleteDialog(
            title = "Delete program?",
            text = "\"${current.program.name}\" and all of its routines will be deleted.",
            onConfirm = { runMutation { ProgramRepository.deleteProgram(current.program.id) } },
            onDismiss = { dialog = null },
        )
        is ProgramDialog.CreateRoutine -> NameEditDialog(
            title = "New routine",
            confirmLabel = "Create",
            onConfirm = { name ->
                scope.launch {
                    try {
                        val routineId = ProgramRepository.createRoutine(current.program.id, name)
                        dialog = null
                        onNavigate(RoutineDetailRoute(routineId = routineId, title = name, isNew = true))
                    } catch (e: Exception) {
                        dialog = null
                        actionError = e.message ?: "Could not save the change"
                    }
                }
            },
            onDismiss = { dialog = null },
        )
        is ProgramDialog.RenameRoutine -> NameEditDialog(
            title = "Rename routine",
            initialValue = current.routine.name,
            onConfirm = { name -> runMutation { ProgramRepository.renameRoutine(current.routine.id, name) } },
            onDismiss = { dialog = null },
        )
        is ProgramDialog.DeleteRoutine -> ConfirmDeleteDialog(
            title = "Delete routine?",
            text = "\"${current.routine.name}\" will be deleted.",
            onConfirm = { runMutation { ProgramRepository.deleteRoutine(current.routine.id) } },
            onDismiss = { dialog = null },
        )
        null -> {}
    }

    actionError?.let { message ->
        ErrorDialog(message = message, onDismiss = { actionError = null })
    }
}

@Composable
private fun ProgramsBody(
    programs: List<Program>?,
    expandedProgramId: Long?,
    routinesByProgram: Map<Long, List<Routine>>,
    contentPadding: PaddingValues,
    onToggle: (Program) -> Unit,
    onRename: (Program) -> Unit,
    onDelete: (Program) -> Unit,
    onAddRoutine: (Program) -> Unit,
    onOpenRoutine: (Routine) -> Unit,
    onRenameRoutine: (Routine) -> Unit,
    onDeleteRoutine: (Routine) -> Unit,
) {
    when {
        programs == null -> CenteredMessage(contentPadding) {
            CircularProgressIndicator()
        }
        programs.isEmpty() -> CenteredMessage(contentPadding) {
            Text("No programs yet. Tap + to create one.")
        }
        else -> LazyColumn(contentPadding = contentPadding) {
            programs.forEach { program ->
                item(key = "program-${program.id}") {
                    Column(modifier = Modifier.animateItem()) {
                        ProgramRow(
                            program = program,
                            expanded = program.id == expandedProgramId,
                            onToggle = onToggle,
                            onRename = onRename,
                            onDelete = onDelete,
                        )
                        HorizontalDivider()
                    }
                }
                if (program.id == expandedProgramId) {
                    item(key = "actions-${program.id}") {
                        Column(modifier = Modifier.animateItem()) {
                            ProgramExpandedActions(onAddRoutine = { onAddRoutine(program) })
                            HorizontalDivider()
                        }
                    }
                    val routines = routinesByProgram[program.id].orEmpty()
                    if (routines.isEmpty()) {
                        item(key = "routines-empty-${program.id}") {
                            Column(modifier = Modifier.animateItem()) {
                                Text(
                                    "No routines yet. Tap + to add one.",
                                    modifier = Modifier.fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                HorizontalDivider()
                            }
                        }
                    } else {
                        routines.forEach { routine ->
                            item(key = "routine-${routine.id}") {
                                Column(modifier = Modifier.animateItem()) {
                                    RoutineRow(
                                        routine = routine,
                                        onOpen = onOpenRoutine,
                                        onRename = onRenameRoutine,
                                        onDelete = onDeleteRoutine,
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramRow(
    program: Program,
    expanded: Boolean,
    onToggle: (Program) -> Unit,
    onRename: (Program) -> Unit,
    onDelete: (Program) -> Unit,
) {
    val chevronRotation by animateFloatAsState(if (expanded) 90f else 0f)
    SwipeRevealRow(
        actions = listOf(
            SwipeRevealAction(
                icon = Icons.Filled.Edit,
                contentDescription = "Edit program",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = { onRename(program) },
            ),
            SwipeRevealAction(
                icon = Icons.Filled.Delete,
                contentDescription = "Delete program",
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                onClick = { onDelete(program) },
            ),
        ),
        onClick = { onToggle(program) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.rotate(chevronRotation),
            )
        },
    ) {
        Text(program.name)
    }
}

@Composable
private fun ProgramExpandedActions(onAddRoutine: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onAddRoutine) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "New routine",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun RoutineRow(
    routine: Routine,
    onOpen: (Routine) -> Unit,
    onRename: (Routine) -> Unit,
    onDelete: (Routine) -> Unit,
) {
    SwipeRevealRow(
        actions = listOf(
            SwipeRevealAction(
                icon = Icons.Filled.Edit,
                contentDescription = "Edit routine",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = { onRename(routine) },
            ),
            SwipeRevealAction(
                icon = Icons.Filled.Delete,
                contentDescription = "Delete routine",
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                onClick = { onDelete(routine) },
            ),
        ),
        onClick = { onOpen(routine) },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open routine",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        Text(routine.name, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
internal fun CenteredMessage(
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        content()
    }
}
