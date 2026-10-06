package olygym.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.serialization.json.JsonObject
import olygym.app.OlyGymApp
import olygym.app.data.AppState
import olygym.app.data.Profile
import olygym.app.lib.I18nCore

/**
 * How a screen reaches the app's two holders. The React app does the same thing at module scope
 * (the ui() and update() helpers in sheets.jsx): one store, one UI holder, read from anywhere.
 */

/** The current profile, or null while it is loading or after a read that failed. */
@Composable
fun currentProfile(): Profile? =
    (OlyGymApp.store.state.collectAsState().value as? AppState.Ready)?.profile

/** The same value outside a composition, for the imperative paths. */
fun profileNow(): Profile? = (OlyGymApp.store.state.value as? AppState.Ready)?.profile

/** One write of the state object: the callback returns the next one. */
fun editProfile(block: (JsonObject) -> JsonObject) = OlyGymApp.store.update(block)

/** The UI holder, for a screen that needs to open a sheet, toast or start a timer. */
val ui: UiState get() = OlyGymApp.ui

/** The translation, spelled the way the web app spells it. */
internal fun t(s: String, vararg args: Any?): String = I18nCore.t(s, *args)

/** A stored message — [key, arg, ...] as the helpers write a plan's reason. */
internal fun tMessage(message: kotlinx.serialization.json.JsonArray): String = I18nCore.tMessage(message)
