package olygym.app.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import olygym.app.data.num
import olygym.app.data.str
import olygym.app.data.Catalogue
import olygym.app.lib.fmtDur
import olygym.app.lib.fmtVol
import olygym.app.lib.setsDone
import olygym.app.lib.workSetsDone
import olygym.app.ui.Nav
import olygym.app.ui.components.Button
import olygym.app.ui.components.ButtonVariant
import olygym.app.ui.components.Glyph
import olygym.app.ui.components.GlyphIcon
import olygym.app.ui.components.Tile
import olygym.app.ui.currentProfile
import olygym.app.ui.t
import olygym.app.ui.ui

/**
 * What the session added up to, the moment it ends. Locked: it is the only way out of a finished
 * workout, so the backdrop and back do not dismiss it.
 */
fun finishSummarySheet(workout: JsonObject, prs: List<String>) {
    ui.openSheet(kind = olygym.app.ui.SheetKind.CENTER, locked = true) { close ->
        FinishSummary(workout, prs, close)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FinishSummary(workout: JsonObject, prs: List<String>, close: () -> Unit) {
    val profile = currentProfile() ?: return
    val unit = profile.settings.unit
    val start = workout.num("start") ?: 0.0
    val end = workout.num("end") ?: 0.0

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 12.dp)
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.TROPHY, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = t("Workout complete!"),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Tile(
                label = t("Duration"),
                value = fmtDur((end - start).toLong()),
                modifier = Modifier.weight(1f),
            )
            Tile(
                label = t("Volume"),
                value = fmtVol(workout.num("vol") ?: 0.0, unit),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Tile(
                label = t("Sets"),
                value = setsDone(workout).toString(),
                suffix = t("{0} work", workSetsDone(workout)),
                modifier = Modifier.weight(1f),
            )
            Tile(
                label = t("PRs"),
                value = if (prs.isEmpty()) "—" else prs.size.toString(),
                modifier = Modifier.weight(1f),
            )
        }

        if (prs.isNotEmpty()) {
            Text(
                text = t("New records").uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                prs.forEach { id ->
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = Catalogue.nameOf(id),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        Button(
            text = t("Done"),
            onClick = {
                close()
                Nav.goHome()
            },
            variant = ButtonVariant.PRIMARY,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

/**
 * Shown when the last set of the last exercise is ticked: finish, or keep going. The web's
 * "That's the whole workout!" prompt.
 */
fun workoutCompleteSheet(onFinish: () -> Unit) {
    ui.openSheet(kind = olygym.app.ui.SheetKind.CENTER) { close ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.CHECK_CIRCLE, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = t("That's the whole workout!"),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = t("Finish up, or keep going and add another exercise."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    text = t("Keep going"),
                    onClick = {
                        close()
                        ui.toast(t("Keep going — tap “+ Add exercise” below"))
                    },
                    variant = ButtonVariant.GHOST,
                    size = olygym.app.ui.components.ButtonSize.SM,
                )
                Button(
                    text = t("Finish workout"),
                    onClick = {
                        close()
                        onFinish()
                    },
                    variant = ButtonVariant.GHOST,
                    size = olygym.app.ui.components.ButtonSize.SM,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
