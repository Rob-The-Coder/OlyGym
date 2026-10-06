package olygym.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import olygym.app.ui.home.HomeScreen
import olygym.app.ui.plan.PlanScreen
import olygym.app.ui.sheet.SheetHost
import olygym.app.ui.theme.FullShape
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
                    AppTabBar(selected = tab, onTab = { tab = it })
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (navigator.lastItem is ShellScreen) TabContent(tab) else navigator.lastItem.Content()
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
        2 -> Placeholder(t("Stats"))
        else -> Placeholder(t("Exercises"))
    }
}

@Composable
private fun Placeholder(title: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // Deliberately plain: this screen is a later phase of the port, and saying so beats an empty
        // surface that looks broken.
        Text(
            text = t("Not ported yet."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun Toast(message: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = FullShape,
        modifier = modifier,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/* --------------------------------------------------------------- bottom bar -- */

@Composable
private fun AppTabBar(selected: Int, onTab: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
            .clickable(onClick = onClick)
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
    Column(
        modifier = Modifier
            .weight(1.3f)
            .clickable {
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
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.DUMBBELL, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimary, stroke = 1.8f)
        }
        Text(
            text = if (hasSession) t("Resume") else t("Start"),
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
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(if (total > 0) (left / total).toFloat().coerceIn(0f, 1f) else 0f)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
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
