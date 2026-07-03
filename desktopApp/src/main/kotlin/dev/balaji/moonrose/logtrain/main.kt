package dev.balaji.moonrose.logtrain

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.balaji.moonrose.logtrain.auth.MoonroseConfig

fun main() {
    MoonroseConfig.baseUrl = "http://localhost:3000"

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "LogTrain",
        ) {
            App()
        }
    }
}