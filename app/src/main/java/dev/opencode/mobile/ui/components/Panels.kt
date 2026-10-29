package dev.opencode.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * A surface: a fill plus a hairline, and nothing else.
 *
 * The border is drawn inside the layout bounds, so a panel never grows by the
 * width of its own outline and two panels stacked in a column stay aligned.
 * Settings and the session list both use it, which is why it lives here rather
 * than in either screen.
 */
internal fun Modifier.panel(fill: Color, border: Color, shape: Shape): Modifier =
    background(fill, shape).border(1.dp, border, shape)

/**
 * A filter pill.
 *
 * Chosen reads as an ink chip — the palette's darkest surface with the page's
 * own background as ink — because a tinted selection is invisible on the four
 * palettes whose accent is already close to their surface.
 *
 * Not a 999dp capsule: the app has three radii (8 rows and chips, 16 panels), and
 * a pill that matches nothing else on the screen is decoration, not emphasis.
 */
@Composable
internal fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier = modifier
            .background(
                color = if (selected) scheme.onSurface else scheme.surfaceContainerHigh,
                shape = shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.surface else scheme.onSurfaceVariant,
        )
    }
}
