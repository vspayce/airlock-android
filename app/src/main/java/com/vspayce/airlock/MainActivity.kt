package com.vspayce.airlock

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vspayce.airlock.ui.BrowserScreen
import com.vspayce.airlock.ui.theme.AirlockTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val manager = Airlock.tabs(this)
        val incoming = if (savedInstanceState == null) urlFrom(intent) else null
        if (manager.tabs.isEmpty() || incoming != null) manager.newTab(incoming)

        setContent {
            AirlockTheme {
                BrowserScreen(manager)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Hardened: everything is burned whenever you leave Airlock.
        if (!isChangingConfigurations && Airlock.prefs(this).mode == Mode.HARDENED) {
            Airlock.tabs(this).burn { }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        urlFrom(intent)?.let { Airlock.tabs(this).newTab(it) }
    }

    private fun urlFrom(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.dataString ?: return null
        return data.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }
    }
}
