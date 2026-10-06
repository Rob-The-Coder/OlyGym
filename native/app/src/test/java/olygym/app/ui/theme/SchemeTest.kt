package olygym.app.ui.theme

import olygym.app.lib.ACCENTS
import olygym.app.lib.DEFAULT_ACCENT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The generated schemes are the port of the colour block in frontend/src/m3.tokens.css, and the
 * reaction to a bad regeneration has to be a test rather than an eye. The two values pinned here
 * are read straight out of that file; the accent list is compared with lib/format.js's own.
 */
class SchemeTest {

    @Test
    fun `every accent the picker offers has a scheme, and nothing else does`() {
        assertEquals(ACCENTS.keys, SCHEMES.keys)
        assertTrue(SCHEMES.containsKey(DEFAULT_ACCENT))
    }

    @Test
    fun `the default accent's dark roles are the ones in the token file`() {
        val dark = SCHEMES.getValue(DEFAULT_ACCENT).dark
        assertEquals(0xFF151219L, dark.surface)
        assertEquals(0xFF1D1A21L, dark.containerLow)
        assertEquals(0xFF252229L, dark.container)
        assertEquals(0xFF2D2B32L, dark.containerHigh)
        assertEquals(0xFF3B383FL, dark.containerHighest)
        assertEquals(0xFFE4E1EAL, dark.onSurface)
        assertEquals(0xFFC8C5CEL, dark.onSurfaceVariant)
        assertEquals(0xFF48454DL, dark.onSurfaceDisabled)
        assertEquals(0xFF322F36L, dark.hairline)
        assertEquals(0xFF928F98L, dark.outline)
        assertEquals(0xFF48454DL, dark.outlineVariant)
        // M3's published baseline: seed #6750A4 gives primary #D0BCFF-ish in dark and #6750A4 in
        // light, and the generator is CIELAB rather than CAM16, so it lands a tone away in dark.
        assertEquals(0xFFD2BBFFL, dark.primary)
        assertEquals(0xFF32226FL, dark.onPrimary)
        assertEquals(0xFF4C3889L, dark.primaryContainer)
        assertEquals(0xFFE9DDFFL, dark.onPrimaryContainer)
    }

    @Test
    fun `the default accent's light primary is exactly the M3 baseline purple`() {
        val light = SCHEMES.getValue(DEFAULT_ACCENT).light
        assertEquals(0xFF6750A4L, light.primary)
        assertEquals(0xFFFBF8FFL, light.surface)
    }

    @Test
    fun `error is M3 baseline red under every accent, so a destructive row looks the same`() {
        SCHEMES.forEach { (accent, scheme) ->
            assertEquals(accent, 0xFFF2B8B5L, scheme.dark.error)
            assertEquals(accent, 0xFF8C1D18L, scheme.dark.errorContainer)
            assertEquals(accent, 0xFFB3261EL, scheme.light.error)
            assertEquals(accent, 0xFFF9DEDCL, scheme.light.errorContainer)
        }
    }

    @Test
    fun `body text on the surface and on a container card clears the contrast floor`() {
        SCHEMES.forEach { (accent, scheme) ->
            listOf(scheme.dark, scheme.light).forEach { roles ->
                assertTrue(accent, contrast(roles.onSurface, roles.surface) >= 7.0)
                assertTrue(accent, contrast(roles.onSurface, roles.container) >= 7.0)
                assertTrue(accent, contrast(roles.onSurfaceVariant, roles.container) >= 4.5)
                assertTrue(accent, contrast(roles.primary, roles.container) >= 4.5)
                assertTrue(accent, contrast(roles.onPrimary, roles.primary) >= 4.5)
            }
        }
    }

    @Test
    fun `every role is opaque and none is left unset`() {
        SCHEMES.forEach { (accent, scheme) ->
            listOf(scheme.dark, scheme.light).forEach { roles ->
                listOf(
                    roles.surface, roles.containerLow, roles.container, roles.containerHigh,
                    roles.containerHighest, roles.onSurface, roles.onSurfaceVariant,
                    roles.onSurfaceDisabled, roles.hairline, roles.outline, roles.outlineVariant,
                    roles.primary, roles.onPrimary, roles.primaryContainer, roles.onPrimaryContainer,
                    roles.error, roles.onError, roles.errorContainer, roles.onErrorContainer,
                ).forEach { role ->
                    assertNotNull(accent, role)
                    assertEquals(accent, 0xFF000000L, role and 0xFF000000L)
                }
            }
        }
    }

    /**
     * WCAG relative-luminance contrast, the same measurement scripts/design/m3-scheme.mjs makes.
     * The palette is generated, so this only has to catch a regeneration that went wrong.
     */
    private fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return if (la > lb) (la + 0.05) / (lb + 0.05) else (lb + 0.05) / (la + 0.05)
    }

    private fun luminance(argb: Long): Double {
        val r = channel(((argb shr 16) and 0xFF).toInt())
        val g = channel(((argb shr 8) and 0xFF).toInt())
        val b = channel((argb and 0xFF).toInt())
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun channel(v: Int): Double {
        val c = v / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
}
