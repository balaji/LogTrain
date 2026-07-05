package dev.balaji.moonrose.logtrain.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.balaji.moonrose.logtrain.navigation.LocalNativeAlertPresenter
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation

@Composable
internal fun NameEditDialog(
    title: String,
    initialValue: String = "",
    confirmLabel: String = "Save",
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
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
        LaunchedEffect(Unit) {
            nativePresenter.presentConfirm(
                title = title,
                message = text,
                confirmLabel = "Delete",
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
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    val nativePresenter = LocalNativeAlertPresenter.current
    if (LocalUseNativeNavigation.current && nativePresenter != null) {
        LaunchedEffect(Unit) {
            nativePresenter.presentInfo(
                title = "Something went wrong",
                message = message,
                onDismiss = onDismiss,
            )
        }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Something went wrong") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}
