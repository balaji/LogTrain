package dev.balaji.moonrose.logtrain.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
fun rememberIsSignedIn(): State<Boolean> {
    val isSignedIn = remember { mutableStateOf(AppAuth.loginController.isSignedIn()) }

    DisposableEffect(Unit) {
        val listener = { isSignedIn.value = AppAuth.loginController.isSignedIn() }
        AppAuth.loginController.addListener(listener)
        onDispose { AppAuth.loginController.removeListener(listener) }
    }

    return isSignedIn
}
