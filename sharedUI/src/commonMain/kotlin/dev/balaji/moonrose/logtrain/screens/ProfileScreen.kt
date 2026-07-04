package dev.balaji.moonrose.logtrain.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.balaji.moonrose.logtrain.auth.AppAuth
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen() {
    if (LocalUseNativeNavigation.current) {
        ProfileContent(Modifier.padding(WindowInsets.safeDrawing.asPaddingValues()))
    } else {
        Scaffold(topBar = { TopAppBar(title = { Text("Profile") }) }) { padding ->
            ProfileContent(Modifier.padding(padding))
        }
    }
}

@Composable
private fun ProfileContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Signed in")
        Button(onClick = { AppAuth.loginController.signOut() }) {
            Text("Sign out")
        }
    }
}
