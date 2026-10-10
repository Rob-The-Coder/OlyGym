package olygym.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.NavigatorDisposeBehavior
import olygym.app.OlyGymApp
import olygym.app.lib.effectiveDay
import olygym.app.lib.todayISO
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonSize
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.GlyphIcon
import olygym.app.ui.components.WaveProgress
import olygym.app.ui.home.HomeScreen
import olygym.app.ui.library.LibraryScreen
import olygym.app.ui.plan.PlanScreen
import olygym.app.ui.stats.StatsScreen
import olygym.app.ui.sheet.SheetHost
import olygym.app.ui.theme.FabShape
import olygym.app.ui.theme.FullShape
import olygym.app.ui.theme.Motion
import olygym.app.ui.theme.fastSpatialSpec
import olygym.app.ui.theme.reduceMotion
import olygym.app.ui.theme.spatialSpec
import olygym.app.ui.workout.WorkoutScreen
import olygym.app.ui.workout.startFlow

/**
 * The one Navigator and the shell around it: the bottom bar, the rest bar, the toast and the sheet
 * stack. Voyager is the navigation library (see the Phase 0 decisions in docs/PORT-TO-KOTLIN.md);
 * this is the single place its stack is created.
 *
 * The tab bar lives here rather than inside the tabs, because the session screen is pushed over the
 * tab host and the bar has to stay: on the web it is always on screen, and its centre button is how
 * you get back to a running workout.
 *
 * disposeBehavior keeps a screen's own state while it is off the top of the stack, and keeps nested
 * navigators alive, so coming back from a pushed screen does not rebuild the one underneath.
 */
@Composable
fun AppNavigator() {
    val ui = OlyGymApp.ui
    var tab by remember { mutableStateOf(0) }
    Navigator(
        screen = ShellScreen,
        disposeBehavior = NavigatorDisposeBehavior(disposeSteps = false, disposeNestedNavigators = false),
    ) { navigator ->
        LaunchedEffect(navigator) {
            Nav.push = { navigator.push(it) }
            Nav.pop = { if (navigator.size > 1) navigator.pop() }
        }
        LaunchedEffect(navigator, tab) {
            // Where "done" lands: back to the tab host, on Home.
            Nav.home = {
                while (navigator.size > 1) navigator.pop()
                tab = 0
            }
            Nav.tabHost = { tab = it }
        }

        // The web's #app.vfade, replayed on every route change: the screen that arrives rises 4dp and
        // fades in over the long duration on the emphasized-decelerate curve. The one leaving is not
        // drawn — the web keys #app on the path, so React unmounts it rather than fading it out.
        val route = navigator.lastItem to tab
        val appear = remember { Animatable(0f) }
        val reduce = reduceMotion()
        // The scheme's own spatial spec: the same spring the sheet, the dialog and the segmented
        // thumb move on, rather than this file's private bézier.
        val appearSpec = spatialSpec<Float>()
        LaunchedEffect(route, reduce) {
            if (reduce) {
                // Reduced motion: the screen is simply there. The fade explains a spatial change,
                // and a reader who has asked for no animation has asked not to have it explained.
                appear.snapTo(1f)
            } else {
                appear.snapTo(0f)
                appear.animateTo(1f, appearSpec)
            }
        }

        val snapshot by ui.state.collectAsState()
        // Back closes the top sheet, then pops a screen, then belongs to the system — which is what
        // the web's back button does in three steps as well.
        BackHandler(enabled = snapshot.sheets.isNotEmpty() || navigator.size > 1) {
            if (!ui.closeTop() && navigator.size > 1) navigator.pop()
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
            bottomBar = {
                // The bar is the app's own, so it carries the gesture inset itself: M3's own
                // navigation bar does this internally and a Surface does not.
                Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                    RestTimerBar(snapshot)
                    AppTabBar(
                        selected = tab,
                        onTab = { index ->
                            tab = index
                            // A tab is a destination, and the bar is visible over a pushed screen, so
                            // tapping one has to leave that screen: the web's tab bar navigates and
                            // the session keeps running. Without this the four tabs were inert
                            // whenever a screen was on top of the stack — a session screen, or any
                            // sheet-opened screen — because onTab only set state nothing read until
                            // the stack happened to return to the host.
                            while (navigator.size > 1) navigator.pop()
                        },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                Box(
                    Modifier
                        // A phone in one hand is the target. On a tablet or in landscape the same
                        // layout stretched edge to edge reads as a phone screen photographed onto a
                        // bigger one, with a 1,000px-wide button in it. Capping the reading width
                        // and centring the column is the whole of the adaptive work this app needs,
                        // and it changes nothing at all on a phone, where this box is never wider.
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .graphicsLayer {
                            alpha = appear.value
                            translationY = (1f - appear.value) * 4.dp.toPx()
                        },
                ) {
                    if (navigator.lastItem is ShellScreen) TabContent(tab) else navigator.lastItem.Content()
                }
                val toast = snapshot.toast
                if (toast != null) {
                    Toast(toast, Modifier.align(Alignment.BottomCenter).padding(16.dp))
                }
            }
        }
        SheetHost(ui)
    }
}

/** The root screen. Its own content is the tabs below, drawn by the shell so the bar can see them. */
internal object ShellScreen : AppScreen() {
    @Composable
    override fun Content() = Unit
}

@Composable
private fun TabContent(tab: Int) {
    when (tab) {
        0 -> HomeScreen()
        1 -> PlanScreen.Content()
        2 -> StatsScreen.Content()
        else -> LibraryScreen.Content()
    }
}

/**
 * The app's "done" feedback, as M3's own Snackbar.
 *
 * It was a hand-built Surface with the inverse roles on it. That looked right and said nothing: the
 * text appeared, no screen reader announced it, and it was gone by the time anyone looked. M3's
 * Snackbar brings its own semantics, insets and padding, and the app's inverse-surface pill is kept
 * through its colour and shape parameters so the change is the component, not the look.
 *
 * It is the Snackbar rather than a SnackbarHost: the shell holds one message at a time and clears it
 * itself, so a host's queue and its own dismiss timer would be two clocks for one job.
 */
@Composable
private fun Toast(message: String, modifier: Modifier = Modifier) {
    Snackbar(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = FullShape,
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium)
    }
}

