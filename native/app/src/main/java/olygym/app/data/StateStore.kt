package olygym.app.data

import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import olygym.app.lib.I18nCore
import olygym.app.lib.migrateToWeeks
import olygym.app.lib.needsWeekMigration
import olygym.app.lib.setWeightDecimals

/** What the shell can show. The empty case carries *why* it is empty, because the path is the fix. */
sealed interface PlanState {
    data object Loading : PlanState

    /** The profile parsed but holds no dated weeks. `fileExists` distinguishes first run from a gap. */
    data class Empty(val path: String, val fileExists: Boolean) : PlanState

    /** `raw` is the file as read — Phase 1 writes it back rather than re-encoding this model. */
    data class Loaded(val settings: Settings, val weeks: List<Week>, val raw: String) : PlanState

    data class Failed(val path: String, val reason: String) : PlanState
}

/**
 * Reads the profile the shipping React app writes:
 * /data/data/<package>/files/opengym-state.json — Capacitor's Directory.Data, which is this app's
 * own filesDir. On a debug build the package is olygym.app.dev, so the two apps never share it.
 *
 * Read-only in this phase: nothing here writes, so the shipping app's file cannot be damaged.
 */
class StateStore(
    private val file: File,
    /**
     * The locale pack for a language, read once when the profile names one. Injected rather than
     * loaded here so the whole read path — parse, migrate, decide — is a plain JVM test; the app
     * passes the asset reader.
     */
    private val localePack: (String) -> Map<String, String> = { emptyMap() },
) {
    private val json = Json {
        ignoreUnknownKeys = true   // the file may be newer than this app, and that is not an error
        coerceInputValues = true   // a null where a list belongs reads as the default
        explicitNulls = false
    }

    private val _state = MutableStateFlow<PlanState>(PlanState.Loading)
    val state: StateFlow<PlanState> = _state.asStateFlow()

    val path: String get() = file.absolutePath

    /**
     * Read the profile, then set the two display settings it carries.
     *
     * ponytail: synchronous, on the caller's thread. The file is a few hundred kilobytes and this
     * is the first thing the app needs; move it to Dispatchers.IO if a trace ever shows it.
     */
    fun load(now: LocalDate = LocalDate.now()) {
        if (!file.isFile) {
            _state.value = PlanState.Empty(path, fileExists = false)
            return
        }
        val raw = try {
            file.readText()
        } catch (e: Exception) {
            _state.value = PlanState.Failed(path, e.message ?: e::class.java.simpleName)
            return
        }
        try {
            val p = json.decodeFromString<Persisted>(raw)
            // A profile saved before the dated-weeks model has a repeating plan but no weeks, so
            // the new field is derived from it here. Additive — the old fields are left alone.
            val weeks = if (needsWeekMigration(p.weeks, p.routines)) {
                migrateToWeeks(p.routines, weekIds(p.week), p.weekStart, now)
            } else {
                p.weeks
            }
            val settings = Settings(p.unit, p.weekStart, p.lang, p.theme, p.accent, p.wdec)
            I18nCore.setLangState(settings.lang, localePack(settings.lang))
            setWeightDecimals(settings.wdec)
            _state.value = if (weeks.isEmpty()) {
                PlanState.Empty(path, fileExists = true)
            } else {
                PlanState.Loaded(settings, weeks, raw)
            }
        } catch (e: Exception) {
            _state.value = PlanState.Failed(path, e.message ?: e::class.java.simpleName)
        }
    }

    /**
     * The old S.week shape: weekday -> routine ids. The JS reads it with `[].concat(week[key] || [])`
     * because an older writer may have stored one id as a bare string, so both are accepted.
     */
    private fun weekIds(week: JsonObject?): Map<String, List<String>> =
        (week ?: JsonObject(emptyMap())).mapValues { (_, value) ->
            when (value) {
                is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                is JsonPrimitive -> listOfNotNull(value.contentOrNull)
                else -> emptyList()
            }
        }

    companion object {
        /** The same name and directory the Capacitor build uses. See docs/PORT-TO-KOTLIN.md. */
        const val FILE = "opengym-state.json"
    }
}
