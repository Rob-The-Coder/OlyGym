package olygym.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import olygym.app.data.AppState
import olygym.app.lib.DEFAULT_ACCENT
import olygym.app.ui.AppNavigator
import olygym.app.ui.theme.OlyGymTheme
import olygym.app.ui.theme.THEME_DARK
import olygym.app.ui.theme.THEME_LIGHT

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge to edge: the insets are the Scaffold's job, not the system bars'.
        enableEdgeToEdge()
        askForNotifications()
        setContent {
            // The profile chooses the scheme; until it has been read, the app's own default —
            // dark, and the M3 baseline purple — so the first frame is already the right colour.
            val state by OlyGymApp.store.state.collectAsState()
            val ui by OlyGymApp.ui.state.collectAsState()
            val settings = (state as? AppState.Ready)?.profile?.settings

            // The timer's completion blinks the app's own theme rather than covering the screen with
            // a rectangle: the alert reads as the app itself flashing, and it settles back on
            // whatever scheme the profile actually has.
            var blink by remember { mutableStateOf(false) }
            LaunchedEffect(ui.timerFlash) {
                if (ui.timerFlash > 0) {
                    blink = true
                    delay(2400)
                    blink = false
                }
            }
            val theme = when {
                !blink -> settings?.theme ?: THEME_DARK
                settings?.theme == THEME_LIGHT -> THEME_DARK
                else -> THEME_LIGHT
            }
            OlyGymTheme(theme = theme, accent = settings?.accent ?: DEFAULT_ACCENT) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    AppNavigator()
                }
            }
        }
    }

    /**
     * The lock-screen rest timer needs this, and it fails silently without it — so it is asked for up
     * front rather than at the first rest. The web build asks when a rest starts; asking on the first
     * launch is one call instead of a callback threaded through the timer, and the answer is the same.
     */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
    }

    /**
     * Adopt whatever the notification did while the app was away: the countdown it has been running
     * is the authority, not the one this process remembers.
     */
    override fun onStart() {
        super.onStart()
        OlyGymApp.ui.reconcileRest()
    }

    /**
     * The web app flushes its file mirror when the page goes away. The same guard here: a set checked
     * off a moment before the app is backgrounded must not wait for the next write.
     */
    override fun onStop() {
        super.onStop()
        OlyGymApp.store.flush()
    }

    private companion object {
        const val NOTIFICATION_REQUEST = 1
    }
}
