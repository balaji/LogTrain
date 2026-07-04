package dev.balaji.moonrose.logtrain.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.logtrain.api.AppApi
import dev.balaji.moonrose.logtrain.api.Plan
import dev.balaji.moonrose.logtrain.api.Routine
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation
import dev.balaji.moonrose.logtrain.navigation.Route
import dev.balaji.moonrose.logtrain.navigation.RoutineDetailRoute
import dev.balaji.moonrose.logtrain.plans.PlansChangeNotifier
import dev.balaji.moonrose.logtrain.plans.PlansCreateRequestNotifier
import kotlinx.coroutines.launch

private sealed interface PlanDialog {
    data object Create : PlanDialog
    data class Rename(val plan: Plan) : PlanDialog
    data class Delete(val plan: Plan) : PlanDialog
    data class CreateRoutine(val plan: Plan) : PlanDialog
    data class RenameRoutine(val routine: Routine) : PlanDialog
    data class DeleteRoutine(val routine: Routine) : PlanDialog
}

private sealed interface RoutinesState {
    data object Loading : RoutinesState
    data class Loaded(val routines: List<Routine>) : RoutinesState
    data class Error(val message: String) : RoutinesState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansScreen(onNavigate: (Route) -> Unit) {
    var plans by remember { mutableStateOf<List<Plan>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var dialog by remember { mutableStateOf<PlanDialog?>(null) }
    var expandedPlanIds by remember { mutableStateOf(emptySet<Long>()) }
    val routinesByPlan = remember { mutableStateMapOf<Long, RoutinesState>() }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val listener = { refreshKey += 1 }
        PlansChangeNotifier.addListener(listener)
        onDispose { PlansChangeNotifier.removeListener(listener) }
    }

    DisposableEffect(Unit) {
        val listener = { dialog = PlanDialog.Create }
        PlansCreateRequestNotifier.addListener(listener)
        onDispose { PlansCreateRequestNotifier.removeListener(listener) }
    }

    fun loadRoutines(planId: Long) {
        scope.launch {
            // Keep showing stale routines while refreshing to avoid flicker.
            if (routinesByPlan[planId] !is RoutinesState.Loaded) {
                routinesByPlan[planId] = RoutinesState.Loading
            }
            routinesByPlan[planId] = try {
                RoutinesState.Loaded(AppApi.api.listRoutines(planId))
            } catch (e: Exception) {
                RoutinesState.Error(e.message ?: "Could not load routines")
            }
        }
    }

    LaunchedEffect(refreshKey) {
        loadError = null
        try {
            val loaded = AppApi.api.listPlans()
            plans = loaded
            val planIds = loaded.mapTo(mutableSetOf()) { it.id }
            expandedPlanIds = expandedPlanIds intersect planIds
            routinesByPlan.keys.retainAll(planIds)
            expandedPlanIds.forEach { loadRoutines(it) }
        } catch (e: Exception) {
            loadError = e.message ?: "Could not load plans"
        }
    }

