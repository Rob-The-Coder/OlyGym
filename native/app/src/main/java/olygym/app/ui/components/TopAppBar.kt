package olygym.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em

/**
 * The Material 3 large top app bar, in the app's type: a 64dp action row that stays put and a big
 * title that scrolls under it.
 *
 * The web app builds this out of two siblings and a scroll listener, because it had to keep the bar
 * from changing height as the page scrolls. M3's exit-until-collapsed behaviour is the same thing
 * done by the platform, so this is the one control that is M3's rather than a port of the CSS.
 *
 * The screen owns the scroll behaviour, because the Scaffold needs the same instance for its
 * nested-scroll connection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OlyAppBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    LargeTopAppBar(
        modifier = modifier,
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.W700,
                        letterSpacing = (-0.028).em,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        navigationIcon = { leading?.invoke() },
        actions = actions ?: {},
        scrollBehavior = scrollBehavior,
        // The page's own background shows through: a bar with its own tone would read as a
        // second surface on a screen that is already a stack of them.
        colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = Color.Transparent),
    )
}

/** The behaviour a screen's Scaffold and its OlyAppBar share. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun olyAppBarScrollBehavior(): TopAppBarScrollBehavior =
    TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
