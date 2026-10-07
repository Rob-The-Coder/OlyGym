package olygym.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

/**
 * The app's type scale. M3's baseline roles with the house tracking from
 * frontend/src/m3.tokens.css — sizes and leading stay M3's, because per DESIGN.md 6 weight and
 * tracking are what a role carries and the app's own sizes live with the call sites.
 */
internal val OlyGymTypography: Typography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(letterSpacing = (-0.012).em),
        displayMedium = t.displayMedium.copy(letterSpacing = (-0.012).em),
        displaySmall = t.displaySmall.copy(letterSpacing = (-0.012).em),
        headlineLarge = t.headlineLarge.copy(letterSpacing = (-0.012).em),
        headlineMedium = t.headlineMedium.copy(letterSpacing = (-0.012).em),
        headlineSmall = t.headlineSmall.copy(letterSpacing = (-0.020).em),
        titleLarge = t.titleLarge.copy(letterSpacing = (-0.008).em),
        titleMedium = t.titleMedium.copy(letterSpacing = (-0.006).em),
        titleSmall = t.titleSmall.copy(letterSpacing = (-0.006).em),
        bodyLarge = t.bodyLarge.copy(letterSpacing = (-0.002).em),
        bodyMedium = t.bodyMedium.copy(letterSpacing = 0.em),
        bodySmall = t.bodySmall.copy(letterSpacing = 0.em),
        labelLarge = t.labelLarge.copy(letterSpacing = 0.006.em),
        labelMedium = t.labelMedium.copy(letterSpacing = 0.030.em),
        labelSmall = t.labelSmall.copy(letterSpacing = 0.006.em),
    )
}

/** Uppercase section labels (.sech in the web app): the overline weight and tracking. */
internal val OverlineWeight = FontWeight.W700
internal const val OVERLINE_TRACK = 0.08f

/**
 * The Emphasized scale changes weight only, never a size — it is a drop-in that cannot reflow a
 * layout. In the token table that comes out as exactly 400 -> 500 and 500 -> 700, so the rule is
 * the whole table.
 *
 * It is picked per call site, exactly as the web picks it — a sheet's title, a card's big value, a
 * tile's value, the app bar's headline, the weekday on a day row — and passed the role's own
 * baseline weight, so the pair stays the token table's. It is deliberately not a blanket step: the
 * web's own token test asserts which selectors carry it, and widening it would be a heavier app
 * than the one being ported.
 */
internal fun emphasizedWeight(base: FontWeight?): FontWeight {
    // A TextStyle's weight is nullable, and null means the default weight — FontWeight.Normal, which
    // is the 400 the rule starts from.
    val w = base ?: FontWeight.Normal
    return when (w) {
        FontWeight.W400 -> FontWeight.W500
        FontWeight.W500 -> FontWeight.W700
        else -> w
    }
}
