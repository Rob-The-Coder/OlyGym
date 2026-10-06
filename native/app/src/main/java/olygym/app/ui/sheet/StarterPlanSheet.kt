package olygym.app.ui.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import olygym.app.lib.DAYN
import olygym.app.lib.appendWeek
import olygym.app.lib.buildStarterPlan
import olygym.app.lib.starterPlanDays
import olygym.app.lib.starterPlanOptions
import olygym.app.lib.weekFor
import olygym.app.lib.todayISO
import olygym.app.ui.components.Accessory
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.ListRow
import olygym.app.ui.components.RowDivider
import olygym.app.ui.components.Section
import olygym.app.ui.currentProfile
import olygym.app.ui.editProfile
import olygym.app.ui.profileNow
import olygym.app.ui.t
import olygym.app.ui.ui

/*
 * The starter-plan chooser — a port of StarterPlanChooser and loadStarterPlan in
 * frontend/src/sheets.jsx. The catalog itself is lib/Starter.kt; this is the way in.
 */

private data class StarterCopy(val name: String, val about: String)

/**
 * The plan names and descriptions, written as string literals inside t() calls because that is the
 * only place the web's check-source-strings script finds them — copy parked in the catalog would
 * quietly ship English in every language. Lazy, as in the web, so a language change is picked up
 * rather than frozen at the first read.
 */
private val STARTER_COPY: Map<String, () -> StarterCopy> = mapOf(
    "ppl" to {
        StarterCopy(
            t("Snatch / Clean & Jerk / Squat"),
            t("Snatch, clean & jerk and squat/pull each get their own day."),
        )
    },
    "upper-lower" to {
        StarterCopy(t("Technique / Strength"), t("Classic-lift technique twice, squat/pull strength twice."))
    },
    "full-body" to {
        StarterCopy(t("Full Body"), t("Three sessions, a classic lift plus squat and pull each time."))
    },
    "5x5" to { StarterCopy(t("5×5"), t("Five sets of five on the main barbell lifts.")) },
)

fun starterPlanSheet() {
    ui.openSheet { close -> StarterPlanChooser(close) }
}

/**
 * Adds the plan's days as a fresh dated week. Existing weeks are never touched, and an id with no
 * plan behind it changes nothing at all — buildStarterPlan returns null for one.
 */
private fun loadStarterPlan(planId: String): Boolean {
    val profile = profileNow() ?: return false
    val copy = STARTER_COPY[planId]?.invoke() ?: return false
    val week = buildStarterPlan(planId, copy.name, profile.settings.weekStart) ?: return false
    editProfile { raw -> appendWeek(raw, week) }
    ui.toast(t("{0} loaded", copy.name))
    return true
}

/**
 * The weekdays a plan claims, as one list. ponytail: a plain comma join. The web reaches for
 * Intl.ListFormat, which java.text has no equivalent of; this only ever lands in a confirmation
 * body.
 */
private fun dayList(days: List<Int>): String = days.joinToString(", ") { t(DAYN[it]) }

@Composable
private fun StarterPlanChooser(close: () -> Unit) {
    val profile = currentProfile() ?: return
    val choose = { id: String, name: String ->
        val days = starterPlanDays(id).orEmpty()
        close()
        // A confirmation is only worth showing when one of those days is already taken in the week
        // the plan would land in.
        val current = weekFor(profile.weeks, todayISO())
        val taken = { dow: Int -> current?.days?.any { it.dow == dow && it.ex.isNotEmpty() } == true }
        if (days.none { taken(it) }) {
            loadStarterPlan(id)
        } else {
            confirmSheet(
                title = t("Load {0}?", name),
                message = t("A new week will be added on {0}. Nothing you already have is changed.", dayList(days)),
                confirmText = t("Load plan"),
                onConfirm = { loadStarterPlan(id) },
            )
        }
    }
    Column(Modifier.fillMaxWidth()) {
        SheetTitle(t("Choose starter plan"))
        Section {
            starterPlanOptions().forEachIndexed { index, option ->
                if (index > 0) RowDivider()
                val copy = STARTER_COPY[option.first]?.invoke() ?: return@forEachIndexed
                ListRow(
                    title = copy.name,
                    icon = Glyph.SPARKLES,
                    subtitle = t("{0} days per week", option.second) + " · " + copy.about,
                    accessory = Accessory.CHEVRON,
                    onClick = { choose(option.first, copy.name) },
                )
            }
        }
    }
}