/* --------------------------------------------------------------- bottom bar -- */

@Composable
private fun AppTabBar(selected: Int, onTab: (Int) -> Unit) {
    // A Box with the colour rather than a Surface: Surface clips its content to its shape, and the
    // Start control rides 20dp above this bar — a Surface cut the disc off at its own top edge. A
    // Box draws the same tone and lets the disc out.
    Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem(Glyph.HOUSE, t("Home"), selected == 0) { onTab(0) }
            TabItem(Glyph.CALENDAR, t("Plan"), selected == 1) { onTab(1) }
            StartButton()
            TabItem(Glyph.CHART, t("Stats"), selected == 2) { onTab(2) }
            TabItem(Glyph.LIST, t("Exercises"), selected == 3) { onTab(3) }
        }
    }
}

@Composable
private fun RowScope.TabItem(glyph: Glyph, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlyphIcon(
            glyph,
            Modifier.size(24.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            stroke = 1.65f,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The centre button. On the session screen itself there is nothing to resume, so it reads as the tab
 * it is; anywhere else it either starts today's session or brings you back to the one already
 * running — the marker is kept in active.cur and never moves on its own.
 */
@Composable
private fun RowScope.StartButton() {
    val profile = currentProfile()
    val hasSession = profile?.active != null
    val press = remember { MutableInteractionSource() }
    val held by press.collectIsPressedAsState()
    // The web's .start .cir:active: the disc compresses to .94 and springs back, on the long duration.
    val disc by animateFloatAsState(
        targetValue = if (held && !reduceMotion()) 0.94f else 1f,
        animationSpec = fastSpatialSpec(),
        label = "start-press",
    )
    val actionLabel = if (hasSession) t("Resume") else t("Start")
    Column(
        modifier = Modifier
            .weight(1.3f)
            // The web lifts the disc 20px above the bar and takes it out of the flow to do it;
            // offset draws it there without making the bar taller, which is what kept the tabs'
            // indicator misaligned before. Only the drawing moves, so the touch area moves with it.
            .offset(y = (-20).dp)
            .semantics(mergeDescendants = true) { contentDescription = actionLabel; role = Role.Button }
            .clickable(interactionSource = press, indication = LocalIndication.current) {
                if (hasSession) {
                    Nav.to(WorkoutScreen)
                } else {
                    val today = profile?.let { effectiveDay(it.raw, todayISO()) }
                    if (today != null && today.ex.isNotEmpty()) startFlow(today) else Nav.to(WorkoutScreen)
                }
            }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .graphicsLayer {
                    scaleX = disc
                    scaleY = disc
                }
                .clip(FabShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.DUMBBELL, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimary, stroke = 1.8f)
        }
        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/* ----------------------------------------------------------------- rest bar -- */

/**
 * One bar, two meanings: the rest countdown between sets, and the work countdown during a timed set.
 * They are mutually exclusive by construction — starting a hold stops any rest — so the bar can
 * never have to show both.
 */
@Composable
private fun RestTimerBar(snapshot: UiSnapshot) {
    val ui = OlyGymApp.ui
    val work = snapshot.work
    val rest = snapshot.rest
    if (work == null && rest == null) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = clock(work?.left ?: rest?.left ?: 0.0),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W700),
                color = if (work != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                val label = work?.label ?: rest?.label
                if (!label.isNullOrBlank()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                val left = work?.left ?: rest?.left ?: 0.0
                val total = work?.total ?: rest?.total ?: 1.0
                // M3 Expressive's wavy bar, as the web's #timer draws it.
                WaveProgress(
                    fraction = if (total > 0) (left / total).toFloat() else 0f,
                    track = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
            Spacer(Modifier.size(10.dp))
            if (work != null) {
                Button(t("Cancel"), { ui.stopWork() }, variant = ButtonVariant.GHOST, size = ButtonSize.SM)
                Button(
                    t("Done"),
                    { ui.finishWorkEarly() },
                    variant = ButtonVariant.PRIMARY,
                    size = ButtonSize.SM,
                    icon = Glyph.CHECK,
                    modifier = Modifier.padding(start = 6.dp),
                )
            } else {
                Button("-15s", { ui.addRest(-15.0) }, variant = ButtonVariant.PLAIN, size = ButtonSize.SM)
                Button("+15s", { ui.addRest(15.0) }, variant = ButtonVariant.PLAIN, size = ButtonSize.SM, modifier = Modifier.padding(start = 6.dp))
                Button(
                    t("Skip"),
                    { ui.stopRest() },
                    variant = ButtonVariant.PRIMARY,
                    size = ButtonSize.SM,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

private fun clock(seconds: Double): String {
    val total = maxOf(0, Math.round(seconds).toInt())
    val rest = total % 60
    return (total / 60).toString() + ":" + (if (rest < 10) "0" else "") + rest.toString()
}
