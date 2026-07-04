package dev.balaji.moonrose.logtrain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.balaji.moonrose.logtrain.auth.AppAuth

@Composable
@Preview
fun App() {
    MaterialTheme {
        var isSignedIn by remember { mutableStateOf(AppAuth.loginController.isSignedIn()) }

        DisposableEffect(Unit) {
            val listener = { isSignedIn = AppAuth.loginController.isSignedIn() }
            AppAuth.loginController.addListener(listener)
            onDispose { AppAuth.loginController.removeListener(listener) }
        }

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isSignedIn) {
                Text("Signed in")
                Button(onClick = { AppAuth.loginController.signOut() }) {
                    Text("Sign out")
                }
            } else {
                Button(onClick = { AppAuth.loginController.startLogin() }) {
                    Text("Sign in with Google")
                }
            }
        }
    }
}
