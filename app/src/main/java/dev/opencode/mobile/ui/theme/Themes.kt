// GENERATED FILE — do not edit by hand.
//
// Colours come from Material 3's colour utilities: one seed colour expanded into
// a full tonal scheme by the M3 algorithm. To add or retune a theme, edit
// tools/theme-gen/generate.js and run it:
//
//   cd tools/theme-gen && npm install && npm run generate

package dev.opencode.mobile.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * A theme's two modes, plus the two colours that are not M3 roles.
 *
 * Both schemes are built up front: there are only a handful of themes and a
 * scheme is 48 immutable colours, so building one at the moment it is selected
 * would be work for no reason.
 */
internal class ThemeColors(
    val light: ColorScheme,
    val dark: ColorScheme,
    /** The vivid seed-hue mark used for the logo, the caret and live pulses. */
    val lightBrand: Color,
    val darkBrand: Color,
    /** "Done" green, harmonised towards the theme's own hue. */
    val lightSuccess: Color,
    val darkSuccess: Color,
)

/**
 * The themes offered in Settings.
 *
 * [label] and [description] are what the user sees. Adding a theme means adding
 * an entry to the generator and running it — nothing else in the app changes.
 */
enum class ThemeFamily(
    val label: String,
    val description: String,
    private val colors: ThemeColors,
) {
    OpenCode("OpenCode", "Signal green, the app's own colour", OpenCodeScheme),
    Midnight("Midnight", "Deep indigo, quiet and cool", MidnightScheme),
    Tide("Tide", "Teal, clear and calm", TideScheme),
    Plum("Plum", "Muted purple", PlumScheme),
    Ember("Ember", "Warm orange, high energy", EmberScheme),
    Sand("Sand", "Warm neutral, low chroma", SandScheme),
    Mono("Mono", "Greyscale, no hue at all", MonoScheme),

    ;

    fun scheme(dark: Boolean): ColorScheme = if (dark) colors.dark else colors.light
    fun brand(dark: Boolean): Color = if (dark) colors.darkBrand else colors.lightBrand
    fun success(dark: Boolean): Color = if (dark) colors.darkSuccess else colors.lightSuccess

    companion object {
        /** The theme the app ships with, and where an unknown stored value lands. */
        val Default: ThemeFamily = OpenCode

        fun fromStored(stored: String?): ThemeFamily =
            entries.firstOrNull { it.name == stored } ?: Default
    }
}

/**
 * The full role list, so each theme file reads as a list of values rather than
 * repeating the factory argument names it was generated from.
 */
internal fun schemeOf(
    isDark: Boolean,
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    inversePrimary: Color,
    secondary: Color,
    onSecondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    tertiary: Color,
    onTertiary: Color,
    tertiaryContainer: Color,
    onTertiaryContainer: Color,
    background: Color,
    onBackground: Color,
    surface: Color,
    onSurface: Color,
    surfaceVariant: Color,
    onSurfaceVariant: Color,
    surfaceTint: Color,
    inverseSurface: Color,
    inverseOnSurface: Color,
    error: Color,
    onError: Color,
    errorContainer: Color,
    onErrorContainer: Color,
    outline: Color,
    outlineVariant: Color,
    scrim: Color,
    surfaceBright: Color,
    surfaceContainer: Color,
    surfaceContainerHigh: Color,
    surfaceContainerHighest: Color,
    surfaceContainerLow: Color,
    surfaceContainerLowest: Color,
    surfaceDim: Color,
    primaryFixed: Color,
    primaryFixedDim: Color,
    onPrimaryFixed: Color,
    onPrimaryFixedVariant: Color,
    secondaryFixed: Color,
    secondaryFixedDim: Color,
    onSecondaryFixed: Color,
    onSecondaryFixedVariant: Color,
    tertiaryFixed: Color,
    tertiaryFixedDim: Color,
    onTertiaryFixed: Color,
    onTertiaryFixedVariant: Color,
): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = surfaceTint,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = scrim,
        surfaceBright = surfaceBright,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceDim = surfaceDim,
        primaryFixed = primaryFixed,
        primaryFixedDim = primaryFixedDim,
        onPrimaryFixed = onPrimaryFixed,
        onPrimaryFixedVariant = onPrimaryFixedVariant,
        secondaryFixed = secondaryFixed,
        secondaryFixedDim = secondaryFixedDim,
        onSecondaryFixed = onSecondaryFixed,
        onSecondaryFixedVariant = onSecondaryFixedVariant,
        tertiaryFixed = tertiaryFixed,
        tertiaryFixedDim = tertiaryFixedDim,
        onTertiaryFixed = onTertiaryFixed,
        onTertiaryFixedVariant = onTertiaryFixedVariant,
    )
} else {
    lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = surfaceTint,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = scrim,
        surfaceBright = surfaceBright,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceDim = surfaceDim,
        primaryFixed = primaryFixed,
        primaryFixedDim = primaryFixedDim,
        onPrimaryFixed = onPrimaryFixed,
        onPrimaryFixedVariant = onPrimaryFixedVariant,
        secondaryFixed = secondaryFixed,
        secondaryFixedDim = secondaryFixedDim,
        onSecondaryFixed = onSecondaryFixed,
        onSecondaryFixedVariant = onSecondaryFixedVariant,
        tertiaryFixed = tertiaryFixed,
        tertiaryFixedDim = tertiaryFixedDim,
        onTertiaryFixed = onTertiaryFixed,
        onTertiaryFixedVariant = onTertiaryFixedVariant,
    )
}
