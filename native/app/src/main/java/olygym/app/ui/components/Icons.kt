package olygym.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The app's own glyph set, transcribed from frontend/src/components/Icon.jsx.
 *
 * Transcribed, not redrawn: every shape below is the same SVG path data the web app draws, on the
 * same 24x24 grid with the same 1.7 stroke and the same round caps and joins, so a screen ported
 * later gets the glyph it already had and a new glyph is a copy and paste rather than an
 * interpretation. The web file's conventions hold here too: strokes only, except where a shape
 * reads better solid, and geometry snapped so it lands on pixel edges at 24px.
 *
 * The five hand-drawn glyphs this file used to carry (minus, plus, check and the two chevrons) are
 * in the same table now, so there is one mechanism instead of two.
 */
enum class Glyph {
    // navigation
    HOUSE, CALENDAR, CHART, MAGNIFIER, GEAR,
    // training
    DUMBBELL, BARBELL, SCALE, FLAME, TIMER, CLOCK,
    // status
    TROPHY, MEDAL, TARGET, SPARKLES, LIGHTBULB, STAR, STAR_FILLED,
    // actions
    PLUS, MINUS, CHECK, CHECK_CIRCLE, XMARK, PENCIL, TRASH, LINK, DOWNLOAD, PLAY, RESET,
    CHEVRON_RIGHT, CHEVRON_LEFT, CHEVRON_DOWN, CHEVRON_UP, ARROW_UP, ARROW_DOWN,
    MINIMIZE, LIST, CLIPBOARD, FLAG, SHUFFLE, INFO, MORE, HISTORY, WARNING, CHART_LINE,
    // objects
    MOON, SUN, DOT,
}

/** One drawable piece of a glyph, in the 24x24 space the web app draws in. */
sealed interface IconPart {
    /** A stroked path: the web's default icon element, stroked in currentColor. */
    data class StrokePath(val d: String) : IconPart

    /** A filled path: the web's fill="currentColor" stroke="none". */
    data class SolidPath(val d: String) : IconPart

    /** A circle element, stroked unless it is one of the solid dots. */
    data class Circle(val cx: Double, val cy: Double, val r: Double, val solid: Boolean = false) : IconPart

    /** A rect element, with the corner radius the web gives it. */
    data class RoundedRect(val x: Double, val y: Double, val w: Double, val h: Double, val rx: Double = 0.0) : IconPart
}

/** The house stroke width (--icon-stroke in index.css). */
const val ICON_STROKE = 1.7f

@Composable
fun GlyphIcon(
    glyph: Glyph,
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = LocalContentColor.current,
    stroke: Float = ICON_STROKE,
) {
    val parts = GLYPHS[glyph] ?: return
    Canvas(modifier) {
        // The paths are in the web app's 24-unit space; scale the canvas to whatever size this
        // glyph was asked for, so the stroke scales with it exactly as an SVG with a viewBox does.
        val s = size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) {
            val line = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            parts.forEach { part ->
                when (part) {
                    is IconPart.StrokePath -> drawPath(part.d.path(), tint, style = line)
                    is IconPart.SolidPath -> drawPath(part.d.path(), tint)
                    is IconPart.Circle -> {
                        val center = Offset(part.cx.toFloat(), part.cy.toFloat())
                        if (part.solid) drawCircle(tint, part.r.toFloat(), center)
                        else drawCircle(tint, part.r.toFloat(), center, style = line)
                    }
                    is IconPart.RoundedRect -> drawRoundRect(
                        color = tint,
                        topLeft = Offset(part.x.toFloat(), part.y.toFloat()),
                        size = Size(part.w.toFloat(), part.h.toFloat()),
                        cornerRadius = CornerRadius(part.rx.toFloat()),
                        style = line,
                    )
                }
            }
        }
    }
}

/**
 * ponytail: parsed per draw. A screen shows a handful of glyphs and a parse is microseconds; cache
 * them in a map if a trace ever says otherwise.
 */
