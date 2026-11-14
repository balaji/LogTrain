package dev.balaji.moonrose.trainfree.navigation

import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertActionStyleDestructive
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleActionSheet
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UITextField
import platform.UIKit.UIViewController

class UIKitNativeAlertPresenter(private val hostViewController: () -> UIViewController) : NativeAlertPresenter {

    override fun presentTextInput(
        title: String,
        initialValue: String,
        confirmLabel: String,
        onConfirm: (String) -> Unit,
        onCancel: () -> Unit,
    ) {
        val alert = UIAlertController.alertControllerWithTitle(
            title = title,
            message = null,
            preferredStyle = UIAlertControllerStyleAlert,
        )
        alert.addTextFieldWithConfigurationHandler { textField -> textField?.text = initialValue }
        alert.addAction(
            UIAlertAction.actionWithTitle("Cancel", style = UIAlertActionStyleCancel) { onCancel() },
        )
        alert.addAction(
            UIAlertAction.actionWithTitle(confirmLabel, style = UIAlertActionStyleDefault) {
                val textField = alert.textFields?.firstOrNull() as? UITextField
                onConfirm(textField?.text.orEmpty())
            },
        )
        hostViewController().presentViewController(alert, animated = true, completion = null)
    }

    override fun presentConfirm(
        title: String,
        message: String,
        confirmLabel: String,
        destructive: Boolean,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
    ) {
        val alert = UIAlertController.alertControllerWithTitle(
            title = title,
            message = message,
            preferredStyle = UIAlertControllerStyleAlert,
        )
        alert.addAction(
            UIAlertAction.actionWithTitle("Cancel", style = UIAlertActionStyleCancel) { onCancel() },
        )
        alert.addAction(
            UIAlertAction.actionWithTitle(
                confirmLabel,
                style = if (destructive) UIAlertActionStyleDestructive else UIAlertActionStyleDefault,
            ) { onConfirm() },
        )
        hostViewController().presentViewController(alert, animated = true, completion = null)
    }

    override fun presentInfo(title: String, message: String, onDismiss: () -> Unit) {
        val alert = UIAlertController.alertControllerWithTitle(
            title = title,
            message = message,
            preferredStyle = UIAlertControllerStyleAlert,
        )
        alert.addAction(
            UIAlertAction.actionWithTitle("OK", style = UIAlertActionStyleDefault) { onDismiss() },
        )
        hostViewController().presentViewController(alert, animated = true, completion = null)
    }

    override fun presentActionSheet(title: String?, actions: List<NativeSheetAction>, onCancel: () -> Unit) {
        val alert = UIAlertController.alertControllerWithTitle(
            title = title,
            message = null,
            preferredStyle = UIAlertControllerStyleActionSheet,
        )
        actions.forEach { action ->
            alert.addAction(
                UIAlertAction.actionWithTitle(
                    action.label,
                    style = if (action.destructive) UIAlertActionStyleDestructive else UIAlertActionStyleDefault,
                ) { action.onSelect() },
            )
        }
        alert.addAction(
            UIAlertAction.actionWithTitle("Cancel", style = UIAlertActionStyleCancel) { onCancel() },
        )
        hostViewController().presentViewController(alert, animated = true, completion = null)
    }
}
