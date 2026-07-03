package dev.balaji.moonrose.logtrain.auth

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun openInSystemBrowser(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl)
}

actual fun prepareRedirectUri(onToken: (String) -> Unit): String = "logtrain://auth"
