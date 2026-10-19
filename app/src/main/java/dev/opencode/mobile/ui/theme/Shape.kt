package dev.opencode.mobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The M3 Expressive ten-step corner scale.
 *
 * Values come from the published shape tokens; the three bold entries were added
 * by the May 2025 Expressive update. Nesting uses `inner = outer - padding`, so
 * a 28dp sheet holding 12dp-padded content gets 16dp children.
 */
internal val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
