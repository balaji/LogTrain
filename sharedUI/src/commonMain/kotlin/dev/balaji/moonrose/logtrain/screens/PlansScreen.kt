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
import dev.balaji.moonrose.logtrain.db.PlanRepository
import dev.balaji.moonrose.logtrain.model.Plan
import dev.balaji.moonrose.logtrain.model.Routine
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation
import dev.balaji.moonrose.logtrain.navigation.Route
import dev.balaji.moonrose.logtrain.navigation.RoutineDetailRoute
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private sealed interface PlanDialog {
    data object Create : PlanDialog
    data class Rename(val plan: Plan) : PlanDialog
    data class Delete(val plan: Plan) : PlanDialog
    data class CreateRoutine(val plan: Plan) : PlanDialog
    data class RenameRoutine(val routine: Routine) : PlanDialog
    data class DeleteRoutine(val routine: Routine) : PlanDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansScreen(onNavigate: (Route) -> Unit) {
    // null until the first database emission, so the loading state is distinct.
    val plans by PlanRepository.plans.collectAsState(initial = null)
    val routinesByPlan by remember {
        PlanRepository.routines.map { routines -> routines.groupBy(Routine::planId) }
    }.collectAsState(initial = emptyMap())
    var actionError by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<PlanDialog?>(null) }
    // Accordion behavior: at most one plan is expanded at a time.
    var expandedPlanId by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    fun toggleExpanded(plan: Plan) {
        expandedPlanId = if (expandedPlanId == plan.id) null else plan.id
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
        PlansBody(
            plans = plans,
            expandedPlanId = expandedPlanId,
            routinesByPlan = routinesByPlan,
            contentPadding = contentPadding,
            onToggle = ::toggleExpanded,
            onRename = { dialog = PlanDialog.Rename(it) },
            onDelete = { dialog = PlanDialog.Delete(it) },
            onAddRoutine = { dialog = PlanDialog.CreateRoutine(it) },
            onOpenRoutine = { routine ->
                onNavigate(RoutineDetailRoute(routineId = routine.id, title = routine.name))
            },
            onRenameRoutine = { dialog = PlanDialog.RenameRoutine(it) },
            onDeleteRoutine = { dialog = PlanDialog.DeleteRoutine(it) },
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
            onConfirm = { name -> runMutation { PlanRepository.createPlan(name) } },
            onDismiss = { dialog = null },
        )
        is PlanDialog.Rename -> NameEditDialog(
            title = "Rename plan",
            initialValue = current.plan.name,
            onConfirm = { name -> runMutation { PlanRepository.renamePlan(current.plan.id, name) } },
            onDismiss = { dialog = null },
        )
        is PlanDialog.Delete -> ConfirmDeleteDialog(
            title = "Delete plan?",
            text = "\"${current.plan.name}\" and all of its routines will be deleted.",
            onConfirm = { runMutation { PlanRepository.deletePlan(current.plan.id) } },
            onDismiss = { dialog = null },
        )
        is PlanDialog.CreateRoutine -> NameEditDialog(
            title = "New routine",
            confirmLabel = "Create",
            onConfirm = { name -> runMutation { PlanRepository.createRoutine(current.plan.id, name) } },
            onDismiss = { dialog = null },
        )
        is PlanDialog.RenameRoutine -> NameEditDialog(
            title = "Rename routine",
            initialValue = current.routine.name,
            onConfirm = { name -> runMutation { PlanRepository.renameRoutine(current.routine.id, name) } },
            onDismiss = { dialog = null },
        )
        is PlanDialog.DeleteRoutine -> ConfirmDeleteDialog(
            title = "Delete routine?",
            text = "\"${current.routine.name}\" will be deleted.",
            onConfirm = { runMutation { PlanRepository.deleteRoutine(current.routine.id) } },
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
    expandedPlanId: Long?,
    routinesByPlan: Map<Long, List<Routine>>,
    contentPadding: PaddingValues,
    onToggle: (Plan) -> Unit,
    onRename: (Plan) -> Unit,
    onDelete: (Plan) -> Unit,
    onAddRoutine: (Plan) -> Unit,
    onOpenRoutine: (Routine) -> Unit,
    onRenameRoutine: (Routine) -> Unit,
    onDeleteRoutine: (Routine) -> Unit,
) {
    when {
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
                            expanded = plan.id == expandedPlanId,
                            onToggle = onToggle,
                            onRename = onRename,
                            onDelete = onDelete,
                        )
                        HorizontalDivider()
                    }
                }
                if (plan.id == expandedPlanId) {
                    item(key = "actions-${plan.id}") {
                        Column(modifier = Modifier.animateItem()) {
                            PlanExpandedActions(onAddRoutine = { onAddRoutine(plan) })
                            HorizontalDivider()
                        }
                    }
                    val routines = routinesByPlan[plan.id].orEmpty()
                    if (routines.isEmpty()) {
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
