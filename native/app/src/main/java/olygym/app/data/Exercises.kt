package olygym.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import olygym.app.lib.I18nCore
import olygym.app.lib.capWords

/** One catalogue entry. The fields the app does not read yet are kept so a later screen has them. */
@Serializable
data class Exercise(
    val id: String = "",
    val n: String = "",
    val bp: String? = null,
    val eq: String? = null,
    val tg: String? = null,
    val sm: List<String> = emptyList(),
    val desc: String? = null,
    val st: List<String> = emptyList(),
    val yt: String? = null,
    val src: String? = null,
)

/**
 * The catalogue, indexed by id. Module-level like lib/exercises.js's EXIDX, because every screen
 * needs it and threading it through would be noise. Installed once, at startup.
 *
 * Custom exercises (S.customEx) are not merged in this phase: a day that references one shows its
 * id. That read arrives with the picker.
 */
object Catalogue {
    private var builtIn: List<Exercise> = emptyList()
    private var byId: Map<String, Exercise> = emptyMap()
    private var customIds: List<String> = emptyList()

    val size: Int get() = byId.size

    /** The built-in catalogue in file order — exercises.js's CATALOGUE, without the customs. */
    val list: List<Exercise> get() = builtIn

    fun install(list: List<Exercise>) {
        builtIn = list
        byId = list.associateBy { it.id }
        customIds = emptyList()
    }

    operator fun get(id: String): Exercise? = byId[id]

    /**
     * Merge S.customEx into the index, dropping the previous customs first (exercises.js's
     * registerCustom). A custom that shadowed a built-in id is removed and the built-in restored.
     */
    fun registerCustom(list: List<Exercise>) {
        val next = byId.toMutableMap()
        customIds.forEach { id ->
            next.remove(id)
            builtIn.find { it.id == id }?.let { next[id] = it }
        }
        customIds = list.map { it.id }
        list.forEach { next[it.id] = it }
        byId = next
    }

    /** The display name, translated when a name pack ships, and the id when it is not in the book. */
    fun nameOf(id: String): String {
        val ex = byId[id] ?: return capWords(id)
        return I18nCore.exerciseNameFor(ex.id, ex.n)
    }
}

/**
 * The two JS modules the React app bundles, shipped here as JSON and read once at startup.
 * native/tools/assets.mjs writes them from the frontend sources.
 */
object Assets {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun locale(context: Context, lang: String): Map<String, String> {
        if (lang == "en") return emptyMap()   // English is the key: no pack to load
        return runCatching {
            json.decodeFromString<Map<String, String>>(read(context, "i18n/$lang.json"))
        }.getOrDefault(emptyMap())
    }

    fun catalogue(context: Context): List<Exercise> =
        json.decodeFromString<List<Exercise>>(read(context, "exercises-data.json"))

    private fun read(context: Context, name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }
}
