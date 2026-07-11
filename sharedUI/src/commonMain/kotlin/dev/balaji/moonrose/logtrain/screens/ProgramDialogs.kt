package dev.balaji.moonrose.logtrain.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.logtrain.db.filterProgramName
import dev.balaji.moonrose.logtrain.model.SampleRoutine
import dev.balaji.moonrose.logtrain.navigation.LocalNativeAlertPresenter
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation
import org.jetbrains.compose.resources.stringResource
import dev.balaji.moonrose.logtrain.sharedui.resources.*

@Composable
internal fun NameEditDialog(
    title: String,
    initialValue: String = "",
    confirmLabel: String = stringResource(Res.string.save),
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val nativePresenter = LocalNativeAlertPresenter.current
    if (LocalUseNativeNavigation.current && nativePresenter != null) {
        LaunchedEffect(Unit) {
            nativePresenter.presentTextInput(
                title = title,
                initialValue = initialValue,
                confirmLabel = confirmLabel,
                onConfirm = { name -> onConfirm(name.trim()) },
                onCancel = onDismiss,
            )
        }
        return
    }

    var name by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = filterProgramName(it) },
                label = { Text(stringResource(Res.string.name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
internal fun ConfirmDeleteDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val nativePresenter = LocalNativeAlertPresenter.current
    if (LocalUseNativeNavigation.current && nativePresenter != null) {
        val deleteLabel = stringResource(Res.string.delete)
        LaunchedEffect(Unit) {
            nativePresenter.presentConfirm(
                title = title,
                message = text,
                confirmLabel = deleteLabel,
                destructive = true,
                onConfirm = onConfirm,
                onCancel = onDismiss,
            )
        }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
internal fun ConfirmCopyDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val nativePresenter = LocalNativeAlertPresenter.current
    if (LocalUseNativeNavigation.current && nativePresenter != null) {
        val addLabel = stringResource(Res.string.add)
        LaunchedEffect(Unit) {
            nativePresenter.presentConfirm(
                title = title,
                message = text,
                confirmLabel = addLabel,
                destructive = false,
                onConfirm = onConfirm,
                onCancel = onDismiss,
            )
        }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

/** Read-only listing of a sample routine's exercises and their set schemes. */
@Composable
internal fun SampleRoutineDialog(routine: SampleRoutine, onDismiss: () -> Unit) {
    val routineName = sampleRoutineName(routine)
    val nativePresenter = LocalNativeAlertPresenter.current
    if (LocalUseNativeNavigation.current && nativePresenter != null) {
        LaunchedEffect(Unit) {
            nativePresenter.presentInfo(
                title = routineName,
                message = routine.workSets.joinToString("\n") { "${it.exerciseName} — ${it.repsSummary}" },
                onDismiss = onDismiss,
            )
        }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(routineName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                routine.workSets.forEach { workSet ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            workSet.exerciseName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            workSet.repsSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.ok)) }
        },
    )
}

@Composable
internal fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    val nativePresenter = LocalNativeAlertPresenter.current
    if (LocalUseNativeNavigation.current && nativePresenter != null) {
        val errorTitle = stringResource(Res.string.something_went_wrong)
        LaunchedEffect(Unit) {
            nativePresenter.presentInfo(
                title = errorTitle,
                message = message,
                onDismiss = onDismiss,
            )
        }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.something_went_wrong)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.ok)) }
        },
    )
}
