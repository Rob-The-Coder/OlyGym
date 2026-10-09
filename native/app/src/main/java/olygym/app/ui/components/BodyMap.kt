package olygym.app.ui.components

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import olygym.app.data.num
import olygym.app.lib.INERT
import olygym.app.lib.MUSCLES
import olygym.app.lib.levelsOf
import olygym.app.ui.t
import olygym.app.ui.theme.fatigueLevelColor
import olygym.app.ui.theme.muscleLevelColor
import olygym.app.ui.theme.muscleSilhouette

/*
 * The body silhouette, front and back, each muscle shaded by how hard it was worked — a port of
 * components/BodyMap.jsx.
 *
 * The geometry is the web's own, generated into assets/body-paths.json by native/tools/assets.mjs:
 * four views (male/female x front/back), each a viewBox and one path list per body part. It is ~93 KB
 * and only the balance and fatigue cards draw it, which is why the web lazy-imports it; here it is an
 * asset parsed once per process, off the main thread, and the two views are laid out side by side.
 *
 * The parts that are not muscles (head, hair, neck, hands, feet, knees, ankles) are drawn as the
 * silhouette and never shaded, and every outline is stroked in the page colour so two neighbouring
 * muscles read as two shapes.
 */

@Serializable
private data class BodyView(val vb: String, val p: Map<String, List<String>>)

@Serializable
private data class BodyGeometryData(val front: BodyView, val back: BodyView)

/** One view, ready to draw: the viewBox split into numbers, and a Path per body part. */
private class BodyViewPaths(
    val minX: Float,
    val minY: Float,
    val width: Float,
    val height: Float,
    val paths: Map<String, List<Path>>,
)

private class BodyPaths(val front: BodyViewPaths, val back: BodyViewPaths)

/**
 * Read and parse once. The class is module-level and the map is keyed by body, so opening Stats twice
 * pays for the geometry once.
 */
private object Bodies {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, BodyPaths>()

    fun get(context: Context, body: String): BodyPaths? = cache[body] ?: runCatching {
        val text = context.assets.open("body-paths.json").bufferedReader().use { it.readText() }
        val all = json.decodeFromString<Map<String, BodyGeometryData>>(text)
        val geometry = all[body] ?: all["male"] ?: return@runCatching null
        BodyPaths(parse(geometry.front), parse(geometry.back)).also { cache[body] = it }
    }.getOrNull()

    private fun parse(view: BodyView): BodyViewPaths {
        val n = view.vb.trim().split(' ').mapNotNull { it.toFloatOrNull() }
        return BodyViewPaths(
            minX = n.getOrElse(0) { 0f },
            minY = n.getOrElse(1) { 0f },
            width = n.getOrElse(2) { 1f },
            height = n.getOrElse(3) { 1f },
            paths = view.p.mapValues { (_, list) -> list.map { PathParser().parsePathString(it).toPath() } },
        )
    }
}

/**
 * The two views. `load` is effective sets per muscle (or the fatigue reading), `thresholds` switches
 * from the balance ramp to the fixed fatigue bands — the same shape levelsOf() takes.
 *
 * With `onMuscle` the figures are tappable: the muscle under the finger is named back to the caller,
 * which is what the web's `className="tappable"` map does on the balance, fatigue and By-muscle
 * screens. `selected` draws that muscle's outline in the label colour, the web's `.bm-m.sel`.
 */
@Composable
fun BodyMap(
    load: JsonElement?,
    body: String,
    modifier: Modifier = Modifier,
    thresholds: JsonArray? = null,
    selected: String? = null,
    onMuscle: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val parsed by produceState<BodyPaths?>(null, body) {
        value = withContext(Dispatchers.Default) { Bodies.get(context, body) }
    }
    val levels = levelsOf(load, thresholds)
    val fatigue = thresholds != null
    val paths = parsed ?: return

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        BodyPanel(paths.front, levels, fatigue, selected, onMuscle, Modifier.weight(1f))
        BodyPanel(paths.back, levels, fatigue, selected, onMuscle, Modifier.weight(1f))
    }
}

