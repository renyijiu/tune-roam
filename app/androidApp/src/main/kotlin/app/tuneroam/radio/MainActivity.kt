package app.tuneroam.radio

import android.app.UiModeManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import app.tuneroam.radio.playback.MediaSessionCommands
import app.tuneroam.radio.playback.RadioLibraryService
import app.tuneroam.radio.ui.TuneRoamApp
import app.tuneroam.radio.ui.theme.TuneRoamTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    /** A `tuneroam://station/…` link waiting for the UI to open it. */
    private val pendingLink = mutableStateOf<String?>(null)
    private val playback: MediaSessionCommands by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // A recreated activity still carries the old intent; that link was already opened.
        if (savedInstanceState == null) takeLink(intent)
        // Registered before the UI's own handlers, so it only runs when nothing in the app
        // wants Back: the user is leaving. A TV has no notification shade or lock screen to
        // stop the radio from afterwards, so leaving it with Back stops it. Home leaves it
        // playing, and phones keep their usual background playback.
        onBackPressedDispatcher.addCallback(this) {
            if (onTelevision()) {
                // Stop the service, then let go of it so it is destroyed, notification and all.
                startService(Intent(this@MainActivity, RadioLibraryService::class.java).setAction(RadioLibraryService.ACTION_STOP))
                playback.disconnect()
                finish()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
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

    /**
     * A TV or projector by what it says it is, or by how it is being used: some projector
     * firmware reports a phone's UI mode and a touchscreen, but arrow keys take the window out
     * of touch mode, and a phone's Back gesture or key does not.
     */
    private fun onTelevision(): Boolean =
        getSystemService(UiModeManager::class.java).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) ||
            !window.decorView.isInTouchMode

    private fun takeLink(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) pendingLink.value = intent.dataString
    }
}