    fun toggleExpanded(plan: Plan) {
        if (plan.id in expandedPlanIds) {
            expandedPlanIds = expandedPlanIds - plan.id
        } else {
            expandedPlanIds = expandedPlanIds + plan.id
            if (routinesByPlan[plan.id] == null) loadRoutines(plan.id)
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

    val body: @Composable (PaddingValues) -> Unit = { contentPadding ->
        PlansBody(
            plans = plans,
            loadError = loadError,
            expandedPlanIds = expandedPlanIds,
            routinesByPlan = routinesByPlan,
            contentPadding = contentPadding,
            onRetry = { refreshKey += 1 },
            onToggle = ::toggleExpanded,
            onRename = { dialog = PlanDialog.Rename(it) },
            onDelete = { dialog = PlanDialog.Delete(it) },
            onAddRoutine = { dialog = PlanDialog.CreateRoutine(it) },
            onOpenRoutine = { routine ->
                onNavigate(RoutineDetailRoute(routineId = routine.id, title = routine.name))
            },
            onRenameRoutine = { dialog = PlanDialog.RenameRoutine(it) },
            onDeleteRoutine = { dialog = PlanDialog.DeleteRoutine(it) },
            onRetryRoutines = ::loadRoutines,
        )
    }

    if (LocalUseNativeNavigation.current) {
        body(WindowInsets.safeDrawing.asPaddingValues())
    } else {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Plans") }) },
            floatingActionButton = {
                FloatingActionButton(onClick = { dialog = PlanDialog.Create }) {
                    Icon(Icons.Filled.Add, contentDescription = "New plan")
                }
            },
        ) { padding ->
            body(padding)
        }
    }

    when (val current = dialog) {
        PlanDialog.Create -> NameEditDialog(
            title = "New plan",
            confirmLabel = "Create",
            onConfirm = { name -> runMutation { AppApi.api.createPlan(name) } },
            onDismiss = { dialog = null },
        )
        is PlanDialog.Rename -> NameEditDialog(
            title = "Rename plan",
            initialValue = current.plan.name,
            onConfirm = { name -> runMutation { AppApi.api.updatePlan(current.plan.id, name) } },
            onDismiss = { dialog = null },
        )
        is PlanDialog.Delete -> ConfirmDeleteDialog(
            title = "Delete plan?",
            text = "\"${current.plan.name}\" and all of its routines will be deleted.",
            onConfirm = { runMutation { AppApi.api.deletePlan(current.plan.id) } },
            onDismiss = { dialog = null },
        )
        is PlanDialog.CreateRoutine -> NameEditDialog(
            title = "New routine",
            confirmLabel = "Create",
            onConfirm = { name -> runMutation { AppApi.api.createRoutine(current.plan.id, name) } },
            onDismiss = { dialog = null },
        )
        is PlanDialog.RenameRoutine -> NameEditDialog(
            title = "Rename routine",
            initialValue = current.routine.name,
            onConfirm = { name -> runMutation { AppApi.api.updateRoutine(current.routine.id, name) } },
            onDismiss = { dialog = null },
        )
        is PlanDialog.DeleteRoutine -> ConfirmDeleteDialog(
            title = "Delete routine?",
            text = "\"${current.routine.name}\" will be deleted.",
            onConfirm = { runMutation { AppApi.api.deleteRoutine(current.routine.id) } },
            onDismiss = { dialog = null },
        )
        null -> {}
    }

    actionError?.let { message ->
        ErrorDialog(message = message, onDismiss = { actionError = null })
    }
}

@Composable
private fun PlansBody(
    plans: List<Plan>?,
    loadError: String?,
    expandedPlanIds: Set<Long>,
    routinesByPlan: Map<Long, RoutinesState>,
    contentPadding: PaddingValues,
    onRetry: () -> Unit,
    onToggle: (Plan) -> Unit,
    onRename: (Plan) -> Unit,
    onDelete: (Plan) -> Unit,
    onAddRoutine: (Plan) -> Unit,
    onOpenRoutine: (Routine) -> Unit,
    onRenameRoutine: (Routine) -> Unit,
    onDeleteRoutine: (Routine) -> Unit,
    onRetryRoutines: (Long) -> Unit,
) {
    when {
        plans == null && loadError != null -> CenteredMessage(contentPadding) {
            Text(loadError)
            Button(onClick = onRetry) { Text("Retry") }
        }
        plans == null -> CenteredMessage(contentPadding) {
            CircularProgressIndicator()
        }
        plans.isEmpty() -> CenteredMessage(contentPadding) {
            Text("No plans yet. Tap + to create one.")
        }
        else -> LazyColumn(contentPadding = contentPadding) {
            plans.forEach { plan ->
                item(key = "plan-${plan.id}") {
                    Column(modifier = Modifier.animateItem()) {
                        PlanRow(
                            plan = plan,
                            expanded = plan.id in expandedPlanIds,
                            onToggle = onToggle,
                            onRename = onRename,
                            onDelete = onDelete,
                        )
                        HorizontalDivider()
                    }
                }
                if (plan.id in expandedPlanIds) {
                    item(key = "actions-${plan.id}") {
                        Column(modifier = Modifier.animateItem()) {
                            PlanExpandedActions(onAddRoutine = { onAddRoutine(plan) })
                            HorizontalDivider()
                        }
                    }
                    when (val state = routinesByPlan[plan.id]) {
                        null, RoutinesState.Loading -> item(key = "routines-loading-${plan.id}") {
                            Box(
                                modifier = Modifier.animateItem()
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        is RoutinesState.Error -> item(key = "routines-error-${plan.id}") {
                            Column(
                                modifier = Modifier.animateItem()
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(state.message)
                                TextButton(onClick = { onRetryRoutines(plan.id) }) { Text("Retry") }
                            }
                        }
                        is RoutinesState.Loaded -> if (state.routines.isEmpty()) {
                            item(key = "routines-empty-${plan.id}") {
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
                            state.routines.forEach { routine ->
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
}

@Composable
private fun PlanRow(
    plan: Plan,
    expanded: Boolean,
    onToggle: (Plan) -> Unit,
    onRename: (Plan) -> Unit,
    onDelete: (Plan) -> Unit,
) {
    val chevronRotation by animateFloatAsState(if (expanded) 90f else 0f)
    SwipeRevealRow(
        actions = listOf(
            SwipeRevealAction(
                icon = Icons.Filled.Edit,
                contentDescription = "Edit plan",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = { onRename(plan) },
            ),
            SwipeRevealAction(
                icon = Icons.Filled.Delete,
                contentDescription = "Delete plan",
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                onClick = { onDelete(plan) },
            ),
        ),
        onClick = { onToggle(plan) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.rotate(chevronRotation),
            )
        },
    ) {
        Text(plan.name)
    }
}

/**
 * Actions shown at the top of an expanded plan. A plain icon row so future
 * actions (history, reorder, …) are one more IconButton here.
 */
@Composable
private fun PlanExpandedActions(onAddRoutine: () -> Unit) {
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
