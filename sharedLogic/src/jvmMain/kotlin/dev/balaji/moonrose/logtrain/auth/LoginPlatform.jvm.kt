package dev.balaji.moonrose.logtrain.auth

import java.awt.Desktop
import java.net.URI

actual fun openInSystemBrowser(url: String) {
    Desktop.getDesktop().browse(URI(url))
}

actual fun prepareRedirectUri(onToken: (String) -> Unit): String = DesktopLoopbackServer.start(onToken)
