package dev.balaji.moonrose.trainfree.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberNotificationPermissionRequester(
    onResult: (NotificationPermissionResult) -> Unit,
): () -> Unit {
    val activity = LocalContext.current.findActivity()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onResult(
            when {
                granted -> NotificationPermissionResult.Granted
                // Rationale false after a denial means the OS auto-denied
                // without prompting (or the user picked "don't ask again").
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true ->
                    NotificationPermissionResult.Denied
                else -> NotificationPermissionResult.DeniedPermanently
            },
        )
    }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onResult(NotificationPermissionResult.Granted)
        }
    }
}

@Composable
actual fun rememberNotificationSettingsOpener(): () -> Unit {
    val context = LocalContext.current
    return {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
