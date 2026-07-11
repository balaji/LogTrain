package dev.balaji.moonrose.logtrain.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import dev.balaji.moonrose.logtrain.model.SampleProgram
import dev.balaji.moonrose.logtrain.model.SamplePrograms
import dev.balaji.moonrose.logtrain.model.SampleRoutine
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation
import dev.balaji.moonrose.logtrain.navigation.ProfileRoute
import dev.balaji.moonrose.logtrain.navigation.Route
import dev.balaji.moonrose.logtrain.navigation.RoutineDetailRoute
import dev.balaji.moonrose.logtrain.navigation.WorkoutSessionRoute
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import dev.balaji.moonrose.logtrain.sharedui.resources.*

private sealed interface ProgramDialog {
    data object Create : ProgramDialog
    data class Rename(val program: Program) : ProgramDialog
    data class Delete(val program: Program) : ProgramDialog
    data class CreateRoutine(val program: Program) : ProgramDialog
    data class RenameRoutine(val routine: Routine) : ProgramDialog
    data class DeleteRoutine(val routine: Routine) : ProgramDialog
    data class CopySample(val sample: SampleProgram) : ProgramDialog
    data class ViewSampleRoutine(val routine: SampleRoutine) : ProgramDialog
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
    var expandedSampleId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val couldNotSaveMessage = stringResource(Res.string.could_not_save)
    val emptyWorkoutTitle = stringResource(Res.string.empty_workout_title)

    fun toggleExpanded(program: Program) {
        expandedProgramId = if (expandedProgramId == program.id) null else program.id
    }

    fun toggleSampleExpanded(sample: SampleProgram) {
        expandedSampleId = if (expandedSampleId == sample.id) null else sample.id
    }

