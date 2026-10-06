package olygym.app

import android.app.Application
import java.io.File
import olygym.app.data.Assets
import olygym.app.data.Catalogue
import olygym.app.data.StateStore

/**
 * The app's only wiring: read the catalogue and the profile once, then hand them to the shell. No DI
 * container — there are two objects and, in this phase, one screen.
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
    }

    companion object {
        /**
         * The profile, module-level like lib/exercises.js's index: every screen reads it and
         * threading it through the composable tree would be noise. Set in onCreate, before any
         * Activity exists.
         */
        lateinit var store: StateStore
            private set
    }
}
