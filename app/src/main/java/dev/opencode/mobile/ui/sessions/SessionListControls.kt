package dev.opencode.mobile.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.ui.components.FilterPill
import dev.opencode.mobile.ui.components.Glyphs
import dev.opencode.mobile.ui.components.panel

/**
 * Search plus the two independent axes: which sessions survive (filter) and how
 * they are sliced (grouping).
 *
 * They share one scrolling strip rather than two stacked rows — two rows of chips
 * cost 80dp of a phone before the first session is visible — but they stay
 * separated by a hairline, because mixing them into one unbroken run makes
 * "Project" look like a fourth filter.
 */
@Composable
internal fun ListControls(
    state: SessionsUiState,
    onQueryChange: (String) -> Unit,
    onGrouping: (SessionGrouping) -> Unit,
    onFilter: (SessionFilter) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SessionFilter.entries.forEach { option ->
                FilterPill(
                    label = option.label,
                    selected = state.filter == option,
                    onClick = { onFilter(option) },
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            )

            SessionGrouping.entries.forEach { option ->
                FilterPill(
                    label = option.label,
                    selected = state.grouping == option,
                    onClick = { onGrouping(option) },
                )
            }
        }
    }
}

/**
 * A search field that is always there.
 *
 * It used to be a toolbar toggle that swapped itself for the field. The list is
 * the screen's only content and searching it is the main thing done to it, so the
 * field earns permanent space and the toggle earns nothing.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val keyboard = LocalSoftwareKeyboardController.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .panel(
                fill = scheme.surfaceContainerLowest,
                border = scheme.outline.copy(alpha = 0.4f),
                shape = shape,
            )
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Glyphs.Search,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = scheme.onSurfaceVariant.copy(alpha = 0.8f),
        )

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 12.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        text = "Search titles or projects",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                }
                inner()
            },
        )

        // Only when there is something to clear: a permanently visible X is a
        // button that does nothing for most of the screen's life.
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Glyphs.Close,
                    contentDescription = "Clear search",
                    modifier = Modifier.size(16.dp),
                    tint = scheme.onSurfaceVariant,
                )
            }
        }
    }
}