    fun runMutation(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                dialog = null
            } catch (e: Exception) {
                dialog = null
                actionError = e.message ?: couldNotSaveMessage
            }
        }
    }

    val body: @Composable (PaddingValues) -> Unit = { contentPadding ->
        ProgramsBody(
            programs = programs,
            expandedProgramId = expandedProgramId,
            routinesByProgram = routinesByProgram,
            contentPadding = contentPadding,
            onStartEmptyWorkout = {
                onNavigate(WorkoutSessionRoute(routineId = 0L, title = emptyWorkoutTitle))
            },
            onToggle = ::toggleExpanded,
            onRename = { dialog = ProgramDialog.Rename(it) },
            onDelete = { dialog = ProgramDialog.Delete(it) },
            onAddRoutine = { dialog = ProgramDialog.CreateRoutine(it) },
            onOpenRoutine = { routine ->
                onNavigate(RoutineDetailRoute(routineId = routine.id, title = routine.name))
            },
            onRenameRoutine = { dialog = ProgramDialog.RenameRoutine(it) },
            onDeleteRoutine = { dialog = ProgramDialog.DeleteRoutine(it) },
            expandedSampleId = expandedSampleId,
            onToggleSample = ::toggleSampleExpanded,
            onCopySample = { dialog = ProgramDialog.CopySample(it) },
            onViewSampleRoutine = { dialog = ProgramDialog.ViewSampleRoutine(it) },
        )
    }

    if (LocalUseNativeNavigation.current) {
        body(WindowInsets.safeDrawing.asPaddingValues())
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.programs_title)) },
                    navigationIcon = { ProfileTopBarButton(onClick = { onNavigate(ProfileRoute) }) },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { dialog = ProgramDialog.Create }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.new_program_cd))
                }
            },
        ) { padding ->
            body(padding)
        }
    }

    when (val current = dialog) {
        ProgramDialog.Create -> NameEditDialog(
            title = stringResource(Res.string.new_program),
            confirmLabel = stringResource(Res.string.create),
            onConfirm = { name -> runMutation { ProgramRepository.createProgram(name) } },
            onDismiss = { dialog = null },
        )
        is ProgramDialog.Rename -> NameEditDialog(
            title = stringResource(Res.string.rename_program),
            initialValue = current.program.name,
            onConfirm = { name -> runMutation { ProgramRepository.renameProgram(current.program.id, name) } },
            onDismiss = { dialog = null },
        )
        is ProgramDialog.Delete -> ConfirmDeleteDialog(
            title = stringResource(Res.string.delete_program_title),
            text = stringResource(Res.string.delete_program_message, current.program.name),
            onConfirm = { runMutation { ProgramRepository.deleteProgram(current.program.id) } },
            onDismiss = { dialog = null },
        )
        is ProgramDialog.CreateRoutine -> NameEditDialog(
            title = stringResource(Res.string.new_routine),
            confirmLabel = stringResource(Res.string.create),
            onConfirm = { name ->
                scope.launch {
                    try {
                        val routineId = ProgramRepository.createRoutine(current.program.id, name)
                        dialog = null
                        onNavigate(RoutineDetailRoute(routineId = routineId, title = name, isNew = true))
                    } catch (e: Exception) {
                        dialog = null
                        actionError = e.message ?: couldNotSaveMessage
                    }
                }
            },
            onDismiss = { dialog = null },
        )
        is ProgramDialog.RenameRoutine -> NameEditDialog(
            title = stringResource(Res.string.rename_routine),
            initialValue = current.routine.name,
            onConfirm = { name -> runMutation { ProgramRepository.renameRoutine(current.routine.id, name) } },
            onDismiss = { dialog = null },
        )
        is ProgramDialog.DeleteRoutine -> ConfirmDeleteDialog(
            title = stringResource(Res.string.delete_routine_title),
            text = stringResource(Res.string.delete_routine_message, current.routine.name),
            onConfirm = { runMutation { ProgramRepository.deleteRoutine(current.routine.id) } },
            onDismiss = { dialog = null },
        )
        is ProgramDialog.CopySample -> {
            // The localized names shown for the sample's keys are what get persisted.
            val sampleName = sampleProgramName(current.sample)
            val routineNames = current.sample.routines.map { sampleRoutineName(it) }
            ConfirmCopyDialog(
                title = stringResource(Res.string.add_sample_title),
                text = stringResource(Res.string.add_sample_message, sampleName),
                onConfirm = {
                    runMutation { ProgramRepository.addSampleProgram(current.sample, sampleName, routineNames) }
                },
                onDismiss = { dialog = null },
            )
        }
        is ProgramDialog.ViewSampleRoutine -> SampleRoutineDialog(
            routine = current.routine,
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
    onStartEmptyWorkout: () -> Unit,
    onToggle: (Program) -> Unit,
    onRename: (Program) -> Unit,
    onDelete: (Program) -> Unit,
    onAddRoutine: (Program) -> Unit,
    onOpenRoutine: (Routine) -> Unit,
    onRenameRoutine: (Routine) -> Unit,
    onDeleteRoutine: (Routine) -> Unit,
    expandedSampleId: String?,
    onToggleSample: (SampleProgram) -> Unit,
    onCopySample: (SampleProgram) -> Unit,
    onViewSampleRoutine: (SampleRoutine) -> Unit,
) {
    if (programs == null) {
        CenteredMessage(contentPadding) { CircularProgressIndicator() }
        return
    }
    LazyColumn(contentPadding = contentPadding) {
        item(key = "start-empty-workout") {
            StartEmptyWorkoutRow(onClick = onStartEmptyWorkout)
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))
        }
        item(key = "my-programs-header") {
            SectionHeader(stringResource(Res.string.my_programs))
        }
        if (programs.isEmpty()) {
            item(key = "empty-programs") {
                Text(
                    stringResource(Res.string.no_programs_yet),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
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
                    val routines = routinesByProgram[program.id].orEmpty()
                    if (routines.isEmpty()) {
                        item(key = "routines-empty-${program.id}") {
                            Column(modifier = Modifier.animateItem()) {
                                Text(
                                    stringResource(Res.string.no_routines_yet),
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
                    item(key = "actions-${program.id}") {
                        Column(modifier = Modifier.animateItem()) {
                            AddRoutineRow(onClick = { onAddRoutine(program) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        item(key = "sample-header") {
            Spacer(Modifier.height(24.dp))
            SectionHeader(stringResource(Res.string.sample_programs))
        }
        SamplePrograms.all.forEach { sample ->
            item(key = "sample-${sample.id}") {
                Column(modifier = Modifier.animateItem()) {
                    SampleProgramRow(
                        sample = sample,
                        expanded = sample.id == expandedSampleId,
                        onToggle = onToggleSample,
                    )
                    HorizontalDivider()
                }
            }
            if (sample.id == expandedSampleId) {
                sample.routines.forEach { routine ->
                    item(key = "sample-routine-${sample.id}-${routine.id}") {
                        Column(modifier = Modifier.animateItem()) {
                            SampleRoutineRow(routine, onClick = { onViewSampleRoutine(routine) })
                            HorizontalDivider()
                        }
                    }
                }
                item(key = "sample-copy-${sample.id}") {
                    Column(modifier = Modifier.animateItem()) {
                        CopySampleRow(onClick = { onCopySample(sample) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun StartEmptyWorkoutRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.start_empty_workout),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
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
                contentDescription = stringResource(Res.string.edit_program),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = { onRename(program) },
            ),
            SwipeRevealAction(
                icon = Icons.Filled.Delete,
                contentDescription = stringResource(Res.string.delete_program),
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                onClick = { onDelete(program) },
            ),
        ),
        onClick = { onToggle(program) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) stringResource(Res.string.collapse) else stringResource(Res.string.expand),
                modifier = Modifier.rotate(chevronRotation),
            )
        },
    ) {
        Text(program.name)
    }
}

@Composable
private fun AddRoutineRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(Res.string.add_routine),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
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
                contentDescription = stringResource(Res.string.edit_routine),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = { onRename(routine) },
            ),
            SwipeRevealAction(
                icon = Icons.Filled.Delete,
                contentDescription = stringResource(Res.string.delete_routine),
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
                contentDescription = stringResource(Res.string.open_routine),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        Text(routine.name, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
internal fun SectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    HorizontalDivider()
}

/**
 * Read-only counterpart to [ProgramRow]: no SwipeRevealRow, so samples cannot be
 * renamed or deleted. The user copies one to get an editable program.
 */
@Composable
private fun SampleProgramRow(
    sample: SampleProgram,
    expanded: Boolean,
    onToggle: (SampleProgram) -> Unit,
) {
    val chevronRotation by animateFloatAsState(if (expanded) 90f else 0f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(sample) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(sampleProgramName(sample), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(Res.string.sample_days_summary, sample.daysPerWeek, sampleProgramSummary(sample)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (expanded) stringResource(Res.string.collapse) else stringResource(Res.string.expand),
            modifier = Modifier.rotate(chevronRotation),
        )
    }
}

@Composable
private fun SampleRoutineRow(routine: SampleRoutine, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(sampleRoutineName(routine), modifier = Modifier.padding(start = 16.dp))
        Text(
            pluralStringResource(Res.plurals.exercise_count, routine.workSets.size, routine.workSets.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CopySampleRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(Res.string.copy_to_my_programs),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
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
