package dev.balaji.moonrose.logtrain.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import dev.balaji.moonrose.logtrain.api.Plan
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation
import dev.balaji.moonrose.logtrain.navigation.PlanDetailRoute
import dev.balaji.moonrose.logtrain.navigation.Route
import dev.balaji.moonrose.logtrain.plans.PlansChangeNotifier
import kotlinx.coroutines.launch

private sealed interface PlanDialog {
    data object Create : PlanDialog
    data class Rename(val plan: Plan) : PlanDialog
    data class Delete(val plan: Plan) : PlanDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansScreen(onNavigate: (Route) -> Unit) {
    var plans by remember { mutableStateOf<List<Plan>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var dialog by remember { mutableStateOf<PlanDialog?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val listener = { refreshKey += 1 }
        PlansChangeNotifier.addListener(listener)
        onDispose { PlansChangeNotifier.removeListener(listener) }
    }

    LaunchedEffect(refreshKey) {
        loadError = null
        try {
            plans = AppApi.api.listPlans()
        } catch (e: Exception) {
            loadError = e.message ?: "Could not load plans"
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
            contentPadding = contentPadding,
            onRetry = { refreshKey += 1 },
            onOpen = { plan -> onNavigate(PlanDetailRoute(planId = plan.id, title = plan.name)) },
            onRename = { dialog = PlanDialog.Rename(it) },
            onDelete = { dialog = PlanDialog.Delete(it) },
        )
    }

    if (LocalUseNativeNavigation.current) {
        Box(Modifier.fillMaxSize()) {
            body(WindowInsets.safeDrawing.asPaddingValues())
            FloatingActionButton(
                onClick = { dialog = PlanDialog.Create },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(WindowInsets.safeDrawing.asPaddingValues())
                    .padding(16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New plan")
            }
        }
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
    contentPadding: PaddingValues,
    onRetry: () -> Unit,
    onOpen: (Plan) -> Unit,
    onRename: (Plan) -> Unit,
    onDelete: (Plan) -> Unit,
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
            items(plans, key = { it.id }) { plan ->
                PlanRow(plan = plan, onOpen = onOpen, onRename = onRename, onDelete = onDelete)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PlanRow(
    plan: Plan,
    onOpen: (Plan) -> Unit,
    onRename: (Plan) -> Unit,
    onDelete: (Plan) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(plan.name) },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Plan actions")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            menuOpen = false
                            onRename(plan)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuOpen = false
                            onDelete(plan)
                        },
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(plan) },
    )
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
