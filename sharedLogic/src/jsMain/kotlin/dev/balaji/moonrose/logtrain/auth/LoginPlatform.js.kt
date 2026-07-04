package dev.balaji.moonrose.logtrain.auth

import kotlinx.browser.window

actual fun openInSystemBrowser(url: String) {
    window.location.href = url
}

actual fun prepareRedirectUri(onToken: (String) -> Unit): String =
    "${window.location.origin}/auth/callback"
