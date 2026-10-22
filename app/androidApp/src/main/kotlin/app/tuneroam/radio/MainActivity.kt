package app.tuneroam.radio

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import app.tuneroam.radio.ui.TuneRoamApp
import app.tuneroam.radio.ui.theme.TuneRoamTheme

class MainActivity : ComponentActivity() {
    /** A `tuneroam://station/…` link waiting for the UI to open it. */
    private val pendingLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // A recreated activity still carries the old intent; that link was already opened.
        if (savedInstanceState == null) takeLink(intent)
        setContent {
            TuneRoamTheme {
                TuneRoamApp(
                    pendingLink = pendingLink.value,
                    onLinkHandled = { pendingLink.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeLink(intent)
    }

    private fun takeLink(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) pendingLink.value = intent.dataString
    }
}
