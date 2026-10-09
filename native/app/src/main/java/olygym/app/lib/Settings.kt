package olygym.app.lib

import kotlinx.serialization.json.JsonObject

/*
 * The settings the profile carries, and the lists the Settings screen offers — a port of the option
 * arrays Settings.jsx builds inline. The keys are the state file's own, unchanged.
 */

/** The two languages the app ships: English, and the Italian pack in the assets. */
val LANGUAGES: Map<String, String> = linkedMapOf("en" to "English", "it" to "Italiano")

/** The languages exercise instructions ship in; anything else reads them in English. */
val INSTR_LANGS = listOf("en")

val REST_OPTIONS = listOf(0, 60, 90, 120, 150, 180)
val WORKOUT_VIEWS = listOf("cards", "list", "compact")
val THEMES = listOf("dark", "light", "system")
val EFFORT_MODES = listOf("none", "rir", "rpe")

/**
 * The workout header's picture: shown, or hidden. The web's old three-way size has one option worth
 * offering left, and anything but "off" — including the legacy "mini" — reads as shown.
 */
val GIF_SIZES = listOf("full", "off")
val WEIGHT_DECIMALS = listOf(1, 2)
val WEEK_STARTS = listOf(MONDAY, SUNDAY)

/**
 * What "reset everything" leaves behind: an empty state object.
 *
 * The web copies its DEF over the store, because that object *is* where its defaults live. Here
 * every default is in code — Persisted's own defaults and the settings derivation in StateStore —
 * so there is nothing to copy: an empty object reads back as a fresh profile, and the React app
 * merges its own DEF over whatever it finds, which is what its reset does too.
 */
fun resetState(): JsonObject = JsonObject(emptyMap())
