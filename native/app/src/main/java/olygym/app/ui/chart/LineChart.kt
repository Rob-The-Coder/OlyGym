package olygym.app.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import olygym.app.lib.CHART_W
import olygym.app.lib.MONTHS
import olygym.app.lib.chartGridStep
import olygym.app.lib.chartGridValues
import olygym.app.lib.chartMonthTicks
import olygym.app.lib.chartSpanTicks
import olygym.app.lib.chartYRange
import olygym.app.lib.fmtNum
import olygym.app.ui.t
import olygym.app.ui.theme.extraColors
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.min

/**
 * One point of a curve: when, how much, and the two things a point can also carry — a second reading
 * on the same dot (m, bigger and more solid = more of it, used for effort on the weight curve) and a
 * note the web would put in a tooltip.
 */
data class ChartPoint(
    val t: Long,
    val y: Double,
    val d: String? = null,
    val m: Double? = null,
    val note: String? = null,
)

/**
 * The line chart — a port of frontend/src/components/LineChart.jsx. The maths is lib/ChartMath.kt
 * and is a test; this draws it: a stretched viewBox, three gridlines, month ticks, the fill under
 * the curve, the marked dots and the goal line.
 *
 * Not ported: the hover tooltip. There is no pointer to hover with, and the reading it gave (the
 * dated value under the finger) is the one a tap would give; the chart is drawn without it rather
 * than with a control that never fires.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    unit: String = "",
    color: Color = MaterialTheme.colorScheme.primary,
    axes: Boolean = true,
    goal: Double? = null,
    invert: Boolean = false,
) {
    if (points.isEmpty()) {
        Text(
            text = t("No data yet"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val grid = MaterialTheme.extraColors.hairline
    val goalColor = MaterialTheme.extraColors.yellow

    Box(modifier.fillMaxWidth().height(height)) {
        Canvas(Modifier.fillMaxSize()) {
            drawChart(points, height.toPx(), unit, color, axes, goal, invert, grid, goalColor, measurer, labelStyle)
        }
    }
}

private fun DrawScope.drawChart(
    points: List<ChartPoint>,
    h: Float,
    unit: String,
    color: Color,
    axes: Boolean,
    goal: Double?,
    invert: Boolean,
    grid: Color,
    goalColor: Color,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
) {
    val w = size.width
    // The point list is in the chart's own 340-wide space; the canvas stretches it to the container.
    val sx = w / CHART_W.toFloat()
    val sy = h / h
    val left = if (axes) 34f * sx else 8f * sx
    val right = 12f * sx
    val top = 10f
    val bottom = if (axes) 22f else 8f
    val single = points.size == 1
    val pts = if (single) listOf(points[0], points[0]) else points
    val (ymin, ymax) = chartYRange(pts.map { it.y }, goal)
    val t0 = pts.first().t
    val t1 = pts.last().t.takeIf { it != t0 } ?: (t0 + 1)
    val x = { tt: Long -> if (t1 == t0) (left + w - right) / 2f else left + (tt - t0).toFloat() / (t1 - t0).toFloat() * (w - left - right) }
    val y = { v: Double ->
        val f = ((v - ymin) / (ymax - ymin)).toFloat()
        top + (if (invert) f else 1f - f) * (h - top - bottom)
    }

    if (axes) {
        val step = chartGridStep(ymax - ymin)
        for (v in chartGridValues(ymin, ymax, step)) {
            val yy = y(v)
            drawLine(grid, Offset(left, yy), Offset(w - right, yy), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 4f)))
            val label = measurer.measure(fmtNum(v), labelStyle)
            drawText(label, topLeft = Offset(left - 5f * sx - label.size.width, yy - label.size.height / 2f))
        }
        var ticks = chartMonthTicks(t0, t1).map { it to monthLabel(it) }
        if (ticks.isEmpty() && !single) {
            ticks = chartSpanTicks(t0, t1).map { it.first to spanLabel(it.first) }
        }
        val every = maxOf(1, ceil(ticks.size / 7.0).toInt())
        ticks.forEachIndexed { i, (tt, text) ->
            if (i % every != 0) return@forEachIndexed
            val xx = x(tt)
            drawLine(grid, Offset(xx, top), Offset(xx, h - bottom), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 4f)))
            val label = measurer.measure(text, labelStyle)
            val anchor = when {
                i == 0 -> 0f
                i == ticks.size - 1 -> label.size.width.toFloat()
                else -> label.size.width / 2f
            }
            drawText(label, topLeft = Offset(xx - anchor, h - 7f - label.size.height))
        }
    }

    if (goal != null && goal.isFinite()) {
        val yy = y(goal)
        drawLine(
            goalColor,
            Offset(left, yy),
            Offset(w - right, yy),
            strokeWidth = 1.6f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 4f)),
        )
        val label = measurer.measure(fmtNum(goal), labelStyle.copy(color = goalColor))
        drawText(label, topLeft = Offset(w - right - label.size.width, yy - 5f - label.size.height))
    }

    // The curve, and the same shape closed along the baseline, filled as a gradient — the web's
    // 28%-to-nothing wash under the line.
    val line = Path()
    pts.forEachIndexed { i, p ->
        val px = x(p.t)
        val py = y(p.y)
        if (i == 0) line.moveTo(px, py) else line.lineTo(px, py)
    }
    val fill = Path().apply {
        addPath(line)
        lineTo(x(pts.last().t), h - bottom)
        lineTo(x(pts.first().t), h - bottom)
        close()
    }
    drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent), startY = top, endY = h - bottom))
    drawPath(line, color, style = Stroke(width = 2.5f * sx.coerceAtMost(1.6f) + 1f))

    if (pts.any { it.m != null }) {
        pts.forEach { p ->
            p.m?.let { m ->
                drawCircle(
                    color = color.copy(alpha = (0.3f + m.toFloat() * 0.7f).coerceIn(0f, 1f)),
                    radius = (2.4f + m.toFloat() * 3f) * sx,
                    center = Offset(x(p.t), y(p.y)),
                )
            }
        }
    }
    drawCircle(color, radius = 4f * sx, center = Offset(x(pts.last().t), y(pts.last().y)))
    if (unit.isNotEmpty() && single) {
        // A one-point curve has no shape to read, so the value is written next to it.
        val label = measurer.measure(fmtNum(pts.last().y) + " " + unit, labelStyle)
        drawText(label, topLeft = Offset(left, min(top, h - bottom - label.size.height)))
    }
}

private val ZONE: ZoneId get() = ZoneId.systemDefault()

private fun monthLabel(t: Long): String =
    t(MONTHS[Instant.ofEpochMilli(t).atZone(ZONE).monthValue - 1])

private fun spanLabel(t: Long): String {
    val d = Instant.ofEpochMilli(t).atZone(ZONE)
    return d.dayOfMonth.toString() + " " + t(MONTHS[d.monthValue - 1])
}
