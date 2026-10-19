package dev.opencode.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
import dev.opencode.mobile.data.chat.AssistantBlock
import dev.opencode.mobile.data.chat.ToolStatus
import dev.opencode.mobile.ui.theme.extendedColors

/**
 * One tool invocation.
 *
 * The header always answers "what is the agent doing" without expanding; the body
 * carries arguments and result. Failed calls stay expanded so an error is never
 * hidden behind a tap.
 */
@Composable
fun ToolCallCard(
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

    AgentActivityCard(modifier = modifier) {
        DisclosureRow(
            title = tool.name.ifBlank { "tool" },
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
                        imageVector = if (tool.status == ToolStatus.Failed) Glyphs.Alert else Glyphs.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = tint,
                    )
                }
            },
            trailing = {
                StatusPill(text = tool.status.label(), color = tint)
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
}

private fun ToolStatus.label(): String = when (this) {
    ToolStatus.Streaming -> "queued"
    ToolStatus.Running -> "running"
    ToolStatus.Completed -> "done"
    ToolStatus.Failed -> "failed"
}
