package dev.opencode.mobile.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every theme has to be usable, not merely present.
 *
 * These are the invariants that break silently: a role left at the factory
 * default, a theme whose accent disappears into its own surface, or a palette
 * that is not actually a light/dark pair. The generator checks contrast when it
 * writes the file; this checks the file that was actually committed, so a
 * hand-edit that bypasses the generator still fails here.
 */
class ThemeFamilyTest {

    private val families = ThemeFamily.entries

    private fun ColorScheme.all(): Map<String, Color> = mapOf(
        "primary" to primary,
        "onPrimary" to onPrimary,
        "primaryContainer" to primaryContainer,
        "onPrimaryContainer" to onPrimaryContainer,
        "inversePrimary" to inversePrimary,
        "secondary" to secondary,
        "onSecondary" to onSecondary,
        "secondaryContainer" to secondaryContainer,
        "onSecondaryContainer" to onSecondaryContainer,
        "tertiary" to tertiary,
        "onTertiary" to onTertiary,
        "tertiaryContainer" to tertiaryContainer,
        "onTertiaryContainer" to onTertiaryContainer,
        "background" to background,
        "onBackground" to onBackground,
        "surface" to surface,
        "onSurface" to onSurface,
        "surfaceVariant" to surfaceVariant,
        "onSurfaceVariant" to onSurfaceVariant,
        "surfaceTint" to surfaceTint,
        "inverseSurface" to inverseSurface,
        "inverseOnSurface" to inverseOnSurface,
        "error" to error,
        "onError" to onError,
        "errorContainer" to errorContainer,
        "onErrorContainer" to onErrorContainer,
        "outline" to outline,
        "outlineVariant" to outlineVariant,
        "scrim" to scrim,
        "surfaceBright" to surfaceBright,
        "surfaceDim" to surfaceDim,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainerLowest" to surfaceContainerLowest,
        "primaryFixed" to primaryFixed,
        "primaryFixedDim" to primaryFixedDim,
        "onPrimaryFixed" to onPrimaryFixed,
        "onPrimaryFixedVariant" to onPrimaryFixedVariant,
        "secondaryFixed" to secondaryFixed,
        "secondaryFixedDim" to secondaryFixedDim,
        "onSecondaryFixed" to onSecondaryFixed,
        "onSecondaryFixedVariant" to onSecondaryFixedVariant,
        "tertiaryFixed" to tertiaryFixed,
        "tertiaryFixedDim" to tertiaryFixedDim,
        "onTertiaryFixed" to onTertiaryFixed,
        "onTertiaryFixedVariant" to onTertiaryFixedVariant,
    )

