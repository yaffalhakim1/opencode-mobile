package dev.opencode.mobile.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.ui.markdown.MarkdownText
import dev.opencode.mobile.ui.markdown.NoticeSummary

/**
 * A system message from the server.
 *
 * The server injects these as markdown documents: a tool catalog, an injected
 * rule set, the list of available tools. They are reference material rather
 * than conversation, so a long one collapses to a single row labelled by the
 * document's own heading, and a short one shows as a quiet left-aligned note.
 *
 * The content is parsed as markdown either way. Showing the source — literal
 * `#`, `##` and `-` — turned a tool catalog into noise that was both ugly and
 * hard to read, and centring it turned it into noise that was hard to scan.
 */
@Composable
fun SystemNotice(
    text: String,
    modifier: Modifier = Modifier,
) {
    val summary = remember(text) { NoticeSummary.summarize(text) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    if (!summary.collapsed) {
        MarkdownText(
            markdown = text,
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }

    var expanded by remember(text) { mutableStateOf(false) }
    DisclosureRow(
        title = summary.label,
        expanded = expanded,
        onToggle = { expanded = !expanded },
        leading = {
            Icon(
                imageVector = Glyphs.Terminal,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = muted,
            )
        },
        trailing = {
            Text(
                text = "${summary.lineCount} lines",
                style = MaterialTheme.typography.labelSmall,
                color = muted,
            )
        },
    ) {
        MarkdownText(
            markdown = summary.body,
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
