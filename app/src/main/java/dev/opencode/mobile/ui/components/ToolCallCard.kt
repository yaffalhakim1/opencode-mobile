package dev.opencode.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.data.chat.AssistantBlock
import dev.opencode.mobile.data.chat.ToolStatus
import dev.opencode.mobile.ui.theme.extendedColors

/**
 * One tool invocation, as a row inside the activity card.
 *
 * This is a row rather than a card of its own: consecutive calls are separated by
 * a hairline in `Messages.kt`, so a run of ten calls is one panel instead of ten.
 * The header still answers "what is the agent doing" without expanding; the body
 * carries arguments and result. Failed calls open automatically so an error is
 * never hidden behind a tap.
 */
@Composable
fun ToolRow(
    tool: AssistantBlock.Tool,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(tool.callId) {
        mutableStateOf(tool.status == ToolStatus.Failed)
    }

    val tint = when (tool.status) {
        ToolStatus.Streaming, ToolStatus.Running -> MaterialTheme.colorScheme.primary
        ToolStatus.Completed -> MaterialTheme.extendedColors.success
        ToolStatus.Failed -> MaterialTheme.colorScheme.error
    }

    DisclosureRow(
        title = tool.summaryLabel(),
        expanded = expanded,
        onToggle = { expanded = !expanded },
        leading = {
            if (tool.status == ToolStatus.Running || tool.status == ToolStatus.Streaming) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = tint,
                )
            } else {
                Icon(
                    imageVector = if (tool.status == ToolStatus.Failed) Glyphs.Alert else Glyphs.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = tint,
                )
            }
        },
        trailing = if (tool.status == ToolStatus.Failed) {
            { StatusPill(text = tool.status.label(), color = tint) }
        } else {
            null
        },
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tool.input?.let { CodeBlock(text = it, label = "Arguments", collapsedLines = 8) }
            tool.output?.let { CodeBlock(text = it, label = "Result", collapsedLines = 14) }
            tool.error?.let {
                CodeBlock(
                    text = it,
                    label = "Error",
                    tone = MaterialTheme.colorScheme.errorContainer,
                    collapsedLines = 20,
                )
            }
        }
    }
}

/**
 * What the row says at a glance.
 *
 * The reference design summarises a run ("Ran 1 command") rather than naming the
 * tool, because the tool name alone means nothing to someone reading the answer.
 * Unknown tools fall back to their name so nothing is ever blank.
 */
private fun AssistantBlock.Tool.summaryLabel(): String = when (name) {
    "shell", "bash" -> "Ran command"
    "read" -> "Read file"
    "write", "edit", "patch" -> "Edited file"
    "grep", "search" -> "Searched"
    "list", "glob" -> "Listed files"
    else -> name.ifBlank { "tool" }
}

private fun ToolStatus.label(): String = when (this) {
    ToolStatus.Streaming -> "queued"
    ToolStatus.Running -> "running"
    ToolStatus.Completed -> "done"
    ToolStatus.Failed -> "failed"
}
