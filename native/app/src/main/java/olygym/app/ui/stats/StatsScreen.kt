package olygym.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import olygym.app.OlyGymApp
import olygym.app.data.AppState
import olygym.app.data.Profile
import olygym.app.data.arr
import olygym.app.data.asArr
import olygym.app.data.asObj
import olygym.app.data.asStr
import olygym.app.data.js
import olygym.app.data.num
import olygym.app.data.str
import olygym.app.data.truthy
import olygym.app.lib.EffortBin
import olygym.app.lib.HARD_RIR
import olygym.app.lib.MUSCLE_NAME
import olygym.app.lib.MUSCLES
import olygym.app.lib.avgRir
import olygym.app.lib.competitionBests
import olygym.app.lib.displayScale
import olygym.app.lib.effortHistogram
import olygym.app.lib.effortOf
import olygym.app.lib.effortSummary
import olygym.app.lib.effortWeeks
import olygym.app.lib.fatigueOf
import olygym.app.lib.fatigueStateOf
import olygym.app.lib.hasEffort
import olygym.app.lib.fmtDate
import olygym.app.lib.fmtNum
import olygym.app.lib.isHardSet
import olygym.app.lib.lastBW
import olygym.app.lib.levelsOf
import olygym.app.lib.loadOfWorkouts
import olygym.app.lib.progressExercises
import olygym.app.lib.progressSeries
import olygym.app.lib.scaleName
import olygym.app.lib.setLabel
import olygym.app.lib.toScale
import olygym.app.lib.muscleBalanceWindow
import olygym.app.lib.rankOf
import olygym.app.lib.streakWeeks
import olygym.app.lib.todayISO
import olygym.app.lib.weekStartOf
import olygym.app.lib.workoutsOnDate
import olygym.app.ui.AppScreen
import olygym.app.ui.components.Accessory
import olygym.app.ui.components.BodyMap
import olygym.app.ui.components.BodyMapLegend
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonSize
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.CardHead
import olygym.app.ui.components.FatigueLegend
import olygym.app.ui.components.ListRow
import olygym.app.ui.components.OlyAppBar
import olygym.app.ui.components.Overline
import olygym.app.ui.components.Section
import olygym.app.ui.components.SectionCard
import olygym.app.ui.components.Segmented
import olygym.app.ui.components.Tile
import olygym.app.ui.components.WorkoutRow
import olygym.app.ui.components.olyAppBarScrollBehavior
import olygym.app.ui.currentProfile
import olygym.app.ui.Nav
import olygym.app.ui.chart.ChartPoint
import olygym.app.ui.chart.Heatmap
import olygym.app.ui.chart.LineChart
import olygym.app.ui.competitions.CompetitionsScreen
import olygym.app.ui.components.Glyph
import olygym.app.ui.history.HistoryScreen
import olygym.app.ui.sheet.SelectOption
import olygym.app.ui.sheet.SelectRow
import olygym.app.ui.sheet.bwDeltaColor
import olygym.app.ui.sheet.calendarSheet
import olygym.app.ui.sheet.goalSheet
import olygym.app.ui.sheet.weighInSheet
import olygym.app.ui.sheet.workoutDetailSheet
import olygym.app.ui.t
import olygym.app.ui.theme.FullShape
import olygym.app.ui.theme.extraColors
import olygym.app.ui.theme.fatigueLevelColor
import olygym.app.ui.theme.levelColor

/**
 * The analytics hub — the Stats tab, a port of frontend/src/views/Stats.jsx: the four totals, a year
 * of activity, the muscle balance (and its fatigue view), how hard the training has been, the body
 * weight and one exercise's own curve, then the last few sessions.
 *
 * Not ported in this phase, and stated where each would show:
 * - competitions (the Competitions card at the foot of the screen), and the exercise-history sheet
 *   behind the exercise detail's row.
 * - the search inside the exercise picker (the app's SelectRow has none yet); the sheet lists every
 *   exercise with a history, which is the list the search would filter.
 */
/**
 * The fatigue ramp's level thresholds, from Stats.jsx: below the first rule is l0, and the last
 * matching rule wins — the same shape levelsOf() takes.
 */
private val FATIGUE_LEVELS: JsonArray = JsonArray(
    listOf(
        js("at" to 0.0, "level" to 0),
        js("at" to 0.15, "level" to 1),
        js("at" to 0.25, "level" to 2),
        js("at" to 0.4, "level" to 3),
        js("at" to 0.55, "level" to 4, "exclusive" to true),
    ),
)

