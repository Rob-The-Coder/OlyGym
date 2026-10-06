package olygym.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import olygym.app.data.AppState
import olygym.app.lib.DEFAULT_ACCENT
import olygym.app.ui.AppNavigator
import olygym.app.ui.theme.OlyGymTheme
import olygym.app.ui.theme.THEME_DARK

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge to edge: the insets are the Scaffold's job, not the system bars'.
        enableEdgeToEdge()
        setContent {
            // The profile chooses the scheme; until it has been read, the app's own default —
            // dark, and the M3 baseline purple — so the first frame is already the right colour.
            val state by OlyGymApp.store.state.collectAsState()
            val settings = (state as? AppState.Ready)?.profile?.settings
            OlyGymTheme(
                theme = settings?.theme ?: THEME_DARK,
                accent = settings?.accent ?: DEFAULT_ACCENT,
            ) {
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
     * The web app flushes its file mirror when the page goes away. The same guard here: a set
     * checked off a moment before the app is backgrounded must not wait for the next write.
     */
    override fun onStop() {
        super.onStop()
        OlyGymApp.store.flush()
    }
}
