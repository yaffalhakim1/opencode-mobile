package dev.opencode.mobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A green-signal palette for a terminal-native tool.
 *
 * The brand green is a signal colour, not a surface colour: it marks the logo,
 * the caret, and anything that is live. Structure is carried by a near-neutral
 * ramp with a faint green bias so the signal reads as part of the same family
 * instead of a sticker on top of it.
 *
 * `primary` is deliberately darker than the brand green in light mode. The
 * brand green is far too light to carry body text on a pale surface, so the
 * roles split: `primary` is the accessible action colour, `brand` is the mark.
 */
internal object Palette {

    /** The two brand greens, used for the mark, the caret and live pulses. */
    val BrandDark = Color(0xFF26E085)
    val BrandLight = Color(0xFF00DA7D)

    // Dark — the default. Near-black with a green bias; easier on the eyes than
    // pure #000 and close to how a terminal presents.
    val DarkPrimary = Color(0xFF26E085)
    val DarkOnPrimary = Color(0xFF00391F)
    val DarkPrimaryContainer = Color(0xFF00522F)
    val DarkOnPrimaryContainer = Color(0xFF6DFFB2)

    val DarkSecondary = Color(0xFFB4CCC0)
    val DarkOnSecondary = Color(0xFF20342A)
    val DarkSecondaryContainer = Color(0xFF364A40)
    val DarkOnSecondaryContainer = Color(0xFFD0E8DB)

    val DarkTertiary = Color(0xFF86D3E8)
    val DarkOnTertiary = Color(0xFF003640)
    val DarkTertiaryContainer = Color(0xFF004E5C)
    val DarkOnTertiaryContainer = Color(0xFFB4EBFF)

    val DarkError = Color(0xFFFFB4AB)
    val DarkOnError = Color(0xFF690005)
    val DarkErrorContainer = Color(0xFF93000A)
    val DarkOnErrorContainer = Color(0xFFFFDAD6)

    val DarkBackground = Color(0xFF0B0F0C)
    val DarkOnBackground = Color(0xFFDFE5E0)
    val DarkSurface = Color(0xFF0B0F0C)
    val DarkOnSurface = Color(0xFFDFE5E0)
    val DarkSurfaceVariant = Color(0xFF2A332D)
    val DarkOnSurfaceVariant = Color(0xFFB4C2B8)
    val DarkOutline = Color(0xFF83938A)
    val DarkOutlineVariant = Color(0xFF3B463F)

    // Light — a cool off-white rather than cream, so green stays clean.
    val LightPrimary = Color(0xFF00794A)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightPrimaryContainer = Color(0xFF9BF6C6)
    val LightOnPrimaryContainer = Color(0xFF00210F)

    val LightSecondary = Color(0xFF4B6355)
    val LightOnSecondary = Color(0xFFFFFFFF)
    val LightSecondaryContainer = Color(0xFFCEE9D9)
    val LightOnSecondaryContainer = Color(0xFF082015)

    val LightTertiary = Color(0xFF0F6B7D)
    val LightOnTertiary = Color(0xFFFFFFFF)
    val LightTertiaryContainer = Color(0xFFB4EBFF)
    val LightOnTertiaryContainer = Color(0xFF001F26)

    val LightError = Color(0xFFB3261E)
    val LightOnError = Color(0xFFFFFFFF)
    val LightErrorContainer = Color(0xFFF9DEDC)
    val LightOnErrorContainer = Color(0xFF410E0B)

    val LightBackground = Color(0xFFF7FBF8)
    val LightOnBackground = Color(0xFF171D19)
    val LightSurface = Color(0xFFF7FBF8)
    val LightOnSurface = Color(0xFF171D19)
    val LightSurfaceVariant = Color(0xFFDCE5DE)
    val LightOnSurfaceVariant = Color(0xFF404943)
    val LightOutline = Color(0xFF707973)
    val LightOutlineVariant = Color(0xFFC0C9C2)
}
