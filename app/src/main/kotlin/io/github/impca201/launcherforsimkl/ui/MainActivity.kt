package io.github.impca201.launcherforsimkl.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import io.github.impca201.launcherforsimkl.CrashReporter
import io.github.impca201.launcherforsimkl.LauncherApp
import io.github.impca201.launcherforsimkl.ui.theme.LauncherTheme

class MainActivity : ComponentActivity() {
    private val resumes = mutableIntStateOf(0)
    private val homePresses = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (CrashReporter.showIfCrashed(this) { recreate() }) return
        val app = application as LauncherApp
        setContent {
            LauncherTheme {
                AppRoot(app, resumes = resumes.intValue, homePresses = homePresses.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while the launcher is already in front (or brought back by the override).
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses.intValue++
    }

    override fun onResume() {
        super.onResume()
        resumes.intValue++
        (application as LauncherApp).content.refreshIfStale()
    }
}
