package dev.opencode.mobile.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.ui.components.Glyphs
@Composable
internal fun ListControls(
    state: SessionsUiState,
    searchOpen: Boolean,
    onQueryChange: (String) -> Unit,
    onGrouping: (SessionGrouping) -> Unit,
    onFilter: (SessionFilter) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (searchOpen) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search title or project") },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                leadingIcon = {
                    Icon(
                        imageVector = Glyphs.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Group",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            SessionGrouping.entries.forEach { option ->
                FilterChip(
                    selected = state.grouping == option,
                    onClick = { onGrouping(option) },
                    label = { Text(option.label, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Show",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            SessionFilter.entries.forEach { option ->
                FilterChip(
                    selected = state.filter == option,
                    onClick = { onFilter(option) },
                    label = { Text(option.label, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
    }
}

/**
 * Walks to the next page of sessions.
 *
 * The server caps a page, so the sessions beyond it are only reachable by
 * following the cursor. This is at the end of the list rather than automatic,
 * because a list that keeps growing while it is being read is never a list
 * anyone can find anything in.
 */