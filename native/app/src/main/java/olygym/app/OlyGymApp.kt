package olygym.app

import android.app.Application
import java.io.File
import olygym.app.data.AppState
import olygym.app.data.Assets
import olygym.app.data.Catalogue
import olygym.app.data.StateStore
import olygym.app.data.bool
import olygym.app.data.str
import olygym.app.platform.ToneSound
import olygym.app.rest.SystemRestMirror
import olygym.app.ui.UiState

/**
 * The app's only wiring: read the catalogue and the profile once, build the UI holder, then hand
 * them to the shell. No DI container — there are three objects.
 */
class OlyGymApp : Application() {
    override fun onCreate() {
        super.onCreate()
        store = StateStore(
            file = File(filesDir, StateStore.FILE),
            localePack = { lang -> Assets.locale(this, lang) },
        )
        // ponytail: 300 KB of catalogue JSON parsed on the main thread at startup. It is the work
        // the web app does at module load and it is one frame; move it off the main thread when a
        // trace says so.
        Catalogue.install(Assets.catalogue(this))
        store.load()

        // The two settings the timers read live on the profile, so they are read through it rather
        // than copied: a change takes effect on the next beep.
        fun profile() = (store.state.value as? AppState.Ready)?.profile
        ui = UiState(
            mirror = SystemRestMirror(this),
            sound = ToneSound(this),
            soundEnabled = { profile()?.settings?.sound ?: true },
            flashEnabled = { profile()?.raw?.bool("timerFlash") == true },
            sessionName = { profile()?.active?.str("name").orEmpty() },
        )
    }

    companion object {
        /**
         * The profile, module-level like lib/exercises.js's index: every screen reads it and
         * threading it through the composable tree would be noise. Set in onCreate, before any
         * Activity exists.
         */
        lateinit var store: StateStore
            private set

        /** The ephemeral half — the sheet stack, the toast and the two countdowns. */
        lateinit var ui: UiState
            private set
    }
}
