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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
 * An assistant turn.
 *
 * A turn arrives as several messages, one per step, and this renders them as a
 * single card: consecutive reasoning and tool blocks become rows separated by a
 * hairline, with prose between them. A run of ten tool calls used to be ten
 * bordered panels filling the screen; as one card it reads as the activity log
 * it actually is, and the prose stays the thing you read.
 */
@Composable
fun AssistantMessage(
    entries: List<ChatEntry.Assistant>,
    onToggleReasoning: (messageID: String, ordinal: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // One card for the whole run: rows carry the dividers.
    val rows = remember(entries) { entries.flatMap { it.blocks } }
    val anyStreaming = entries.any { it.isStreaming }

    Column(
        modifier = modifier.fillMaxWidth(),
        // 12dp between a card and the prose around it. At 2dp the card looked
        // glued to the paragraph; at 8dp it was still tight against a card that
        // follows prose. Dividers live *inside* the activity card only.
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (rows.isEmpty() && anyStreaming) {
            ThinkingRow()
        }

        val groups = remember(rows) { groupBlocks(rows) }
        groups.forEach { group ->
            when (group) {
                is BlockGroup.Text -> TextBlock(group.block)
                is BlockGroup.Activity -> ActivityCard(
                    blocks = group.blocks,
                    onToggleReasoning = onToggleReasoning,
                )
            }
        }

        entries.forEach { entry ->
            entry.errorText?.let { message -> ErrorRow(message) }
        }
    }
}

@Composable
private fun ErrorRow(message: String) {
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

/** One renderable run: prose, or a card of activity rows. */
private sealed interface BlockGroup {
    data class Text(val block: AssistantBlock.Text) : BlockGroup
    data class Activity(val blocks: List<AssistantBlock>) : BlockGroup
}

/**
 * Folds the block list into prose and activity runs.
 *
 * A `question` or `permission` block breaks the run: those are interactive and
 * get their own card, so folding them into an activity stack would bury the
 * thing that needs an answer.
 */
private fun groupBlocks(blocks: List<AssistantBlock>): List<BlockGroup> {
    val groups = mutableListOf<BlockGroup>()
    val activity = mutableListOf<AssistantBlock>()

    fun flush() {
        if (activity.isNotEmpty()) {
            groups += BlockGroup.Activity(activity.toList())
            activity.clear()
        }
    }

    blocks.forEach { block ->
        when (block) {
            is AssistantBlock.Text -> {
                flush()
                groups += BlockGroup.Text(block)
            }
            is AssistantBlock.Reasoning, is AssistantBlock.Tool -> activity += block
        }
    }
    flush()
    return groups
}

@Composable
private fun TextBlock(block: AssistantBlock.Text) {
    MarkdownText(
        markdown = block.text,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

/**
 * The single card a run of reasoning and tool calls collapses into.
 *
 * Rows are separated by a hairline instead of their own borders, which is what
 * keeps a long run short.
 */
@Composable
private fun ActivityCard(
    blocks: List<AssistantBlock>,
    onToggleReasoning: (String, Int) -> Unit,
) {
    AgentActivityCard {
        blocks.forEachIndexed { index, block ->
            if (index > 0) {
                // Faint on purpose: the divider is a hint that these rows are
                // separate, not a line the eye should stop on.
                HorizontalDivider(
                    color = MaterialTheme.extendedColors.cardBorder.copy(alpha = 0.45f),
                )
            }
            when (block) {
                is AssistantBlock.Reasoning -> ReasoningRow(block, onToggleReasoning)
                is AssistantBlock.Tool -> ToolRow(block)
                is AssistantBlock.Text -> Unit
            }
        }
    }
}

@Composable
private fun ReasoningRow(
    block: AssistantBlock.Reasoning,
    onToggle: (String, Int) -> Unit,
) {
    val label = if (block.streaming) "Thinking…" else "Thought process"
    DisclosureRow(
        title = label,
        expanded = block.expanded,
        onToggle = { onToggle(block.messageID, block.ordinal) },
        leading = {
            Icon(
                imageVector = Glyphs.Sparkle,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.tertiary,
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
