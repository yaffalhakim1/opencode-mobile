package dev.opencode.mobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.data.remote.dto.FileEntryDto
import dev.opencode.mobile.data.remote.dto.SkillDto
import dev.opencode.mobile.ui.theme.extendedColors
/**
 * The list a mention picks from.
 *
 * It sits above the composer rather than over the screen, so the text being
 * written stays visible while the list is open. A path is shown the way the
 * user wrote it, with the directory it is in as the quieter second line, so
 * two files with the same name are still tellable apart.
 */
@Composable
internal fun MentionList(
    state: MentionState,
    onPick: (MentionSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.extendedColors.cardSurface, MaterialTheme.shapes.medium)
            // Bounded: the list is above the composer, and an unbounded one
            // would push the text field off the screen.
            .heightIn(max = 220.dp),
    ) {
        when {
            state.loading && state.isEmpty -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Searching…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.isEmpty -> Text(
                text = "Nothing matches",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )

            else -> {
                MentionHeader(headerFor(state))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    state.suggestions.forEach { suggestion ->
                        when (suggestion) {
                            is MentionSuggestion.File -> MentionRow(
                                label = suggestion.entry.path,
                                detail = suggestion.entry.path.substringBeforeLast('/', ""),
                                onClick = { onPick(suggestion) },
                            )

                            is MentionSuggestion.Entry -> MentionRow(
                                // The marker is part of the name, so the row
                                // reads as the thing to type, not as a label.
                                label = "/${suggestion.entry.name}",
                                detail = suggestion.entry.description,
                                onClick = { onPick(suggestion) },
                            )

                            is MentionSuggestion.Skill -> MentionRow(
                                label = "/${suggestion.skill.name}",
                                detail = suggestion.skill.description,
                                onClick = { onPick(suggestion) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** What the list is a list of. */
private fun headerFor(state: MentionState): String = when {
    state.suggestions.any { it is MentionSuggestion.File } -> "Files in this project"
    // A command and a skill are both run with the same key, so they are one
    // header rather than two lists that look alike.
    else -> "Commands and skills"
}

@Composable
private fun MentionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun MentionRow(label: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // A small dot, so the rows have a consistent left edge.
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
