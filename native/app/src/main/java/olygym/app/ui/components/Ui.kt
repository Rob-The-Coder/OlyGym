package olygym.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row as LayoutRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import olygym.app.lib.fmtNum
import olygym.app.lib.parseNumInput
import olygym.app.ui.theme.CardShape
import olygym.app.ui.theme.FullShape
import olygym.app.ui.theme.OVERLINE_TRACK
import olygym.app.ui.theme.OverlineWeight
import olygym.app.ui.theme.emphasizedWeight
import olygym.app.ui.theme.extraColors

/*
 * The shared controls, ported from frontend/src/components/ui.jsx — only the ones the screens in
 * this phase actually use, so nothing here is a guess at what a later screen wants.
 *
 * The web builds every input itself instead of styling the platform's, because a native control
 * painted its own colours and ignored the theme. That reason does not exist in Compose: an M3
 * component is already themed, so the switch below is Material3's own. The rest stay the app's,
 * because their metrics are the app's and M3 has no equivalent — a full-width 17px button, a 29px
 * tinted icon rail, a 30px checkbox ring inside a 44px target.
 */

/** The web app's .sech: a 12px uppercase overline above a group of cards. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = OverlineWeight,
            letterSpacing = OVERLINE_TRACK.em,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * The web app's .card: one job on a container surface. No shadow — tone is what separates a
 * surface here, and on dark a shadow barely reads anyway.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = CardShape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/**
 * The web app's .sect: an inset-grouped list — a label over one rounded surface holding rows that
 * share hairlines. The app's main structural primitive, and what a menu is built out of.
 */
@Composable
fun Section(
    title: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 7.dp),
            )
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = CardShape) {
            Column(content = content)
        }
        if (footer != null) {
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 7.dp),
            )
        }
    }
}

/** The hairline between two rows of a Section, inset past the icon rail. */
@Composable
fun RowDivider(inset: Dp = 14.dp) {
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.extraColors.hairline,
        modifier = Modifier.padding(start = inset),
    )
}

/** A card's own heading: the block title, the line under it, and room for its control. */
@Composable
fun CardHead(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    LayoutRow(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * The web app's .tile: a label, a value and a unit in a compact block, three to a row on Home.
 * The value carries M3's emphasized weight — the scale that changes weight only.
 */
@Composable
fun Tile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    icon: Glyph? = null,
    valueColor: Color? = null,
    onClick: (() -> Unit)? = null,
    /** A control that belongs to the label line — the goal tile's own help button. */
    labelTrailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = CardShape,
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.padding(14.dp)) {
            LayoutRow(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (icon != null) {
                    GlyphIcon(icon, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                labelTrailing?.invoke()
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = emphasizedWeight(FontWeight.W600),
                    letterSpacing = (-0.026).em,
                ),
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp),
            )
            if (suffix != null) {
                Text(
                    text = suffix,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Which trailing mark a row carries, if any. */
enum class Accessory { NONE, CHEVRON, CHECK }

/**
 * The web app's .lrow: one row of a grouped list or a menu. Named ListRow rather than Row so it
 * cannot be confused with the layout Row it is built on.
 *
 * trailing is the slot a control goes in (a switch, an info button), and sits between the title and
 * the value, which is where the web puts it too.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: Glyph? = null,
    subtitle: String? = null,
    value: String? = null,
    accessory: Accessory = Accessory.NONE,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    LayoutRow(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 46.dp)
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(29.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(icon, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(letterSpacing = (-0.012).em),
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.copy(letterSpacing = (-0.012).em),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }
        when (accessory) {
            Accessory.NONE -> Unit
            Accessory.CHEVRON -> GlyphIcon(
                Glyph.CHEVRON_RIGHT,
                Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurface,
                stroke = 2.4f,
            )
            Accessory.CHECK -> GlyphIcon(
                Glyph.CHECK,
                Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.primary,
                stroke = 2.4f,
            )
        }
    }
}

/* ============================ buttons ============================ */

enum class ButtonVariant { PLAIN, PRIMARY, TINTED, DANGER, GHOST }

/** MD is the web's full-width .btn; SM and XS wrap their content. */
enum class ButtonSize { MD, SM, XS }

/**
 * The web app's .btn: a full-width 17px/600 button with a 12px radius, and smaller inline variants.
 * Built here rather than taken from Material3 for the metrics, not the colours: the app has screens
 * full of these, and M3's own paddings and type scale would reflow every one of them.
 */
@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PLAIN,
    size: ButtonSize = ButtonSize.MD,
    icon: Glyph? = null,
    trailingIcon: Glyph? = null,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val background = when (variant) {
        ButtonVariant.PRIMARY -> colors.primary
        ButtonVariant.TINTED -> colors.primary.copy(alpha = 0.16f)
        ButtonVariant.DANGER -> colors.error.copy(alpha = 0.15f)
        ButtonVariant.PLAIN -> colors.surfaceContainerHigh
        ButtonVariant.GHOST -> Color.Transparent
    }
    val foreground = when (variant) {
        ButtonVariant.PRIMARY -> colors.onPrimary
        ButtonVariant.TINTED, ButtonVariant.GHOST -> colors.primary
        ButtonVariant.DANGER -> colors.error
        ButtonVariant.PLAIN -> colors.onSurface
    }
    val padding = when (size) {
        ButtonSize.MD -> Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
        ButtonSize.SM -> Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ButtonSize.XS -> Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
    }
    val shape = when (size) {
        ButtonSize.MD -> RoundedCornerShape(12.dp)
        ButtonSize.SM -> RoundedCornerShape(8.dp)
        ButtonSize.XS -> RoundedCornerShape(7.dp)
    }
    val style = when (size) {
        ButtonSize.MD -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W600)
        ButtonSize.SM -> MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W600)
        ButtonSize.XS -> MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600)
    }
    val iconSize = when (size) {
        ButtonSize.MD -> 19.dp
        ButtonSize.SM -> 16.dp
        ButtonSize.XS -> 14.dp
    }
    val alpha = if (enabled) 1f else 0.32f

    Box(
        modifier = modifier
            .then(if (size == ButtonSize.MD) Modifier.fillMaxWidth() else Modifier)
            .clip(shape)
            .background(background)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .then(padding)
            .defaultMinSize(minHeight = if (size == ButtonSize.MD) 48.dp else 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        LayoutRow(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (size == ButtonSize.XS) 4.dp else 7.dp),
        ) {
            if (icon != null) GlyphIcon(icon, Modifier.size(iconSize), tint = foreground.copy(alpha = alpha))
            if (text.isNotEmpty()) {
                Text(text = text, style = style, color = foreground.copy(alpha = alpha), maxLines = 1)
            }
            if (trailingIcon != null) {
                GlyphIcon(trailingIcon, Modifier.size(iconSize), tint = foreground.copy(alpha = alpha))
            }
        }
    }
}

