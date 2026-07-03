package dev.balaji.moonrose.logtrain.auth

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

actual fun openInSystemBrowser(url: String) {
    val intent = CustomTabsIntent.Builder().build().intent.apply {
        data = Uri.parse(url)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    AndroidContextHolder.appContext.startActivity(intent)
}

actual fun prepareRedirectUri(onToken: (String) -> Unit): String = "logtrain://auth"
