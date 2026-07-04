package dev.balaji.moonrose.logtrain.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.logtrain.api.AppApi
import dev.balaji.moonrose.logtrain.api.Routine
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation
import dev.balaji.moonrose.logtrain.navigation.PlanDetailRoute
import dev.balaji.moonrose.logtrain.plans.PlansChangeNotifier
import kotlinx.coroutines.launch

private sealed interface PlanDetailDialog {
    data object CreateRoutine : PlanDetailDialog
    data class RenameRoutine(val routine: Routine) : PlanDetailDialog
    data class DeleteRoutine(val routine: Routine) : PlanDetailDialog
    data object RenamePlan : PlanDetailDialog
    data object DeletePlan : PlanDetailDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDetailScreen(route: PlanDetailRoute, onGoBack: () -> Unit) {
    var planName by remember { mutableStateOf(route.title) }
    var routines by remember { mutableStateOf<List<Routine>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var dialog by remember { mutableStateOf<PlanDetailDialog?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val listener = { refreshKey += 1 }
        PlansChangeNotifier.addListener(listener)
        onDispose { PlansChangeNotifier.removeListener(listener) }
    }

    LaunchedEffect(refreshKey) {
        loadError = null
        try {
            routines = AppApi.api.listRoutines(route.planId)
        } catch (e: Exception) {
            loadError = e.message ?: "Could not load routines"
        }
    }

    fun runMutation(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                dialog = null
                PlansChangeNotifier.notifyChanged()
            } catch (e: Exception) {
                dialog = null
                actionError = e.message ?: "Request failed"
            }
        }
    }

    val useNativeNavigation = LocalUseNativeNavigation.current

    val body: @Composable (PaddingValues) -> Unit = { contentPadding ->
        RoutinesBody(
            routines = routines,
            loadError = loadError,
            contentPadding = contentPadding,
            planActionsHeader = if (useNativeNavigation) {
                {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { dialog = PlanDetailDialog.RenamePlan }) { Text("Rename plan") }
                        TextButton(onClick = { dialog = PlanDetailDialog.DeletePlan }) { Text("Delete plan") }
                    }
                }
            } else {
                null
            },
            onRetry = { refreshKey += 1 },
            onRename = { dialog = PlanDetailDialog.RenameRoutine(it) },
            onDelete = { dialog = PlanDetailDialog.DeleteRoutine(it) },
        )
    }

    if (useNativeNavigation) {
        Box(Modifier.fillMaxSize()) {
            body(WindowInsets.safeDrawing.asPaddingValues())
            FloatingActionButton(
                onClick = { dialog = PlanDetailDialog.CreateRoutine },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(WindowInsets.safeDrawing.asPaddingValues())
                    .padding(16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New routine")
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(planName) },
                    navigationIcon = {
                        IconButton(onClick = onGoBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        PlanActionsMenu(
                            onRename = { dialog = PlanDetailDialog.RenamePlan },
                            onDelete = { dialog = PlanDetailDialog.DeletePlan },
                        )
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { dialog = PlanDetailDialog.CreateRoutine }) {
                    Icon(Icons.Filled.Add, contentDescription = "New routine")
                }
            },
        ) { padding ->
            body(padding)
        }
    }

    when (val current = dialog) {
        PlanDetailDialog.CreateRoutine -> NameEditDialog(
            title = "New routine",
            confirmLabel = "Create",
            onConfirm = { name -> runMutation { AppApi.api.createRoutine(route.planId, name) } },
            onDismiss = { dialog = null },
        )
        is PlanDetailDialog.RenameRoutine -> NameEditDialog(
            title = "Rename routine",
            initialValue = current.routine.name,
            onConfirm = { name -> runMutation { AppApi.api.updateRoutine(current.routine.id, name) } },
            onDismiss = { dialog = null },
        )
        is PlanDetailDialog.DeleteRoutine -> ConfirmDeleteDialog(
            title = "Delete routine?",
            text = "\"${current.routine.name}\" will be deleted.",
            onConfirm = { runMutation { AppApi.api.deleteRoutine(current.routine.id) } },
            onDismiss = { dialog = null },
        )
        PlanDetailDialog.RenamePlan -> NameEditDialog(
            title = "Rename plan",
            initialValue = planName,
            onConfirm = { name ->
                runMutation {
                    val updated = AppApi.api.updatePlan(route.planId, name)
                    planName = updated.name
                }
            },
            onDismiss = { dialog = null },
        )
        PlanDetailDialog.DeletePlan -> ConfirmDeleteDialog(
            title = "Delete plan?",
            text = "\"$planName\" and all of its routines will be deleted.",
            onConfirm = {
                runMutation {
                    AppApi.api.deletePlan(route.planId)
                    onGoBack()
                }
            },
            onDismiss = { dialog = null },
        )
        null -> {}
    }

    actionError?.let { message ->
        ErrorDialog(message = message, onDismiss = { actionError = null })
    }
}

@Composable
private fun PlanActionsMenu(onRename: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Plan actions")
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Rename plan") },
                onClick = {
                    menuOpen = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete plan") },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun RoutinesBody(
    routines: List<Routine>?,
    loadError: String?,
    contentPadding: PaddingValues,
    planActionsHeader: (@Composable () -> Unit)?,
    onRetry: () -> Unit,
    onRename: (Routine) -> Unit,
    onDelete: (Routine) -> Unit,
) {
    when {
        routines == null && loadError != null -> CenteredMessage(contentPadding) {
            Text(loadError)
            Button(onClick = onRetry) { Text("Retry") }
        }
        routines == null -> CenteredMessage(contentPadding) {
            CircularProgressIndicator()
        }
        else -> LazyColumn(contentPadding = contentPadding) {
            if (planActionsHeader != null) {
                item(key = "plan-actions") { planActionsHeader() }
            }
            if (routines.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No routines yet. Tap + to create one.")
                    }
                }
            }
            items(routines, key = { it.id }) { routine ->
                RoutineRow(routine = routine, onRename = onRename, onDelete = onDelete)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun RoutineRow(
    routine: Routine,
    onRename: (Routine) -> Unit,
    onDelete: (Routine) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(routine.name) },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Routine actions")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            menuOpen = false
                            onRename(routine)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuOpen = false
                            onDelete(routine)
                        },
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