    private fun contrast(foreground: Color, background: Color): Double {
        val a = foreground.luminance()
        val b = background.luminance()
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    /** The pairs the app actually renders, and the ratio each one needs. */
    private fun renderedPairs(scheme: ColorScheme) = listOf(
        "onPrimary on primary" to (scheme.onPrimary to scheme.primary),
        "onPrimaryContainer on primaryContainer" to
            (scheme.onPrimaryContainer to scheme.primaryContainer),
        "onSecondary on secondary" to (scheme.onSecondary to scheme.secondary),
        "onSecondaryContainer on secondaryContainer" to
            (scheme.onSecondaryContainer to scheme.secondaryContainer),
        "onTertiary on tertiary" to (scheme.onTertiary to scheme.tertiary),
        "onTertiaryContainer on tertiaryContainer" to
            (scheme.onTertiaryContainer to scheme.tertiaryContainer),
        "onError on error" to (scheme.onError to scheme.error),
        "onErrorContainer on errorContainer" to
            (scheme.onErrorContainer to scheme.errorContainer),
        "onSurface on surface" to (scheme.onSurface to scheme.surface),
        "onBackground on background" to (scheme.onBackground to scheme.background),
        "onSurfaceVariant on surfaceVariant" to
            (scheme.onSurfaceVariant to scheme.surfaceVariant),
        // The code-block and card surfaces the app paints itself.
        "onSurface on surfaceContainerHighest" to
            (scheme.onSurface to scheme.surfaceContainerHighest),
        "onSurfaceVariant on surfaceContainerHighest" to
            (scheme.onSurfaceVariant to scheme.surfaceContainerHighest),
        "onSurface on surfaceContainerLow" to (scheme.onSurface to scheme.surfaceContainerLow),
        "onSurface on surfaceContainerHigh" to (scheme.onSurface to scheme.surfaceContainerHigh),
        "onSurfaceVariant on surfaceContainerHigh" to
            (scheme.onSurfaceVariant to scheme.surfaceContainerHigh),
    )

    @Test
    fun `every theme supplies both modes`() {
        // A missing mode would fall back to the M3 baseline purple, which is how
        // one screen ends up looking like a different app.
        families.forEach { family ->
            val light = family.scheme(dark = false)
            val dark = family.scheme(dark = true)
            assertTrue(
                "${family.name} returns the same scheme for both modes",
                light.surface != dark.surface,
            )
        }
    }

    @Test
    fun `every role is filled in every theme`() {
        families.forEach { family ->
            listOf(false, true).forEach { dark ->
                val scheme = family.scheme(dark)
                val mode = if (dark) "dark" else "light"
                scheme.all().forEach { (role, color) ->
                    assertTrue(
                        "${family.name} $mode has no value for $role",
                        color.alpha > 0f,
                    )
                }
            }
        }
    }

    @Test
    fun `no two themes are the same scheme`() {
        // A copy-paste slip in the generator would ship two themes that look
        // identical, and the picker would appear to do nothing.
        families.forEachIndexed { index, family ->
            families.drop(index + 1).forEach { other ->
                listOf(false, true).forEach { dark ->
                    assertTrue(
                        "${family.name} and ${other.name} share a ${if (dark) "dark" else "light"} primary",
                        family.scheme(dark).primary != other.scheme(dark).primary,
                    )
                }
            }
        }
    }

    @Test
    fun `rendered text pairs meet their contrast threshold`() {
        families.forEach { family ->
            listOf(false, true).forEach { dark ->
                val mode = if (dark) "dark" else "light"
                renderedPairs(family.scheme(dark)).forEach { (label, pair) ->
                    val ratio = contrast(pair.first, pair.second)
                    assertTrue(
                        "${family.name} $mode: $label is ${"%.2f".format(ratio)}:1, needs 4.5:1",
                        ratio >= 4.5,
                    )
                }
            }
        }
    }

    @Test
    fun `the brand and success colours are visible on their surface`() {
        // Both are indicators — a caret, a live dot, a status glyph — so they are
        // held to the 3:1 non-text threshold rather than the text one.
        families.forEach { family ->
            listOf(false, true).forEach { dark ->
                val scheme = family.scheme(dark)
                val mode = if (dark) "dark" else "light"

                val brand = contrast(family.brand(dark), scheme.surface)
                assertTrue(
                    "${family.name} $mode: brand on surface is ${"%.2f".format(brand)}:1",
                    brand >= 3,
                )

                val success = contrast(family.success(dark), scheme.surface)
                assertTrue(
                    "${family.name} $mode: success on surface is ${"%.2f".format(success)}:1",
                    success >= 3,
                )
            }
        }
    }

    @Test
    fun `the light scheme is lighter than the dark one`() {
        // The one thing that makes a scheme the right mode. A theme built with
        // the modes swapped is unreadable and otherwise passes every role check.
        families.forEach { family ->
            val light = family.scheme(dark = false).surface.luminance()
            val dark = family.scheme(dark = true).surface.luminance()
            assertTrue("${family.name} light surface is not lighter than dark", light > dark)
        }
    }

    @Test
    fun `the user bubble is readable`() {
        // The bubble is painted from `primaryContainer`, and the app draws the
        // user's own text on it. This is the pair a theme change is most likely
        // to break, because it is a container rather than a surface.
        families.forEach { family ->
            listOf(false, true).forEach { dark ->
                val scheme = family.scheme(dark)
                val ratio = contrast(scheme.onPrimaryContainer, scheme.primaryContainer)
                assertTrue(
                    "${family.name}: bubble text is ${"%.2f".format(ratio)}:1",
                    ratio >= 4.5,
                )
            }
        }
    }

    @Test
    fun `a code block is a surface above the page in dark mode`() {
        // Code blocks sit on `surfaceContainerHighest`. If that were darker than
        // the page in dark mode, every block would read as a hole rather than a
        // raised panel.
        families.forEach { family ->
            val scheme = family.scheme(dark = true)
            assertTrue(
                "${family.name}: code block is not above the surface",
                scheme.surfaceContainerHighest.luminance() > scheme.surface.luminance(),
            )
        }
    }

    @Test
    fun `the stored name resolves, and an unknown one falls back`() {
        families.forEach { family ->
            assertTrue(
                "${family.name} does not resolve from its stored name",
                ThemeFamily.fromStored(family.name) == family,
            )
        }
        assertTrue(ThemeFamily.fromStored("NoSuchTheme") == ThemeFamily.Default)
        assertTrue(ThemeFamily.fromStored(null) == ThemeFamily.Default)
        assertTrue(ThemeFamily.fromStored("") == ThemeFamily.Default)
    }

    @Test
    fun `the default theme is one of the offered ones`() {
        assertTrue(
            "the default theme is not in the list",
            ThemeFamily.entries.contains(ThemeFamily.Default),
        )
    }

    @Test
    fun `a translucent overlay lands where it claims to`() {
        // Several surfaces are drawn at an alpha over the page — the session row,
        // the composer, the settings cards. Text on those is measured against the
        // blended result, so the blend has to be predictable.
        val page = Color(0xFFF6FBF3)
        val overlay = Color(0xFF000000)
        val blended = overlay.copy(alpha = 0.1f).compositeOver(page)
        assertTrue("a black overlay did not darken the page", blended.luminance() < page.luminance())
    }
}
