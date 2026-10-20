package dev.opencode.mobile.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.opencode.mobile.R

/**
 * IBM Plex Mono, bundled as an asset.
 *
 * The platform monospace (`FontFamily.Monospace`) resolves to Droid Sans Mono on
 * most devices and to whatever the OEM chose on others, so code looked different
 * on every phone and none of them matched the desktop app. Bundling the three
 * weights actually used keeps code, inline spans and tool output identical
 * everywhere.
 */
internal val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)
