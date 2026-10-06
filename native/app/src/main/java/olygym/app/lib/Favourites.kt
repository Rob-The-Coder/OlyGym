package olygym.app.lib

import kotlinx.serialization.json.JsonObject
import olygym.app.data.Exercise
import olygym.app.data.arr
import olygym.app.data.asStr

/*
 * Favourite exercises (issue #6): a personal shortlist that floats to the top of the picker, the
 * Library and the muscle explorer, so building a routine takes fewer scrolls. A port of
 * frontend/src/lib/favourites.js.
 *
 * Stored as a flat id list in S.favEx, catalogue and custom ids alike. A profile written before the
 * field existed simply has no list, so every reader tolerates its absence.
 */

fun favIds(S: JsonObject?): List<String> = S?.arr("favEx")?.mapNotNull { it.asStr() }.orEmpty()

fun isFav(S: JsonObject?, id: String): Boolean = favIds(S).contains(id)

/**
 * The list after flipping one exercise. The JS mutates the state draft and returns the new state;
 * here the caller stores the returned list — see PORTING.md's mutation note.
 */
fun toggledFavs(S: JsonObject?, id: String): List<String> {
    val list = favIds(S)
    return if (list.contains(id)) list.filter { it != id } else list + id
}

/**
 * Favourites first, everything else after — both halves keep the order they came in, so a list that
 * is already sorted by name (or by usage) stays that way within each half.
 */
fun sortFavouritesFirst(list: List<Exercise>, S: JsonObject?): List<Exercise> {
    val fav = favIds(S)
    if (fav.isEmpty()) return list
    val set = fav.toSet()
    return list.filter { set.contains(it.id) } + list.filterNot { set.contains(it.id) }
}
