package dev.opencode.mobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.ui.components.Glyphs
import dev.opencode.mobile.ui.theme.ThemeFamily

/**
 * The palette picker.
 *
 * Each row is painted in the theme it offers — its own surface, its own accent,
 * its own third hue — so the choice is made by looking at the theme rather than
 * by reading its name. A name alone would mean picking blind and finding out
 * after leaving the screen.
 *
 * The preview uses the mode currently in effect, so a theme is shown in the light
 * or dark set the user would actually get.
 */
@Composable
internal fun ThemeFamilyPicker(
    selected: ThemeFamily,
    dark: Boolean,
    onSelect: (ThemeFamily) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeFamily.entries.forEach { family ->
            ThemeFamilyRow(
                family = family,
                dark = dark,
                isSelected = family == selected,
                onClick = { onSelect(family) },
            )
        }
    }
}

@Composable
private fun ThemeFamilyRow(
    family: ThemeFamily,
    dark: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = family.scheme(dark)
    val shape = MaterialTheme.shapes.large

    // Opaque, not translucent: the swatch rings are painted in this exact colour
    // to read as a cut-out, and a see-through fill would leave a seam where the
    // ring and the row's own backdrop disagree.
    val fill = if (isSelected) {
        if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest
    } else {
        scheme.secondaryContainer
            .copy(alpha = 0.55f)
            .compositeOver(MaterialTheme.colorScheme.background)
    }
    val edge = if (isSelected) scheme.onSurface else scheme.primary.copy(alpha = 0.22f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(fill, shape)
            // Drawn inside the bounds, so a 2dp selected edge does not nudge the
            // row's contents by a pixel when the choice moves.
            .border(if (isSelected) 2.dp else 1.dp, edge, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { selected = isSelected }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ThemeSwatch(scheme = scheme, ring = fill)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = family.label,
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
            )
            Text(
                text = family.description,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Glyphs.Tick,
                // The row already reports itself as selected, so the tick is
                // decoration rather than a second announcement.
                contentDescription = null,
                tint = scheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The three colours that identify a theme at a glance.
 *
 * Surface, accent and third hue: what the app is mostly made of, what it signals
 * with, and the one place it paints something the user wrote. Three dots are
 * enough to tell Sand from Tide across the width of a phone.
 *
 * They overlap like a stack of coins — the first one on top, each with a ring in
 * the row's own colour so the group stays three shapes instead of one smear.
 */
@Composable
private fun ThemeSwatch(scheme: ColorScheme, ring: Color) {
    val dots = listOf(scheme.primary, scheme.primaryContainer, scheme.tertiaryContainer)
    val dot = 24.dp
    val overlap = 16.dp

    Box(modifier = Modifier.width(dot + overlap * (dots.size - 1)).height(dot)) {
        // Back to front: index 0 is drawn last, which puts it on top.
        for (index in dots.indices.reversed()) {
            Box(
                modifier = Modifier
                    .offset(x = overlap * index)
                    .size(dot)
                    .background(dots[index], CircleShape)
                    .border(2.dp, ring, CircleShape),
            )
        }
    }
}
