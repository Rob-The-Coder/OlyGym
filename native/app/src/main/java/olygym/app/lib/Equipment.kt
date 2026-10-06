package olygym.app.lib

import kotlinx.serialization.json.JsonObject
import olygym.app.data.Exercise
import olygym.app.data.arr
import olygym.app.data.asStr
import olygym.app.data.bool
import olygym.app.data.present
import olygym.app.data.str

/*
 * Equipment profiles — a port of the reading half of frontend/src/lib/equipment.js. A profile is a
 * name plus a checklist of what you have; the picker uses it to hide what you cannot do. Editing
 * the profiles themselves is the Settings screen's job and is a later phase, so this file has no
 * writer.
 */

/** Body weight is never gated by a profile: no setup can take it away from you. */
private const val ALWAYS_AVAILABLE = "body weight"

/** The active profile, or null when filtering is off or nothing is selected. */
fun activeProfile(S: JsonObject?): JsonObject? {
    if (S?.bool("equipFilterOn") != true) return null
    val id = S.str("activeEquipId") ?: return null
    return S.arr("equipProfiles").mapNotNull { it as? JsonObject }.firstOrNull { it.str("id") == id }
}

/**
 * Whether an exercise is usable under the active profile. With filtering off, or no profile
 * selected, everything is available: this is purely additive, never a trap that hides the whole
 * library because nothing has been set up yet.
 */
fun exAvailable(S: JsonObject?, ex: Exercise): Boolean {
    val profile = activeProfile(S) ?: return true
    val eq = ex.eq
    if (eq.isNullOrBlank() || eq == ALWAYS_AVAILABLE) return true
    return profile.arr("equipment").any { it.asStr() == eq }
}

/** The profile's own chosen equipment, as a set of ids. */
fun profileEquipment(profile: JsonObject?): List<String> =
    profile?.arr("equipment")?.mapNotNull { it.asStr() }.orEmpty()

/** Kept so a caller can tell "filtering is on but nothing is selected" from "filtering is off". */
fun equipmentFiltering(S: JsonObject?): Boolean =
    S?.bool("equipFilterOn") == true && S["activeEquipId"].present()
