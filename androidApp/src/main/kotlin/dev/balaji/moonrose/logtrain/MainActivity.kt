package dev.balaji.moonrose.logtrain

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.balaji.moonrose.logtrain.db.DbContextHolder
import dev.balaji.moonrose.logtrain.db.DevSeed

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        DbContextHolder.appContext = applicationContext
        maybeSeedTestData()

        setContent {
            App()
        }
    }

    // Test harness hook: `am start ... --ei seedTestData <days>` on a fresh
    // debuggable install lands the app on a populated profile (see
    // scripts/run-android.py --seed).
    private fun maybeSeedTestData() {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!debuggable) return
        val days = intent?.getIntExtra("seedTestData", 0) ?: 0
        if (days > 0) DevSeed.seedIfNoProfile(days)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
