package olygym.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import olygym.app.data.Profile
import olygym.app.data.bool
import olygym.app.data.int
import olygym.app.data.str
import olygym.app.data.with
import olygym.app.data.without
import olygym.app.lib.ACCENTS
import olygym.app.lib.DEFAULT_ACCENT
import olygym.app.lib.EFFORT_MODES
import olygym.app.lib.INSTR_LANGS
import olygym.app.lib.LANGUAGES
import olygym.app.lib.REST_OPTIONS
import olygym.app.lib.THEMES
import olygym.app.lib.WEIGHT_DECIMALS
import olygym.app.lib.WEEK_STARTS
import olygym.app.lib.WORKOUT_VIEWS
import olygym.app.lib.effortOf
import olygym.app.lib.resetState
import olygym.app.lib.todayISO
import olygym.app.lib.weekStartOf
import olygym.app.ui.AppScreen
import olygym.app.ui.Nav
import olygym.app.ui.components.Accessory
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.IconButton
import olygym.app.ui.components.ListRow
import olygym.app.ui.components.OlyAppBar
import olygym.app.ui.components.Section
import olygym.app.ui.components.Segmented
import olygym.app.ui.components.olyAppBarScrollBehavior
import olygym.app.ui.currentProfile
import olygym.app.ui.editProfile
import olygym.app.ui.sheet.SelectOption
import olygym.app.ui.sheet.SelectRow
import olygym.app.ui.sheet.confirmSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import olygym.app.lib.readXlsx
import olygym.app.ui.sheet.coachImportSheet
import olygym.app.ui.sheet.starterPlanSheet
import olygym.app.ui.sheet.weightClassesSheet
import olygym.app.ui.t
import olygym.app.ui.ui

/**
 * Settings — a port of frontend/src/views/Settings.jsx: the general preferences, the ones the
 * workout screen obeys, the appearance, and the data.
 *
 * Not ported, and each for a reason of its own:
 * - **Weight classes**, **import from Google Drive**, **auto-backup** and
 *   **the update check**: phase 3 (competitions, the spreadsheet, the background jobs).
 * - **Keep the screen awake**, **exercise pictures**, **demo videos** and **the reminder card**: they
 *   are the Capacitor build's job (a wake lock, the media packs, a local notification), which the
 *   native app does with its own platform pieces in their own phases.
 * - **Workout controls** and **the body diagram**: their sheets and their screen are not ported yet,
 *   and a switch that wrote a key nothing reads would be a lie. The automatic-progression help is in
 *   the same position — the row works, the (i) is not there.
 * - **Play sounds on silent**: iOS only.
 */
