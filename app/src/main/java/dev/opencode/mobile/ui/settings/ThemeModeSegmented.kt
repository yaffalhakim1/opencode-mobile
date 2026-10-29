package dev.opencode.mobile.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.data.settings.ThemeChoice

/** How a mode is named, once: the card's subtitle and the segments must agree. */
internal val ThemeChoice.label: String
    get() = when (this) {
        ThemeChoice.System -> "System"
        ThemeChoice.Light -> "Light"
        ThemeChoice.Dark -> "Dark"
    }

/** What picking the mode actually does, in the user's words. */
internal val ThemeChoice.subtitle: String
    get() = when (this) {
        ThemeChoice.System -> "Following the system setting"
        ThemeChoice.Light -> "Always light"
        ThemeChoice.Dark -> "Always dark"
    }

/**
 * Three modes in one track, the chosen one lifted out of it.
 *
 * A segmented control rather than three chips because the options are exclusive
 * and always all three: chips imply a set that can grow, and a row of three
 * separate outlines reads as three separate decisions.
 *
 * Only the fill and the ink animate. Sliding a thumb would mean measuring the
 * segments and moving a box between them, which is more machinery than a 120ms
 * colour change is worth on a screen this small.
 */
@Composable
internal fun ThemeModeSegmented(
    selected: ThemeChoice,
    dark: Boolean,
    onSelect: (ThemeChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val track = scheme.surfaceContainerHigh
    // The chosen segment has to look lifted in both modes: white on a light
    // track, a lighter grey on a dark one.
    val thumb = if (dark) scheme.surfaceContainerHighest else scheme.surfaceContainerLowest
    val segmentShape = RoundedCornerShape(8.dp)
    val motion = tween<Color>(
        durationMillis = 120,
        easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(track, MaterialTheme.shapes.medium)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ThemeChoice.entries.forEach { choice ->
            val isSelected = choice == selected
            val fill by animateColorAsState(
                targetValue = if (isSelected) thumb else Color.Transparent,
                animationSpec = motion,
                label = "segment-fill",
            )
            val ink by animateColorAsState(
                targetValue = if (isSelected) scheme.onSurface else scheme.onSurfaceVariant,
                animationSpec = motion,
                label = "segment-ink",
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(segmentShape)
                    .background(fill)
                    .clickable(role = Role.RadioButton) { onSelect(choice) }
                    .semantics { this.selected = isSelected }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = choice.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = ink,
                )
            }
        }
    }
}