object StatsScreen : AppScreen() {
    @Composable
    override fun Content() {
        val state by OlyGymApp.store.state.collectAsState()
        when (val s = state) {
            is AppState.Ready -> Stats(s.profile)
            else -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    text = t("Not ported yet."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Stats(profile: Profile) {
    val S = profile.raw
    val unit = profile.settings.unit
    val iso = todayISO()
    val weekStart = weekStartOf(profile.settings.weekStart)
    val scroll = olyAppBarScrollBehavior()
    var range by remember { mutableStateOf(90) }
    val workouts = S.arr("workouts")
    val now = System.currentTimeMillis().toDouble()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            OlyAppBar(
                title = t("Stats"),
                scrollBehavior = scroll,
                subtitle = t("Progress & history"),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Overline(t("Overview"), Modifier.padding(top = 6.dp, bottom = 7.dp))
            // Two by two, which is the web's own grid on a phone (.tiles is 1fr 1fr; four
            // across is the >=1100px rule). Four across leaves every label an ellipsis.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(t("Workouts"), workouts.size.toString(), Modifier.weight(1f), labelWrap = true)
                Tile(
                    t("This month"),
                    workouts.count { it.asObj()?.str("d")?.startsWith(iso.take(7)) == true }.toString(),
                    Modifier.weight(1f),
                    labelWrap = true,
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(t("Week streak"), streakWeeks(S).toString(), Modifier.weight(1f), labelWrap = true)
                val delta30 = weightDelta30(profile)
                val currentW = lastBW(S)?.asObj()?.num("w") ?: 0.0
                Tile(
                    t("Weight 30d"),
                    delta30?.let { if (it > 0) "+" + fmtNum(it) else fmtNum(it) } ?: "—",
                    Modifier.weight(1f),
                    valueColor = if (delta30 == null) null else bwDeltaColor(delta30, profile.settings.targetW, currentW),
                    labelWrap = true,
                )
            }

            SectionCard(Modifier.padding(top = 12.dp)) {
                CardHead(t("Activity — last 12 months"), subtitle = t("by time trained"))
                Heatmap(
                    workouts = S["workouts"],
                    todayIso = iso,
                    weekStart = weekStart,
                    unit = unit,
                    modifier = Modifier.padding(top = 10.dp),
                    // One session opens it; several open the calendar on that month, which is where a
                    // day's own list of sessions lives.
                    onDay = { dayIso ->
                        val onDay = workoutsOnDate(S["workouts"], dayIso)
                        if (onDay.size == 1) workoutDetailSheet(onDay[0])
                        else if (onDay.isNotEmpty()) calendarSheet(dayIso)
                    },
                )
            }

            if (workouts.isNotEmpty()) {
                Overline(t("Balance"), Modifier.padding(top = 18.dp, bottom = 7.dp))
                MuscleBalanceCard(profile, iso, weekStart, now)
            }
            if (hasEffort(S)) {
                Overline(t("Effort"), Modifier.padding(top = 18.dp, bottom = 7.dp))
                EffortCard(profile)
            }

            CompetitionsCard(profile)

            Overline(t("Progress"), Modifier.padding(top = 18.dp, bottom = 7.dp))
            BodyWeightCard(profile, range) { range = it }
            ExerciseProgressCard(profile)

            if (workouts.isNotEmpty()) RecentWorkouts(profile)
        }
    }
}

/** The last 30 days of weigh-ins, first to last, or null when there are fewer than two. */
internal fun weightDelta30(profile: Profile): Double? {
    val cutoff = System.currentTimeMillis() - 30L * 86_400_000L
    val recent = profile.bodyweight.filter { element ->
        val w = element.asObj() ?: return@filter false
        val at = w.num("t")
            ?: w.str("d")?.let { java.time.LocalDate.parse(it).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli().toDouble() }
            ?: 0.0
        at > cutoff
    }
    if (recent.size < 2) return null
    val first = recent.first().asObj()?.num("w") ?: return null
    val last = recent.last().asObj()?.num("w") ?: return null
    return last - first
}

/**
 * The muscle balance and its fatigue view, a port of MuscleBalance in Stats.jsx.
 *
 * ponytail: the readings are the same and drawn as ranked bars, where the web draws them on a body.
 * The body map is ~90 KB of paths and a path renderer; the bars give every number the map would.
 */
@Composable
private fun MuscleBalanceCard(profile: Profile, iso: String, weekStart: Int, now: Double) {
    val S = profile.raw
    var view by remember { mutableStateOf("balance") }
    var win by remember { mutableStateOf(7) }
    var hard by remember { mutableStateOf(false) }
    // The tapped muscle, or null. The web drops the selection whenever the window or the scale
    // changes, because the row under the map is then about a different reading.
    var sel by remember { mutableStateOf<String?>(null) }
    val inWin = muscleBalanceWindow(S["workouts"], win, now, iso, weekStart)
    val rated = inWin.any { w ->
        val entries = w.asObj()?.arr("entries") ?: JsonArray(emptyList())
        entries.any { e ->
            val sets = e.asObj()?.arr("sets") ?: JsonArray(emptyList())
            sets.any { s -> truthy(s.asObj()?.get("done")) && isHardSet(s) }
        }
    }
    val on = hard && rated
    val load = loadOfWorkouts(inWin, if (on) ({ element: kotlinx.serialization.json.JsonElement -> isHardSet(element) }) else null)
    val rank = rankOf(load)
    val worked = rank.arr("worked").mapNotNull { it.asStr() }
    val missed = rank.arr("missed").mapNotNull { it.asStr() }
    val max = worked.firstOrNull()?.let { load.num(it) ?: 0.0 } ?: 0.0

    SectionCard {
        CardHead(t("Muscle balance"), subtitle = if (on) t("by hard sets") else t("by sets worked")) {
            Segmented(
                options = listOf("balance", "fatigue"),
                labels = listOf(t("Balance"), t("Fatigue")),
                value = view,
                onChange = { view = it; sel = null },
                modifier = Modifier.fillMaxWidth(0.55f),
            )
        }
        if (view == "balance") {
            RangeStrip(
                values = listOf("7", "30", "90", "365", "0"),
                labels = listOf(t("Week"), "1M", "3M", "1Y", t("All")),
                value = win.toString(),
                onChange = { win = it.toIntOrNull() ?: 7; sel = null },
                modifier = Modifier.padding(top = 10.dp),
            )
            if (rated) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        text = if (on) t("Hard sets") else t("All sets"),
                        onClick = { hard = !hard; sel = null },
                        variant = ButtonVariant.GHOST,
                        size = ButtonSize.SM,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (inWin.isEmpty()) {
                Text(
                    text = t("No workouts in this period yet."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // The web's card is the map first and the numbers under it: the picture answers "what
                // did I neglect" at a glance, the rows say by how much.
                BodyMap(
                    load = load,
                    body = S.str("body") ?: "male",
                    selected = sel,
                    onMuscle = { picked -> sel = if (sel == picked) null else picked },
                )
                BodyMapLegend()
                // A tapped muscle takes the rows' place — it is the one reading that was asked for.
                val picked = sel
                if (picked != null) {
                    PickedRow(
                        name = t(MUSCLE_NAME[picked] ?: picked),
                        reading = t("{0} sets", fmtNum(Math.round((load.num(picked) ?: 0.0) * 10) / 10.0)),
                    )
                } else {
                    worked.take(4).forEach { slug ->
                        BarRow(
                            label = t(MUSCLE_NAME[slug] ?: slug),
                            value = load.num(slug) ?: 0.0,
                            max = max,
                            trailing = t("{0} sets", fmtNum(Math.round((load.num(slug) ?: 0.0) * 10) / 10.0)),
                            accent = on,
                        )
                    }
                }
                if (missed.isNotEmpty()) {
                    Overline(
                        if (on) t("No hard sets in this period") else t("Not trained in this period"),
                        Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    Text(
                        text = missed.joinToString(" · ") { t(MUSCLE_NAME[it] ?: it) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (worked.isNotEmpty()) {
                    Text(
                        text = if (on) {
                            t("Every muscle group got at least one hard set in this period.")
                        } else {
                            t("Every muscle group got some work in this period.")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        } else {
            val bodyweightKg = lastBW(S)?.asObj()?.num("w")?.takeIf { it > 0.0 }
            val fatigue = fatigueOf(S["workouts"], now.toLong(), bodyweightKg, profile.settings.unit)
            val levels = levelsOf(JsonObject(fatigue.mapValues { JsonPrimitive(it.value) }), FATIGUE_LEVELS)
            val fatigueMax = MUSCLES.maxOfOrNull { fatigue[it] ?: 0.0 } ?: 0.0
            Text(
                text = t("Fatigue"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (fatigueMax <= 0.0) {
                Text(
                    text = t("No workouts in this period yet."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                BodyMap(
                    load = JsonObject(fatigue.mapValues { JsonPrimitive(it.value) }),
                    body = S.str("body") ?: "male",
                    thresholds = FATIGUE_LEVELS,
                    selected = sel,
                    onMuscle = { picked -> sel = if (sel == picked) null else picked },
                )
                FatigueLegend()
                val picked = sel
                if (picked != null) {
                    PickedRow(
                        name = t(MUSCLE_NAME[picked] ?: picked),
                        reading = fatigueLabel(fatigue[picked] ?: 0.0),
                    )
                } else {
                    MUSCLES
                        .map { it to (fatigue[it] ?: 0.0) }
                        .sortedByDescending { it.second }
                        .take(8)
                        .forEach { (slug, value) ->
                            BarRow(
                                label = t(MUSCLE_NAME[slug] ?: slug),
                                value = value,
                                max = 1.0,
                                trailing = fatigueLabel(value),
                                level = (levels.num(slug) ?: 0.0).toInt(),
                            )
                        }
                }
            }
            Text(
                text = t("Fatigue shows how recently each muscle was trained. High means rest."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/**
 * The range picker: 1M / 3M / 1Y / All, and the balance card's Week alongside them.
 *
 * M3's segmented control gives each option an equal share of the width and then clips its label, so
 * the web's own control — .seg-range, whose buttons shrink to 11.5px at four or five options — is
 * built here instead: equal cells, the smaller type role, and the selected one tinted.
 */
@Composable
private fun RangeStrip(
    values: List<String>,
    labels: List<String>,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        values.forEachIndexed { index, option ->
            val selected = option == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(FullShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    )
                    .clickable { onChange(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labels.getOrElse(index) { option },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** The three bands the web's legend names, from one fatigue value. */
@Composable
internal fun fatigueLabel(value: Double): String = when (fatigueStateOf(value)) {
    "ready" -> t("Ready")
    "recovering" -> t("Recovering")
    else -> t("Fatigued")
}

/** One ranked bar: the name, a track, and the reading. */
@Composable
internal fun BarRow(
    label: String,
    value: Double,
    max: Double,
    trailing: String,
    accent: Boolean = false,
    level: Int = -1,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.4f),
        )
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(if (max > 0) (value / max).toFloat().coerceIn(0f, 1f) else 0f)
                    .height(8.dp)
                    .background(
                        if (accent) {
                            MaterialTheme.extraColors.yellow
                        } else if (level >= 0) {
                            fatigueLevelColor(level)
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    ),
            )
        }
        Text(
            text = trailing,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * What a tapped muscle gets, where the ranked bars were: the web's own row, a bold name and the one
 * reading that was asked for. It sits under the map, which is where the finger already is.
 */
@Composable
private fun PickedRow(name: String, reading: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = reading,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * How hard the training was — the half of the picture a volume chart cannot show. Every number
 * carries how much of the training it speaks for: rating is optional, so a partly rated history is
 * the normal case and an average without its denominator would speak for sets nobody rated.
 */
@Composable
private fun EffortCard(profile: Profile) {
    val S = profile.raw
    var win by remember { mutableStateOf(90) }
    val kind = displayScale(S)
    val hd = scaleName(kind)
    val sum = effortSummary(S, win)
    val weeks = effortWeeks(S, win)
    val hist = effortHistogram(S, win)
    val maxBin = maxOf(1, hist.maxOfOrNull { it.n } ?: 1)
    val points = weeks.map { w ->
        ChartPoint(t = w.t, y = toScale(kind, w.rir) ?: 0.0, note = t("{0} sets", w.sets))
    }

    SectionCard {
        CardHead(t("Effort"), subtitle = t("how close to failure"))
        RangeStrip(
            values = listOf("30", "90", "365", "0"),
            labels = listOf("1M", "3M", "1Y", t("All")),
            value = win.toString(),
            onChange = { win = it.toIntOrNull() ?: 90 },
        )
        if (sum.rated == 0) {
            Text(
                text = t("No rated sets in this period."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = sum.avg?.let { fmtNum(toScale(kind, it) ?: 0.0) + " " + hd } ?: "—",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.W600),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = t("average effort"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = sum.hardPct?.let { Math.round(it * 100).toString() + "%" } ?: "—",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.W600),
                        color = MaterialTheme.extraColors.yellow,
                    )
                    Text(
                        text = t("at {0} {1} or harder", hd, fmtNum(toScale(kind, HARD_RIR) ?: 0.0)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = t("{0} of {1} finished sets rated", sum.rated, sum.done),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (effortOf(S) == "none") {
                Text(
                    text = t("Effort per set is switched off — turn it on in Settings to keep rating."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extraColors.yellow,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (points.size > 1) {
                Overline(t("Week by week"), Modifier.padding(top = 12.dp, bottom = 6.dp))
                LineChart(
                    points = points,
                    height = 140.dp,
                    unit = hd,
                    color = MaterialTheme.extraColors.yellow,
                    invert = kind == "rir",
                )
            }
            Overline(t("Where the sets land"), Modifier.padding(top = 12.dp, bottom = 6.dp))
            hist.forEach { bin ->
                BarRow(
                    label = hd + " " + binLabel(kind, bin),
                    value = bin.n.toDouble(),
                    max = maxBin.toDouble(),
                    trailing = if (bin.n > 0) bin.n.toString() + " · " + Math.round(bin.pct * 100) + "%" else "—",
                    accent = bin.rir <= HARD_RIR,
                )
            }
            Text(
                text = t("Most working sets belong close to failure without living there — half at the floor and half at the top average out to a healthy-looking middle."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Bins run hardest-first in both scales: RIR 0 and RPE 10 are the same set. */
private fun binLabel(kind: String, bin: EffortBin): String = if (kind == "rpe") {
    if (bin.tail) "≤ 6" else (10 - bin.rir).toString()
} else {
    if (bin.tail) bin.rir.toString() + "+" else bin.rir.toString()
}

/** The body-weight curve, with the goal line while there is one. */
@Composable
private fun BodyWeightCard(profile: Profile, range: Int, onRange: (Int) -> Unit) {
    val unit = profile.settings.unit
    val target = profile.settings.targetW
    val now = System.currentTimeMillis()
    val points = profile.bodyweight.mapNotNull { element ->
        val w = element.asObj() ?: return@mapNotNull null
        val iso = w.str("d") ?: return@mapNotNull null
        val at = w.num("t")?.toLong()
            ?: java.time.LocalDate.parse(iso).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        if (range != 0 && at <= now - range.toLong() * 86_400_000L) return@mapNotNull null
        ChartPoint(t = at, y = w.num("w") ?: 0.0, d = iso)
    }
    SectionCard(Modifier.padding(top = 8.dp)) {
        CardHead(t("Body weight")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    text = target?.let { fmtNum(it) } ?: t("Goal"),
                    onClick = { goalSheet() },
                    variant = ButtonVariant.GHOST,
                    size = ButtonSize.SM,
                    icon = Glyph.TARGET,
                )
                Button(
                    text = t("Log"),
                    onClick = { weighInSheet() },
                    variant = ButtonVariant.GHOST,
                    size = ButtonSize.SM,
                    icon = Glyph.PLUS,
                )
            }
        }
        RangeStrip(
            values = listOf("30", "90", "365", "0"),
            labels = listOf("1M", "3M", "1Y", t("All")),
            value = range.toString(),
            onChange = { onRange(it.toIntOrNull() ?: 90) },
            modifier = Modifier.padding(bottom = 8.dp),
        )
        LineChart(points = points, height = 160.dp, unit = unit, goal = target)
    }
}

/** One exercise's own curve, and the five sessions behind it. */
@Composable
private fun ExerciseProgressCard(profile: Profile) {
    val S = profile.raw
    val readings = progressExercises(S)
    var picked by remember { mutableStateOf<String?>(null) }
    val id = picked?.takeIf { candidate -> readings.any { it.id == candidate } } ?: readings.firstOrNull()?.id
    SectionCard(Modifier.padding(top = 8.dp)) {
        CardHead(t("Exercise progress"))
        if (id == null) {
            Text(
                text = t("Finish your first workout to see progress curves here."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        val series = progressSeries(S, id)
        val kind = displayScale(S)
        val rirs = series.points.map { avgRir(it.sets) }
        val showEff = rirs.count { it != null } >= 3
        var metric by remember(id) { mutableStateOf("top") }
        val onEff = showEff && metric == "effort"
        SelectRow(
            title = t("Exercise"),
            value = id,
            options = readings.map {
                SelectOption(
                    value = it.id,
                    label = it.name + if (it.mx > 0) " — " + fmtNum(it.mx) + " " + it.unit else "",
                )
            },
            onChange = { picked = it },
            sheetTitle = t("Exercise progress"),
            modifier = Modifier.padding(bottom = 10.dp),
        )
        if (showEff) {
            Segmented(
                options = listOf("top", "effort"),
                labels = listOf(t("Top set"), t("Effort")),
                value = metric,
                onChange = { metric = it },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        val points = if (onEff) {
            series.points.mapIndexedNotNull { i, p ->
                rirs[i]?.let { ChartPoint(t = p.t, y = toScale(kind, it) ?: 0.0, d = p.d) }
            }
        } else {
            series.points.mapIndexed { i, p ->
                ChartPoint(t = p.t, y = p.y, d = p.d, m = rirs[i]?.let { 1.0 - minOf(4.0, maxOf(0.0, it)) / 4.0 })
            }
        }
        LineChart(
            points = points,
            height = if (onEff) 150.dp else 150.dp,
            unit = if (onEff) scaleName(kind) else series.unit,
            color = if (onEff) MaterialTheme.extraColors.yellow else MaterialTheme.colorScheme.tertiary,
            invert = onEff && kind == "rir",
        )
        series.points.takeLast(5).reversed().forEach { p ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = p.d?.let { fmtDate(it, long = true) } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = p.sets.mapNotNull { s -> if (s.asObj() != null) setLabel(id, s, p.target) else null }
                        .joinToString("  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            text = if (onEff) {
                t("Average effort per workout")
            } else if (series.unit == "s") {
                t("Longest hold per workout") + " · " + t("Best:") + " " + fmtNum(series.best) + " " + series.unit
            } else if (series.repsOnly) {
                t("Most reps in a set per workout") + " · " + t("Best:") + " " + fmtNum(series.best) + " " + series.unit
            } else {
                t("Best set weight per workout") + " · " + t("Best:") + " " + fmtNum(series.best) + " " + series.unit
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (!onEff && showEff) {
            Text(
                text = t(
                    "A fuller dot means less left in the tank — the same weight at a lower {0} is progress the line alone does not show.",
                    scaleName(kind),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** The meets are not training, so they get one card of their own and a door to their screen. */
@Composable
private fun CompetitionsCard(profile: Profile) {
    val meets = profile.raw.arr("competitions")
    val bests = competitionBests(meets)
    val unit = profile.settings.unit
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        CardHead(
            title = t("Competitions"),
            subtitle = if (meets.isEmpty()) null else t(if (meets.size == 1) "{0} competition" else "{0} competitions", meets.size),
        )
        if (meets.isEmpty()) {
            Text(
                text = t("No competitions yet."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Tile(
                    label = t("Best total"),
                    value = if (bests.total == null) "—" else fmtNum(bests.total),
                    suffix = if (bests.total == null) t("not done") else unit,
                    modifier = Modifier.weight(1f),
                )
            }
            Section(modifier = Modifier.padding(top = 8.dp)) {
                ListRow(
                    title = t("All competitions"),
                    icon = Glyph.MEDAL,
                    value = meets.size.toString(),
                    accessory = Accessory.CHEVRON,
                    onClick = { Nav.to(CompetitionsScreen) },
                )
            }
        }
    }
}

/** The last few sessions, each one opening its own detail sheet, and the door into all of them. */
@Composable
private fun RecentWorkouts(profile: Profile) {
    val all = profile.raw.arr("workouts").mapNotNull { it.asObj() }
    val workouts = all.reversed().take(6)
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Overline(t("Recent workouts"), Modifier.weight(1f))
            Button(
                text = t("All") + " " + all.size.toString(),
                onClick = { Nav.to(HistoryScreen) },
                variant = ButtonVariant.GHOST,
                size = ButtonSize.SM,
                trailingIcon = Glyph.CHEVRON_RIGHT,
            )
        }
        workouts.forEach { w ->
            WorkoutRow(w, profile.settings.unit) { workoutDetailSheet(w) }
        }
    }
}
