package olygym.app.ui

import cafe.adriel.voyager.core.screen.Screen

/**
 * Navigation from outside a composition — the seam the web app has in lib/nav.js, where the shell
 * hands React's navigate() over once and everything else calls into it.
 *
 * It is needed because the sheet functions and the session lifecycle are plain functions, not
 * composables: starting a workout has to push a screen from inside a sheet's callback.
 */
object Nav {
    var push: ((Screen) -> Unit)? = null
    var pop: (() -> Unit)? = null
    var home: (() -> Unit)? = null

    /** Selects a tab on the root screen — what the week rail and the "open the plan" action need. */
    var tabHost: ((Int) -> Unit)? = null

    fun to(screen: Screen) {
        push?.invoke(screen)
    }

    fun back() {
        pop?.invoke()
    }

    /** Back to the tab host, on Home: where finishing a workout leaves you. */
    fun goHome() {
        home?.invoke()
    }
}