@Composable
private fun BodyPanel(
    view: BodyViewPaths,
    levels: JsonObject,
    fatigue: Boolean,
    selected: String?,
    onMuscle: ((String) -> Unit)?,
    modifier: Modifier,
) {
    val silhouette = muscleSilhouette()
    val outline = MaterialTheme.colorScheme.surface
    val pickedOutline = MaterialTheme.colorScheme.onSurface
    val fills = HashMap<String, androidx.compose.ui.graphics.Color>()
    view.paths.keys.forEach { slug ->
        fills[slug] = if (slug in INERT) {
            silhouette
        } else {
            val level = (levels.num(slug) ?: 0.0).toInt()
            if (fatigue) fatigueLevelColor(level) else muscleLevelColor(level)
        }
    }

    Canvas(
        modifier
            .aspectRatio(view.width / view.height)
            .then(
                if (onMuscle == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(onMuscle) {
                        detectTapGestures { at ->
                            // The box has the viewBox's own ratio, so this is the draw transform read
                            // backwards: screen to view units, then ask which muscle is under it.
                            val s = size.width / view.width
                            muscleAt(view, at.x / s + view.minX, at.y / s + view.minY)?.let(onMuscle)
                        }
                    }
                },
            ),
    ) {
        // The viewBox is one coordinate space the two views crop out of, so each canvas maps its own
        // rectangle onto its own box: subtract the origin, then scale by width.
        val s = size.width / view.width
        withTransform({
            scale(s, s, pivot = Offset.Zero)
            translate(-view.minX, -view.minY)
        }) {
            val line = Stroke(width = 2.5f, join = StrokeJoin.Round)
            val picked = Stroke(width = 7f, join = StrokeJoin.Round)
            // The web's order, not the asset's: the silhouette first and then the muscles head to toe.
            // Where two flat shapes overlap, the one drawn later is the one you see, so this order is
            // part of the picture rather than an accident of how the JSON happens to be keyed.
            val order = INERT + MUSCLES
            order.forEach { slug ->
                val color = fills[slug] ?: return@forEach
                val isPicked = slug == selected
                view.paths[slug]?.forEach { path ->
                    drawPath(path, color)
                    drawPath(path, if (isPicked) pickedOutline else outline, style = if (isPicked) picked else line)
                }
            }
        }
    }
}

/**
 * Which muscle is under a point, in the view's own units. The draw order is the other way round: the
 * shape painted last is the one you can see, so it is the first one a tap should find.
 */
private fun muscleAt(view: BodyViewPaths, x: Float, y: Float): String? {
    val at = Offset(x, y)
    // The silhouette is not a target in the web either: only the muscles answer a tap.
    return MUSCLES.asReversed().firstOrNull { slug ->
        view.paths[slug]?.any { it.getBounds().contains(at) && pathHolds(it, x, y) } == true
    }
}

/**
 * The fill the hit test rasterises with: white, no antialiasing, the platform's default winding.
 */
private val HIT_FILL = android.graphics.Paint().apply {
    color = android.graphics.Color.WHITE
    isAntiAlias = false
    style = android.graphics.Paint.Style.FILL
}

/**
 * Is the point inside the shape? A Compose Path cannot be asked, and the obvious tool — the platform's
 * Region.setPath — came back empty for these outlines, so this rasterises instead: the path is drawn
 * into one pixel at the point's offset, and that pixel is the answer. It is the same rule the picture
 * is drawn by, which is the one thing a hit test must not disagree with. Only paths whose bounds
 * already hold the point are ever drawn.
 */
private fun pathHolds(path: Path, x: Float, y: Float): Boolean {
    val pixel = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(pixel)
    canvas.translate(-x, -y)
    canvas.drawPath(path.asAndroidPath(), HIT_FILL)
    return pixel.getPixel(0, 0) != 0
}

/** The five-step ramp, named the way the heatmap names its own: less work on the left. */
@Composable
fun BodyMapLegend(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = t("Less"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (level in 0..4) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(muscleLevelColor(level)),
            )
        }
        Text(
            text = t("More"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The fatigue map's three bands, in the order the web names them. */
@Composable
fun FatigueLegend(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(t("Fatigued") to 4, t("Recovering") to 2, t("Ready") to 0).forEach { (label, level) ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(fatigueLevelColor(level)),
            )
        }
    }
}
