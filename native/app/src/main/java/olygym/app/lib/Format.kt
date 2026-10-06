package olygym.app.lib

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Formatting and date helpers — a port of frontend/src/lib/format.js. Dates go through noon-local
 * arithmetic in the browser to keep DST from shifting a day; java.time has no such problem, so
 * LocalDate is used directly and the observable behaviour is the same.
 */

/** getDay() indices: Sunday is 0, Monday is 1 — the same convention as the stored state. */
const val SUNDAY = 0
const val MONDAY = 1

val DAYN = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
val DAYS = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
val MONTHS_LONG = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/** ISO yyyy-MM-dd, which LocalDate.toString() already is. */
fun isoOf(date: LocalDate): String = date.toString()

fun todayISO(): String = isoOf(LocalDate.now())

fun fmtDate(iso: String, long: Boolean, withYear: Boolean = false): String {
    val pattern = when {
        long && withYear -> "EEE d MMM y"
        long -> "EEE d MMM"
        withYear -> "d MMM y"
        else -> "d MMM"
    }
    return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern(pattern, I18nCore.dateLocale()))
}

/**
 * The full date a screen can afford: weekday, day, month. Home's title and the weigh-in sheet.
 */
fun fmtLongDate(iso: String): String =
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEEE d MMMM", I18nCore.dateLocale()))

fun fmtDur(ms: Long): String {
    val m = ms / 60000
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "$m min"
}

/** Imported history has no clock — an unknown duration is left out rather than shown as "0 min". */
fun durPart(ms: Long): List<String> = if (ms >= 60000) listOf(fmtDur(ms)) else emptyList()

private val CAP_WORDS = Regex("(^|[\\s(\\-/])(\\p{Ll})")

/**
 * Exercise names are stored lower-case and shown capitalised. Text with no element of its own (a
 * label built in code) capitalises here instead.
 */
fun capWords(s: String?): String =
    CAP_WORDS.replace(s ?: "") { m -> m.groupValues[1] + m.groupValues[2].uppercase() }

/*
 * How many decimals a weight is shown with. One is enough for plate-loadable numbers, but a
 * per-side figure from kg plates lands on .25 and .75, and microplate work needs two. The setting
 * is module-level rather than passed through every call site, exactly as in format.js. Display
 * only: nothing rounds differently in storage.
 */
private var decimals = 1

fun setWeightDecimals(n: Int) {
    decimals = if (n == 2) 2 else 1
}

fun weightDecimals(): Int = decimals

fun fmtNum(n: Double): String {
    val p = if (decimals == 2) 100.0 else 10.0
    val rounded = (n * p).roundToLong() / p
    val nf = NumberFormat.getNumberInstance(I18nCore.dateLocale())
    nf.minimumFractionDigits = 0
    nf.maximumFractionDigits = decimals
    return nf.format(rounded)
}

/** Volume stays in the profile's unit throughout — no "t" shorthand, which pounds made a lie. */
fun fmtVol(v: Double, unit: String): String = fmtNum(v) + " " + unit

/** Plural forms are not automatic when the English string is the key. */
fun exCount(n: Int): String = I18nCore.t(if (n == 1) "{0} exercise" else "{0} exercises", n)

fun routineCount(n: Int): String = I18nCore.t(if (n == 1) "{0} routine" else "{0} routines", n)

/* ---------------------------------------------------------------- week start --
   Where a week begins is a local convention: most of Europe starts on Monday, the
   Americas on Sunday. The app used to assume Monday everywhere. It is now S.weekStart — a
   getDay() index — and every place that needs it asks these helpers.
 */

/** The profile's first weekday, defaulting to Monday for every state written before this. */
fun weekStartOf(weekStart: Int?): Int = if (weekStart == SUNDAY) SUNDAY else MONDAY

/** getDay() indices in display order — [1..6,0] for a Monday start, [0..6] for a Sunday one. */
fun weekOrder(ws: Int = MONDAY): List<Int> = (0 until 7).map { (ws + it) % 7 }

/** How many days a weekday sits past the start of its week. 0..6, so it doubles as a column. */
fun weekDayOffset(day: Int, ws: Int = MONDAY): Int = (day - ws + 7) % 7

/** The date that starts the week the ISO date falls in. */
fun startOfWeek(iso: String, ws: Int = MONDAY): LocalDate {
    val d = LocalDate.parse(iso)
    return d.minusDays(weekDayOffset(jsDow(d), ws).toLong())
}

/**
 * A key that is equal for two dates in the same week: the ISO date of the week's first day. Only
 * ever compared or used as a map key, so it does not need to be an ISO week number — which would
 * be Monday-first, with no Sunday-first equivalent to fall back to.
 */
fun weekKey(iso: String, ws: Int = MONDAY): String = isoOf(startOfWeek(iso, ws))

/** getDay(): Sunday 0 … Saturday 6, where java.time says Monday 1 … Sunday 7. */
private fun jsDow(d: LocalDate): Int = d.dayOfWeek.value % 7

fun localTZ(): String = TimeZone.getDefault()?.id ?: "UTC"

private const val ALPHA36 = "0123456789abcdefghijklmnopqrstuvwxyz"

fun uid(): String =
    System.currentTimeMillis().toString(36) +
        (0 until 5).map { ALPHA36[Random.nextInt(ALPHA36.length)] }.joinToString("")

/*
 * The accent picker: eight M3 seeds — the tone-40 value of each hue family — not eight ad-hoc
 * colours. Each seed expands into a full scheme in frontend/src/m3.tokens.css and in
 * ui/theme/Scheme.kt, both GENERATED. These hexes are only the swatch and the validity map; never
 * paint with them.
 */
val ACCENTS = linkedMapOf(
    "lime" to "#146C2E", "sky" to "#0B57D0", "orange" to "#8F4C00", "violet" to "#6750A4",
    "pink" to "#984061", "red" to "#B3261E", "teal" to "#006A6A", "gold" to "#7A5900",
)

/** The default accent is M3's baseline purple. An unknown stored accent falls back here. */
const val DEFAULT_ACCENT = "violet"
