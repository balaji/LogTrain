package dev.balaji.moonrose.trainfree.navigation

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A destructive/default/cancel action shown in a native action sheet.
 */
data class NativeSheetAction(
    val label: String,
    val destructive: Boolean = false,
    val onSelect: () -> Unit,
)

/**
 * Presents system-native alerts/action sheets (UIAlertController on iOS).
 * Only provided when [LocalUseNativeNavigation] is true; screens must fall
 * back to Compose Material dialogs/menus otherwise.
 */
interface NativeAlertPresenter {
    fun presentTextInput(
        title: String,
        initialValue: String,
        confirmLabel: String,
        onConfirm: (String) -> Unit,
        onCancel: () -> Unit,
    )

    fun presentConfirm(
        title: String,
        message: String,
        confirmLabel: String,
        destructive: Boolean,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
    )

    fun presentInfo(title: String, message: String, onDismiss: () -> Unit)

    fun presentActionSheet(title: String?, actions: List<NativeSheetAction>, onCancel: () -> Unit)
}

val LocalNativeAlertPresenter = staticCompositionLocalOf<NativeAlertPresenter?> { null }
