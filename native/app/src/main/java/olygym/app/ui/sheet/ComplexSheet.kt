package olygym.app.ui.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonArray
import olygym.app.data.arr
import olygym.app.data.asArr
import olygym.app.data.asInt
import olygym.app.data.asObj
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.objectAt
import olygym.app.data.str
import olygym.app.lib.patchUnit
import olygym.app.lib.supersetUnits
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Stepper
import olygym.app.ui.currentProfile
import olygym.app.ui.editProfile
import olygym.app.ui.t
import olygym.app.ui.ui

/*
 * The complex sheet — a port of ComplexConfig in frontend/src/sheets.jsx. A complex is one card in
 * a day, and its sets and load belong to the whole thing: the coach writes "3+3 @ 30kg" once, not
 * once per movement. The rows keep their own reps (that is what makes a complex a complex), so this
 * sheet writes only the two shared numbers, to every member of the unit.
 */
fun complexConfigSheet(weekId: String, dayIndex: Int, unitIndex: Int): Long =
    ui.openSheet { close -> ComplexConfig(weekId, dayIndex, unitIndex, close) }

@Composable
private fun ComplexConfig(weekId: String, dayIndex: Int, unitIndex: Int, close: () -> Unit) {
    val profile = currentProfile() ?: return
    // Read fresh on every recomposition so the steppers and the rows behind the sheet agree; the
    // same lookup repeats inside the writer, because the store replaces the state tree per write.
    val week = profile.raw.arr("weeks").mapNotNull { it.asObj() }.firstOrNull { it.str("id") == weekId }
    val list = week.arr("days").getOrNull(dayIndex)?.asObj()?.arr("ex") ?: JsonArray(emptyList())
    val unit = supersetUnits(list).getOrNull(unitIndex).asArr() ?: JsonArray(emptyList())
    val first = unit.firstOrNull()?.asInt()?.let { list.objectAt(it) }
    val unit2 = profile.settings.unit

    Column(Modifier.fillMaxWidth()) {
        SheetTitle(t("Complex"))
        if (first == null) {
            Button(t("Done"), onClick = close, variant = ButtonVariant.PRIMARY)
            return@Column
        }
        SheetNote(t("Sets and load are the same for every exercise in the complex — each one keeps its own reps."))
        Stepper(
            label = t("Sets"),
            value = first.num("sets") ?: 1.0,
            step = 1.0,
            decimal = false,
            onChange = { editProfile { raw -> patchUnit(raw, weekId, dayIndex, unitIndex, js("sets" to it)) } },
        )
        Stepper(
            label = t("Weight ({0})", unit2),
            value = first.num("weight") ?: 0.0,
            step = 2.5,
            onChange = { editProfile { raw -> patchUnit(raw, weekId, dayIndex, unitIndex, js("weight" to it)) } },
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            text = t("Done"),
            onClick = close,
            variant = ButtonVariant.PRIMARY,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