/* ============================ checkbox ============================ */

/**
 * The web app's .chk: a 30px ring that fills with the accent when it is on, inside a 44px target —
 * 30 is what looks right beside a stepper but a poor thing to hit with a thumb.
 */
@Composable
fun Check(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .then(if (enabled) Modifier.clickable { onChange(!checked) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .then(
                    if (checked) {
                        Modifier.background(MaterialTheme.colorScheme.primary)
                    } else {
                        Modifier.border(1.8.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                GlyphIcon(
                    Glyph.CHECK,
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    stroke = 2.6f,
                )
            }
        }
    }
}

/* ============================ number field ============================ */

/**
 * The web app's NumberField: a number you can type, keeping its own text while it has focus so a
 * half-typed "62." is not rewritten under the finger, and snapping back to the formatted value when
 * the focus goes. The commit rule itself is in lib/NumInput.kt, where it has a test.
 *
 * nullable is for the fields where "nothing entered" and 0 mean different things — an unlogged RIR
 * is not a set taken to failure.
 */
@Composable
fun NumberField(
    value: Double?,
    onChange: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = true,
    nullable: Boolean = false,
    textStyle: TextStyle? = null,
    align: TextAlign = TextAlign.Center,
) {
    var draft by remember { mutableStateOf<String?>(null) }
    var committed by remember { mutableStateOf<Double?>(null) }
    // The draft stops being shown as soon as the value is no longer the one this field committed:
    // something else changed it, and half-typed text must not win over that.
    val shown = if (draft != null && committed == value) draft!! else value?.let { fmtNum(it) } ?: ""
    val style = (textStyle ?: MaterialTheme.typography.bodyLarge).copy(
        textAlign = align,
        color = MaterialTheme.colorScheme.onSurface,
    )
    BasicTextField(
        value = shown,
        onValueChange = { typed ->
            val parsed = parseNumInput(typed, decimal, nullable)
            draft = parsed.draft
            committed = parsed.value
            onChange(parsed.value)
        },
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = modifier.onFocusChanged { state ->
            if (!state.isFocused) {
                draft = null
                committed = null
            }
        },
    )
}

/* ============================ stepper ============================ */

/**
 * The web app's Stepper: a minus, a value you can also type, and a plus, in a pill. A stepper with a
 * label of its own is a row — the label takes the line and the pill keeps 150dp — which is how the
 * design system says a lone stepper reads.
 *
 * The value is clamped at zero and rounded to two decimals on every change, typing included, the
 * same rule the web control applies.
 *
 * ponytail: hold-to-repeat, which the web control accelerates after 400ms, is not here; a tap is one
 * step and the field takes a typed number.
 */
@Composable
fun Stepper(
    value: Double,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    step: Double = 1.0,
    decimal: Boolean = true,
    label: String? = null,
    unit: String? = null,
) {
    val set = { v: Double -> onChange(maxOf(0.0, Math.round(v * 100) / 100.0)) }
    val pill = @Composable {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = FullShape,
            modifier = Modifier.width(150.dp),
        ) {
            LayoutRow(verticalAlignment = Alignment.CenterVertically) {
                StepperButton(Glyph.MINUS, "Decrease") { set(value - step) }
                Box(
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LayoutRow(verticalAlignment = Alignment.CenterVertically) {
                        NumberField(
                            value = value,
                            onChange = { set(it ?: 0.0) },
                            decimal = decimal,
                            modifier = Modifier.width(56.dp),
                        )
                        if (unit != null) {
                            Text(
                                text = unit,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 2.dp),
                            )
                        }
                    }
                }
                StepperButton(Glyph.PLUS, "Increase") { set(value + step) }
            }
        }
    }

    if (label == null) {
        Box(modifier) { pill() }
    } else {
        LayoutRow(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            pill()
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StepperButton(
    glyph: Glyph,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, Modifier.size(20.dp))
    }
}

/* ============================ icon button ============================ */

/**
 * The web app's .iconbtn: a 36px circle that paints the surface tone when it is filled, and the
 * app bar's transparent variant otherwise. The tap target is the 44px box, not the 36px circle.
 */
@Composable
fun IconButton(
    glyph: Glyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    filled: Boolean = false,
    enabled: Boolean = true,
    stroke: Float = 1.7f,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .then(
                    if (filled) Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                glyph,
                Modifier.size(18.dp),
                tint = tint.copy(alpha = if (enabled) 1f else 0.32f),
                stroke = stroke,
            )
        }
    }
}
