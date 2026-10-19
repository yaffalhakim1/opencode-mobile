package dev.opencode.mobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.data.chat.AssistantBlock
import dev.opencode.mobile.data.chat.ChatEntry
import dev.opencode.mobile.data.chat.NoticeKind
import dev.opencode.mobile.ui.markdown.MarkdownText
import dev.opencode.mobile.ui.theme.extendedColors

/**
 * A user turn. Right-aligned and tonally filled so the conversation's authorship
 * is readable at a glance without avatars competing for attention.
 */
@Composable
fun UserMessage(
    entry: ChatEntry.User,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(MaterialTheme.extendedColors.userBubble, MaterialTheme.shapes.large)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = entry.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.extendedColors.userBubbleText,
            )
        }
    }
}

/**
 * An assistant turn. Blocks stack in arrival order, so reasoning, tool calls and
 * prose interleave exactly as the agent produced them.
 */
@Composable
fun AssistantMessage(
    entry: ChatEntry.Assistant,
    onToggleReasoning: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (entry.blocks.isEmpty() && entry.isStreaming) {
            ThinkingRow()
        }

        entry.blocks.forEach { block ->
            when (block) {
                is AssistantBlock.Text -> TextBlock(block)
                is AssistantBlock.Reasoning -> ReasoningBlock(block, onToggleReasoning)
                is AssistantBlock.Tool -> ToolCallCard(block)
            }
        }

        entry.errorText?.let { message ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.shapes.medium,
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Glyphs.Alert,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun TextBlock(block: AssistantBlock.Text) {
    MarkdownText(
        markdown = block.text,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun ThinkingRow() {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val dot = MaterialTheme.colorScheme.primary
        repeat(3) { index ->
            val alpha by animateColorAsState(
                targetValue = if (index == 0) dot else dot.copy(alpha = 0.35f),
                label = "thinking-dot-$index",
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(alpha, CircleShape),
            )
        }
        Text(
            text = "Working",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Reasoning is collapsed by default: it is useful when debugging an agent run and
 * noise when you just want the answer.
 *
 * It shares the tool card's surface and header so a run reads as one stack of
 * cards rather than two competing visual languages.
 */
@Composable
private fun ReasoningBlock(
    block: AssistantBlock.Reasoning,
    onToggle: (Int) -> Unit,
) {
    val label = if (block.streaming) "Thinking…" else "Thought process"
    AgentActivityCard {
        DisclosureRow(
            title = label,
            expanded = block.expanded,
            onToggle = { onToggle(block.ordinal) },
            leading = {
                Icon(
                    imageVector = Glyphs.Sparkle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            },
            trailing = {
                StatusPill(
                    text = if (block.streaming) "thinking" else "thought",
                    color = MaterialTheme.colorScheme.tertiary,
                )
            },
        ) {
            MarkdownText(
                markdown = block.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Quiet, centered rows for system notes, shell commands and compaction. */
@Composable
fun NoticeMessage(
    entry: ChatEntry.Notice,
    modifier: Modifier = Modifier,
) {
    val (label, tint) = when (entry.kind) {
        NoticeKind.System -> null to MaterialTheme.colorScheme.onSurfaceVariant
        NoticeKind.Skill -> "Skill" to MaterialTheme.colorScheme.tertiary
        NoticeKind.Shell -> "Shell" to MaterialTheme.colorScheme.secondary
        NoticeKind.Compaction -> "Context" to MaterialTheme.colorScheme.secondary
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (label != null) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                textAlign = TextAlign.Center,
            )
        }
        if (entry.kind == NoticeKind.Shell) {
            CodeBlock(text = entry.text, label = null)
        } else {
            Text(
                text = entry.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}
