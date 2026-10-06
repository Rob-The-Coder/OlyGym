package olygym.app.lib

import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/*
 * The line chart's maths, lifted out of frontend/src/components/LineChart.jsx so the axis the chart
 * draws is a value a test can pin. The drawing is Compose (ui/chart/LineChart.kt); everything here
 * is the arithmetic behind it, unchanged from the web.
 */

/** The chart's own coordinate space. The viewBox is stretched to the container, so this is fixed. */
const val CHART_W = 340.0

/**
 * A padded y range: the data, the goal when there is one, and 12% breathing room. A flat series gets
 * a unit of room above and below so a single value still draws inside the frame.
 */
fun chartYRange(ys: List<Double>, goal: Double? = null): Pair<Double, Double> {
    if (ys.isEmpty()) return 0.0 to 1.0
    var lo = ys.min()
    var hi = ys.max()
    if (goal != null && goal.isFinite()) {
        lo = minOf(lo, goal)
        hi = maxOf(hi, goal)
    }
    if (lo == hi) {
        lo -= 1.0
        hi += 1.0
    }
    val pad = (hi - lo) * 0.12
    return (lo - pad) to (hi + pad)
}

/**
 * The gridline step: the range split three ways, rounded up to 1, 2, 2.5, 5 or 10 of a power of ten.
 * That is what makes the labels read as round numbers rather than as the range divided by three.
 */
fun chartGridStep(range: Double): Double {
    val raw = range / 3.0
    if (!raw.isFinite() || raw <= 0.0) return 1.0
    val pow = 10.0.pow(floor(log10(raw)))
    for (m in listOf(1.0, 2.0, 2.5, 5.0, 10.0)) if (raw <= m * pow) return m * pow
    return 10.0 * pow
}

/** The gridline values inside the range: the first multiple of the step above its floor, then up. */
fun chartGridValues(ymin: Double, ymax: Double, step: Double): List<Double> {
    if (step <= 0.0 || !step.isFinite()) return emptyList()
    val out = mutableListOf<Double>()
    var v = ceil(ymin / step) * step
    while (v <= ymax + 1e-9 && out.size < 40) {
        out.add(v)
        v += step
    }
    return out
}

/** The month boundaries strictly inside the span, as epoch millis. One tick per month it crosses. */
fun chartMonthTicks(t0: Long, t1: Long): List<Long> {
    val zone = ZoneId.systemDefault()
    var month = Instant.ofEpochMilli(t0).atZone(zone).toLocalDate().withDayOfMonth(1).plusMonths(1)
    val out = mutableListOf<Long>()
    while (out.size < 60) {
        val at = month.atStartOfDay(zone).toInstant().toEpochMilli()
        if (at > t1) break
        out.add(at)
        month = month.plusMonths(1)
    }
    return out
}

/**
 * Three evenly spaced ticks across the span, for a chart too short to cross a month boundary. The
 * end ticks hang inwards so their labels do not run off the frame.
 */
fun chartSpanTicks(t0: Long, t1: Long): List<Pair<Long, String>> = (0..2).map { i ->
    val at = t0 + ((t1 - t0) * i) / 2
    at to when (i) {
        0 -> "start"
        2 -> "end"
        else -> "middle"
    }
}