private fun String.path(): Path = PathParser().parsePathString(this).toPath()

private fun stroke(d: String) = IconPart.StrokePath(d)
private fun circle(cx: Double, cy: Double, r: Double) = IconPart.Circle(cx, cy, r)
private fun dot(cx: Double, cy: Double, r: Double) = IconPart.Circle(cx, cy, r, solid = true)

/** Every glyph the ported screens use, exactly as Icon.jsx draws it. */
val GLYPHS: Map<Glyph, List<IconPart>> = mapOf(
    Glyph.HOUSE to listOf(stroke("M3.5 10.7 12 3.8l8.5 6.9M5.9 9.4V19a1.4 1.4 0 0 0 1.4 1.4h9.4A1.4 1.4 0 0 0 18.1 19V9.4")),
    Glyph.CALENDAR to listOf(
        IconPart.RoundedRect(3.4, 5.2, 17.2, 15.4, 3.2),
        stroke("M8.2 3.4v3.4M15.8 3.4v3.4M3.4 10.2h17.2"),
    ),
    Glyph.CHART to listOf(stroke("M4.5 20.2V13M9.5 20.2V6.4M14.5 20.2v-5.1M19.5 20.2V9.6")),
    Glyph.MAGNIFIER to listOf(circle(11.0, 11.0, 7.0), stroke("m20.5 20.5-4.4-4.4")),
    Glyph.GEAR to listOf(
        stroke("M20.48 10.59 20.48 13.41 18.58 13.72 17.87 15.43 19 17 17 19 15.43 17.87 13.72 18.58 13.41 20.48 10.59 20.48 10.28 18.58 8.57 17.87 7 19 5 17 6.13 15.43 5.42 13.72 3.52 13.41 3.52 10.59 5.42 10.28 6.13 8.57 5 7 7 5 8.57 6.13 10.28 5.42 10.59 3.52 13.41 3.52 13.72 5.42 15.43 6.13 17 5 19 7 17.87 8.57 18.58 10.28Z"),
        circle(12.0, 12.0, 3.1),
    ),

    Glyph.DUMBBELL to listOf(
        IconPart.RoundedRect(5.9, 7.9, 3.2, 8.2, 1.3),
        IconPart.RoundedRect(14.9, 7.9, 3.2, 8.2, 1.3),
        stroke("M9.1 12h5.8M3.6 9.9v4.2M20.4 9.9v4.2"),
    ),
    Glyph.BARBELL to listOf(stroke("M2.9 12h18.2M7.4 7.4v9.2M9.8 9.3v5.4M16.6 7.4v9.2M14.2 9.3v5.4")),
    Glyph.SCALE to listOf(
        IconPart.RoundedRect(3.4, 4.4, 17.2, 16.2, 3.4),
        stroke("M8.3 9.2a3.9 3.9 0 0 1 7.4 0"),
        stroke("M12 9.2v2.5M8.9 16.2h6.2"),
    ),
    Glyph.FLAME to listOf(stroke("M12 20.4c3.2 0 5.4-2.1 5.4-5.1 0-3.9-3.4-5.6-2.6-9.8-2.5.8-4 2.9-4 5.1 0 1-.5 1.6-1.2 1.6-.8 0-1.2-.7-1.2-1.8-1.1 1.2-1.8 2.9-1.8 4.9 0 3 2.2 5.1 5.4 5.1Z")),
    Glyph.TIMER to listOf(circle(12.0, 13.4, 7.2), stroke("M12 9.6v3.8h2.8M9.6 3.4h4.8")),
    Glyph.CLOCK to listOf(circle(12.0, 12.0, 8.2), stroke("M12 7.4V12l3.1 1.9")),

    Glyph.TROPHY to listOf(
        stroke("M7.6 4h8.8v4.6a4.4 4.4 0 0 1-8.8 0Z"),
        stroke("M7.6 5.6H4.9v1.5a3 3 0 0 0 2.9 3M16.4 5.6h2.7v1.5a3 3 0 0 1-2.9 3M12 13v3.4M8.6 20.4h6.8l-.7-4H9.3Z"),
    ),
    Glyph.MEDAL to listOf(
        circle(12.0, 14.8, 5.2),
        circle(12.0, 14.8, 1.9),
        stroke("M9.1 9.9 6.4 3.6M14.9 9.9l2.7-6.3"),
    ),
    Glyph.TARGET to listOf(circle(12.0, 12.0, 8.2), circle(12.0, 12.0, 4.6), circle(12.0, 12.0, 1.1)),
    Glyph.SPARKLES to listOf(
        stroke("m8.4 3.8 1.1 2.9 2.9 1.1-2.9 1.1-1.1 2.9-1.1-2.9L4.4 7.8l2.9-1.1Z"),
        stroke("m16.2 12.4.8 2.1 2.1.8-2.1.8-.8 2.1-.8-2.1-2.1-.8 2.1-.8Z"),
    ),
    Glyph.LIGHTBULB to listOf(stroke("M9.2 16.4a5.6 5.6 0 1 1 5.6 0v1.8H9.2Z"), stroke("M10 20.6h4")),
    Glyph.STAR to listOf(stroke("m12 3.9 2.6 5.3 5.8.8-4.2 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8-4.2-4.1 5.8-.8Z")),
    Glyph.STAR_FILLED to listOf(
        IconPart.SolidPath("m12 3.9 2.6 5.3 5.8.8-4.2 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8-4.2-4.1 5.8-.8Z")
    ),

    Glyph.PLUS to listOf(stroke("M12 5.2v13.6M5.2 12h13.6")),
    Glyph.MINUS to listOf(stroke("M5.2 12h13.6")),
    Glyph.CHECK to listOf(stroke("m4.8 12.6 4.8 4.8L19.2 6.8")),
    Glyph.CHECK_CIRCLE to listOf(circle(12.0, 12.0, 8.2), stroke("m8.2 12.2 2.7 2.7 5.1-5.4")),
    Glyph.XMARK to listOf(stroke("M6.2 6.2 17.8 17.8M17.8 6.2 6.2 17.8")),
    Glyph.PENCIL to listOf(
        stroke("M17.1 3.9a2.1 2.1 0 0 1 3 3l-9.9 9.9-4 1 1-4Z"),
        stroke("m15.1 5.9 3 3"),
    ),
    Glyph.TRASH to listOf(
        stroke("M4.8 6.6h14.4M9.4 6.6V4.8a1.2 1.2 0 0 1 1.2-1.2h2.8a1.2 1.2 0 0 1 1.2 1.2v1.8"),
        stroke("M6.6 6.6 7.4 19a1.6 1.6 0 0 0 1.6 1.4h6a1.6 1.6 0 0 0 1.6-1.4l.8-12.4"),
        stroke("M10.4 10.2v6.4M13.6 10.2v6.4"),
    ),
    Glyph.LINK to listOf(
        stroke("M10.2 13.8a3.6 3.6 0 0 0 5.4.4l2.6-2.6a3.6 3.6 0 0 0-5.1-5.1l-1.5 1.5"),
        stroke("M13.8 10.2a3.6 3.6 0 0 0-5.4-.4l-2.6 2.6a3.6 3.6 0 0 0 5.1 5.1l1.5-1.5"),
    ),
    Glyph.DOWNLOAD to listOf(stroke("M12 3.8v11.4M7.6 11.2 12 15.6l4.4-4.4M4.6 19.4h14.8")),
    Glyph.PLAY to listOf(stroke("M8.4 5.6 18 12l-9.6 6.4Z")),
    Glyph.RESET to listOf(stroke("M4.4 12a7.6 7.6 0 1 0 2.3-5.4"), stroke("M4 4.4v4.4h4.4")),
    Glyph.CHEVRON_RIGHT to listOf(stroke("m9.6 5.6 6.6 6.4-6.6 6.4")),
    Glyph.CHEVRON_LEFT to listOf(stroke("m14.4 5.6-6.6 6.4 6.6 6.4")),
    Glyph.CHEVRON_DOWN to listOf(stroke("m5.6 9.4 6.4 6.2 6.4-6.2")),
    Glyph.CHEVRON_UP to listOf(stroke("m5.6 14.6 6.4-6.2 6.4 6.2")),
    Glyph.ARROW_UP to listOf(stroke("M12 19.6V4.4M6.2 10.6 12 4.4l5.8 6.2")),
    Glyph.ARROW_DOWN to listOf(stroke("M12 4.4v15.2M6.2 13.4 12 19.6l5.8-6.2")),
    Glyph.MINIMIZE to listOf(stroke("M19.6 9.6h-5.2V4.4M4.4 14.4h5.2v5.2M14.4 9.6l5.2-5.2M9.6 14.4l-5.2 5.2")),
    Glyph.LIST to listOf(stroke("M8.4 6.6h11.2M8.4 12h11.2M8.4 17.4h11.2M4.6 6.6h.01M4.6 12h.01M4.6 17.4h.01")),
    Glyph.CLIPBOARD to listOf(
        IconPart.RoundedRect(5.4, 4.8, 13.2, 15.8, 2.6),
        stroke("M9 4.8a1.6 1.6 0 0 1 1.6-1.6h2.8A1.6 1.6 0 0 1 15 4.8v1.4H9Z"),
        stroke("M9.2 11.6h5.6M9.2 15.2h4"),
    ),
    Glyph.FLAG to listOf(
        stroke("M6 20.4V4.2"),
        stroke("M6.4 5.2h13v9.2h-13"),
        stroke("M12.9 5.2v9.2M6.4 9.8h13"),
    ),
    Glyph.SHUFFLE to listOf(
        stroke("M3.6 7.2h2.9c1.6 0 2.8.9 3.8 2.4l3 4.8c1 1.5 2.2 2.4 3.8 2.4h2.9M3.6 16.8h2.9c1.6 0 2.8-.9 3.8-2.4l.7-1.1M15.6 9.9l.7-1.1c1-1.5 2.2-2.4 3.8-2.4h1.9"),
        stroke("m17.9 4.3 2.8 2.1-2.8 2.1M17.9 14.7l2.8 2.1-2.8 2.1"),
    ),
    Glyph.INFO to listOf(circle(12.0, 12.0, 8.2), stroke("M12 11v5.4"), dot(12.0, 7.9, 0.9)),
    Glyph.MORE to listOf(dot(5.5, 12.0, 1.6), dot(12.0, 12.0, 1.6), dot(18.5, 12.0, 1.6)),
    Glyph.HISTORY to listOf(
        stroke("M4.5 12.2a7.6 7.6 0 1 0 2.5-5.6"),
        stroke("M4.1 4.4v4.3h4.3"),
        stroke("M12 8.3v4.2l3.1 1.9"),
    ),
    Glyph.WARNING to listOf(stroke("M12 3.4 21.2 19.4H2.8Z"), stroke("M12 9.6v4.4"), dot(12.0, 16.6, 0.9)),

    Glyph.CHART_LINE to listOf(stroke("M3.6 20.2V4.4M3.6 20.2h16.8M6.4 16.4l3.9-4.8 3.1 2.7 5.2-6.6")),
    Glyph.MOON to listOf(stroke("M19.4 14.2A7.8 7.8 0 0 1 9.8 4.6a8.2 8.2 0 1 0 9.6 9.6Z")),
    Glyph.SUN to listOf(
        circle(12.0, 12.0, 4.4),
        stroke("M12 3.6v2M12 18.4v2M20.4 12h-2M5.6 12h-2M17.94 6.06l-1.42 1.42M7.48 16.52l-1.42 1.42M17.94 17.94l-1.42-1.42M7.48 7.48 6.06 6.06"),
    ),
    Glyph.DOT to listOf(dot(12.0, 12.0, 4.2)),
)
