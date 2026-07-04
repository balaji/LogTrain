package dev.balaji.moonrose.logtrain

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.balaji.moonrose.logtrain.auth.AndroidContextHolder
import dev.balaji.moonrose.logtrain.auth.AppAuth
import dev.balaji.moonrose.logtrain.auth.MoonroseConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidContextHolder.appContext = applicationContext
        MoonroseConfig.baseUrl = BuildConfig.API_BASE_URL

        setContent {
            App()
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.data?.toString()?.let { uri ->
            AppAuth.loginController.completeLogin(uri)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}