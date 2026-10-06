package olygym.app.data

import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToStream
import olygym.app.lib.I18nCore
import olygym.app.lib.migrateToWeeks
import olygym.app.lib.needsWeekMigration
import olygym.app.lib.setWeightDecimals

/** What the shell can show. */
sealed interface AppState {
    data object Loading : AppState

    /** The file is there but could not be read. Nothing is written while this is the state. */
    data class Failed(val path: String, val reason: String) : AppState

    data class Ready(val profile: Profile) : AppState
}

/**
 * The profile as the screens read it.
 *
 * `raw` is the whole state object, exactly as it was read. Everything this app does not model stays
 * in it and a write is a *merge over* it, so a build that does not understand a field cannot drop
 * it — which is the only reason it is safe for two apps to take turns owning this file.
 *
 * The typed fields are the ones a screen needs on every recomposition. The session shapes (a workout
 * entry, a set row, `S.active`) stay JSON on purpose: see data/Js.kt for why.
 */
data class Profile(
    val raw: JsonObject,
    val path: String,
    /** False on a first run, which is what tells "no profile yet" from "a profile with no plan". */
    val fileExists: Boolean,
    val settings: Settings,
    val weeks: List<Week>,
    val active: JsonObject?,
    val workouts: List<JsonObject>,
    val bodyweight: List<JsonObject>,
) {
    /** The exercise of the running session the workout screen is on. */
    val cur: Int get() = active?.int("cur") ?: 0

    val entries: List<JsonObject> get() = active?.arr("entries").orEmpty().mapNotNull { it.asObj() }

    val currentEntry: JsonObject? get() = entries.getOrNull(cur)
}

/**
 * Reads and writes the profile the shipping React app keeps at
 * /data/data/<package>/files/opengym-state.json — Capacitor's Directory.Data, which is this app's
 * own filesDir. On a debug build the package is olygym.app.dev, so the two never share one.
 *
 * Writes are atomic (a temp file, fsync, rename) and coalesced off the main thread: the JS debounces
 * its file mirror by 800 ms and this drops superseded states instead, which matters because the
 * workout screen writes on every set that gets checked off.
 */
