package olygym.app.ui

import cafe.adriel.voyager.core.screen.Screen

/**
 * Every screen in the app. Voyager keeps the stack by key and restores it after process death, so a
 * screen that carries arguments must fold them into its key — an object needs nothing.
 */
abstract class AppScreen : Screen {
    override val key: String get() = this::class.java.name
}