object SettingsScreen : AppScreen() {
    @Composable
    override fun Content() {
        val profile = currentProfile() ?: return
        Settings(profile)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Settings(profile: Profile) {
    val scroll = olyAppBarScrollBehavior()
    val context = LocalContext.current
    val S = profile.raw

    val write: ((JsonObject) -> JsonObject) -> Unit = { block -> editProfile(block) }

    // The whole state object, pretty-printed, into a file the user picks; and the same back again.
    val json = Json { prettyPrint = true }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.encodeToString(JsonObject.serializer(), profile.raw).toByteArray())
            }
        }.onSuccess { ui.toast(t("Backup exported")) }
            .onFailure { ui.toast(t("Export failed: {0}", it.message ?: "")) }
    }
    // The coach's workbook is read off the main thread: the zip and its XML are the one piece of
    // real work this screen does, and the review it opens is derived state from then on.
    val coachScope = rememberCoroutineScope()
    val coachLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coachScope.launch {
            val workbook = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { readXlsx(it.readBytes()) }
                }.getOrNull()
            }
            if (workbook == null || workbook.sheets.isEmpty()) {
                ui.toast(t("Import failed: {0}", "not an Excel workbook"))
            } else {
                coachImportSheet(workbook.sheets)
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        }.getOrNull()
        val data = text?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        val looksLikeBackup = data != null &&
            (data["workouts"] is JsonArray || data["weeks"] is JsonArray || data["routines"] is JsonArray)
        if (!looksLikeBackup) {
            ui.toast(t("Import failed: {0}", "not an OlyGym backup"))
        } else {
            confirmSheet(
                title = t("Import backup?"),
                message = t("This replaces all current data with the backup file."),
                confirmText = t("Import"),
                danger = true,
                onConfirm = {
                    editProfile { _ -> data!! }
                    ui.toast(t("Backup imported"))
                },
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            OlyAppBar(
                title = t("Settings"),
                scrollBehavior = scroll,
                leading = { IconButton(Glyph.CHEVRON_LEFT, onClick = { Nav.back() }) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Section(title = t("General")) {
                SelectRow(
                    title = t("Language"),
                    value = S.str("lang") ?: "en",
                    options = LANGUAGES.map { (code, name) ->
                        SelectOption(
                            value = code,
                            label = name,
                            subtitle = if (code in INSTR_LANGS) {
                                null
                            } else {
                                t("Exercise instructions aren't available in this language yet — they stay in English.")
                            },
                        )
                    },
                    onChange = { value -> write { it.with("lang", value) } },
                )
                // Display only: one decimal reads fine for plate-loadable numbers, two for anyone
                // whose per-side figure lands on .25 or .75 (issue #139).
                InlineRow(t("Weight decimals"), t("How precisely weights are shown.")) {
                    Segmented(
                        options = WEIGHT_DECIMALS.map { it.toString() },
                        labels = listOf(t("0.5"), t("0.25")),
                        value = (if (S.int("wdec") == 2) 2 else 1).toString(),
                        onChange = { value -> write { it.with("wdec", value.toInt()) } },
                        modifier = Modifier.fillMaxWidth(0.5f),
                    )
                }
                InlineRow(t("Week starts on")) {
                    Segmented(
                        options = WEEK_STARTS.map { it.toString() },
                        labels = listOf(t("Monday"), t("Sunday")),
                        value = weekStartOf(S.int("weekStart")).toString(),
                        onChange = { value -> write { it.with("weekStart", value.toInt()) } },
                        modifier = Modifier.fillMaxWidth(0.6f),
                    )
                }
            }

            Section(title = t("Workout")) {
                SwitchRow(
                    title = t("Weigh in before workouts"),
                    subtitle = t("Asks for your body weight when a workout starts. Off starts the session straight away."),
                    icon = Glyph.SCALE,
                    checked = S.bool("weighIn") != false,
                    onChange = { value -> write { it.with("weighIn", value) } },
                )
                SwitchRow(
                    title = t("Automatic progression"),
                    icon = Glyph.ARROW_UP,
                    checked = S.bool("autoProg") == true,
                    onChange = { value -> write { it.with("autoProg", value) } },
                )
                SelectRow(
                    title = t("Workout view"),
                    value = (S.str("workoutView") ?: "cards").takeIf { it in WORKOUT_VIEWS } ?: "cards",
                    icon = Glyph.LIST,
                    options = WORKOUT_VIEWS.map { SelectOption(it, t(it.replaceFirstChar { c -> c.uppercase() })) },
                    onChange = { value -> write { it.with("workoutView", value) } },
                )
                SelectRow(
                    title = t("Rest timer"),
                    value = (S.int("restSec") ?: 90).toString(),
                    icon = Glyph.TIMER,
                    options = REST_OPTIONS.map {
                        SelectOption(it.toString(), if (it == 0) t("Off") else it.toString() + "s")
                    },
                    onChange = { value -> write { it.with("restSec", value.toIntOrNull() ?: 90) } },
                )
                SwitchRow(
                    title = t("Sounds"),
                    checked = S.bool("sound") != false,
                    onChange = { value -> write { it.with("sound", value) } },
                )
                SwitchRow(
                    title = t("Flash screen when timer ends"),
                    checked = S.bool("timerFlash") == true,
                    onChange = { value -> write { it.with("timerFlash", value) } },
                )
                SelectRow(
                    title = t("Effort per set"),
                    value = effortOf(S),
                    icon = Glyph.TARGET,
                    options = EFFORT_MODES.map {
                        SelectOption(it, when (it) {
                            "rir" -> t("RIR")
                            "rpe" -> t("RPE")
                            else -> t("Off")
                        })
                    },
                    onChange = { value ->
                        write { it.with("effort", value).without("showRir") }
                    },
                )
            }

            // The categories belong to a federation, not to the app: they change every few years,
            // and a meet keeps the string it was saved with.
            Section(title = t("Competition")) {
                ListRow(
                    title = t("Weight classes"),
                    icon = Glyph.TROPHY,
                    subtitle = t("The categories your federation runs. Edit them when the rules change."),
                    accessory = Accessory.CHEVRON,
                    onClick = { weightClassesSheet() },
                )
            }

            Section(title = t("Appearance")) {
                InlineRow(t("Theme")) {
                    Segmented(
                        options = THEMES,
                        labels = listOf(t("Dark"), t("Light"), t("System")),
                        value = (S.str("theme") ?: "dark").takeIf { it in THEMES } ?: "dark",
                        onChange = { value -> write { it.with("theme", value) } },
                        modifier = Modifier.fillMaxWidth(0.72f),
                    )
                }
                Column(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 14.dp)) {
                    Text(
                        text = t("Accent color"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val current = S.str("accent") ?: DEFAULT_ACCENT
                        ACCENTS.forEach { (name, seed) ->
                            val on = current == name
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF000000L or (seed.removePrefix("#").toLongOrNull(16) ?: 0L)))
                                    .then(
                                        if (on) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable { write { it.with("accent", name) } },
                            )
                        }
                    }
                }
            }

            Section(title = t("Data")) {
                ListRow(
                    title = t("Load starter plan"),
                    icon = Glyph.SPARKLES,
                    accessory = Accessory.CHEVRON,
                    onClick = { starterPlanSheet() },
                )
                ListRow(
                    title = t("Import a coach’s plan"),
                    subtitle = t("An Excel week: his exercises, sets, reps and loads, read and reviewed before they land in your plan"),
                    accessory = Accessory.CHEVRON,
                    onClick = { coachLauncher.launch(arrayOf("*/*")) },
                )
                ListRow(
                    title = t("Import backup"),
                    accessory = Accessory.CHEVRON,
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                )
                ListRow(
                    title = t("Export backup (JSON)"),
                    accessory = Accessory.CHEVRON,
                    onClick = { exportLauncher.launch("opengym-backup-" + todayISO() + ".json") },
                )
                ListRow(
                    title = t("Reset everything"),
                    icon = Glyph.TRASH,
                    danger = true,
                    onClick = {
                        confirmSheet(
                            title = t("Reset everything?"),
                            message = t("Deletes your plan, workouts and body weight on this device. This cannot be undone."),
                            confirmText = t("Delete everything"),
                            danger = true,
                            onConfirm = {
                                editProfile { resetState() }
                                Nav.goHome()
                                ui.toast(t("All data reset"))
                            },
                        )
                    },
                )
            }
        }
    }
}

/** A row that is a label and a control, which is how the segmented settings read. */
@Composable
private fun InlineRow(
    title: String,
    subtitle: String? = null,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        control()
    }
}

/** The same row with a switch, which is what the workout settings read. */
@Composable
private fun SwitchRow(
    title: String,
    icon: Glyph? = null,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        trailing = { Switch(checked = checked, onCheckedChange = onChange) },
        onClick = { onChange(!checked) },
    )
}