class StateStore(
    private val file: File,
    /**
     * The locale pack for a language, read once when the profile names one. Injected rather than
     * loaded here so the whole read path — parse, migrate, decide — is a plain JVM test; the app
     * passes the asset reader.
     */
    private val localePack: (String) -> Map<String, String> = { emptyMap() },
    /** False in tests, which then write synchronously and can assert the file straight away. */
    private val backgroundWrites: Boolean = true,
) {
    private val json = Json {
        ignoreUnknownKeys = true   // the file may be newer than this app, and that is not an error
        coerceInputValues = true   // a null where a list belongs reads as the default
        explicitNulls = false
    }

    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state.asStateFlow()

    /** Why the last write failed, or null. A training log that cannot be saved has to say so. */
    private val _writeError = MutableStateFlow<String?>(null)
    val writeError: StateFlow<String?> = _writeError.asStateFlow()

    /** The state object as last read or written. The writer's input; never re-derived from disk. */
    private var current: JsonObject = JsonObject(emptyMap())

    private val pending = MutableStateFlow<JsonObject?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * One writer at a time, and the writer always takes the *newest* state rather than the one it
     * was woken for — otherwise a background write that started earlier can land after a later one
     * and undo it.
     */
    private val writeLock = Any()

    val path: String get() = file.absolutePath

    init {
        if (backgroundWrites) {
            scope.launch {
                // Conflated: a burst of taps inside one write leaves the newest state, not a queue.
                pending.collect { writeLatest() }
            }
        }
    }

    /**
     * Read the profile, then apply the two display settings it carries.
     *
     * ponytail: synchronous, on the caller's thread. The file is a few hundred kilobytes and this is
     * the first thing the app needs; move it to Dispatchers.IO if a trace ever shows it.
     */
    fun load(now: LocalDate = LocalDate.now()) {
        if (!file.isFile) {
            current = JsonObject(emptyMap())
            _state.value = derive(current, fileExists = false, now = now)
            return
        }
        val raw = try {
            json.parseToJsonElement(file.readText()) as? JsonObject
                ?: throw IllegalArgumentException("the profile is not a JSON object")
        } catch (e: Exception) {
            _state.value = AppState.Failed(path, e.message ?: e::class.java.simpleName)
            return
        }
        current = raw
        _state.value = derive(raw, fileExists = true, now = now)
    }

    /**
     * Change the profile: `block` gets the state object and returns the next one, which is stamped
     * with the time and saved. The JS's `update(mut)` with the same stamp, except that the new state
     * is published before the file is written, so a tap never waits for the disk.
     *
     * A failed read refuses to write: the alternative is overwriting a profile that could still be
     * recovered by hand.
     */
    fun update(block: (JsonObject) -> JsonObject) {
        if (_state.value is AppState.Failed) return
        val next = block(current).with("_ts", System.currentTimeMillis())
        current = next
        _state.value = derive(next, fileExists = true, now = LocalDate.now())
        if (backgroundWrites) pending.value = next else write(next)
    }

    /** Write whatever is still pending, synchronously. Called when the app goes to the background. */
    fun flush() = writeLatest()

    private fun writeLatest() = synchronized(writeLock) {
        pending.value?.let { write(it) }
    }

    /** The read model, from the state object. Never throws: a shape that will not parse is Failed. */
    private fun derive(raw: JsonObject, fileExists: Boolean, now: LocalDate): AppState = try {
        val p = json.decodeFromJsonElement<Persisted>(raw)
        // A profile saved before the dated-weeks model has a repeating plan but no weeks, so the new
        // field is derived from it here. Additive — the old fields are left exactly as they are.
        val weeks = if (needsWeekMigration(p.weeks, p.routines)) {
            migrateToWeeks(p.routines, weekIds(p.week), p.weekStart, now)
        } else {
            p.weeks
        }
        val settings = Settings(
            unit = p.unit,
            weekStart = p.weekStart,
            lang = p.lang,
            theme = p.theme,
            accent = p.accent,
            wdec = p.wdec,
            targetW = p.targetW,
            // Absent reads as on: a profile written before this setting existed opened the weigh-in.
            weighIn = p.weighIn != false,
        )
        // Only on a change: setLangState reloads the pack and bumps the version, and this runs on
        // every write.
        if (I18nCore.lang != settings.lang) I18nCore.setLangState(settings.lang, localePack(settings.lang))
        setWeightDecimals(settings.wdec)
        AppState.Ready(
            Profile(
                raw = raw,
                path = path,
                fileExists = fileExists,
                settings = settings,
                weeks = weeks,
                active = raw.obj("active"),
                workouts = raw.arr("workouts").mapNotNull { it.asObj() },
                bodyweight = raw.arr("bodyweight").mapNotNull { it.asObj() },
            )
        )
    } catch (e: Exception) {
        AppState.Failed(path, e.message ?: e::class.java.simpleName)
    }

    /**
     * Temp file, flush to the disk, rename over the profile. The rename is atomic, so a phone that
     * dies mid-write leaves either the old file or the new one — never half of either.
     */
    private fun write(obj: JsonObject) {
        try {
            val tmp = File(file.parentFile, file.name + ".writing")
            FileOutputStream(tmp).use { out ->
                json.encodeToStream(obj, out)
                out.flush()
                out.fd.sync()
            }
            if (!tmp.renameTo(file)) {
                // The parent may not exist on a first run, or the rename may be refused; a plain
                // write is still better than losing the change.
                file.writeText(json.encodeToString(JsonObject.serializer(), obj))
                tmp.delete()
            }
            _writeError.value = null
        } catch (e: Exception) {
            _writeError.value = e.message ?: e::class.java.simpleName
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
