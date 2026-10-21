package dev.opencode.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.data.chat.AssistantBlock
import dev.opencode.mobile.data.chat.QuestionOption
import dev.opencode.mobile.data.chat.QuestionPrompt
import dev.opencode.mobile.data.chat.ToolStatus
import dev.opencode.mobile.ui.theme.extendedColors

/**
 * A question, drawn from the tool call that asked it.
 *
 * This reads the question off the `question` tool's own arguments rather than
 * waiting for a pending form. A tool call is always in the timeline, so the
 * question is visible even when no form is — which is exactly the case that used
 * to render it as raw JSON under an "Arguments" label.
 *
 * Answering still goes through the form, because that is the only endpoint the
 * server accepts an answer on. When no form is pending the card says so instead
 * of offering a button that cannot work.
 */
@Composable
fun QuestionPromptCard(
    tool: AssistantBlock.Tool,
    prompt: QuestionPrompt,
    /** Whether a pending form exists for this question, i.e. whether it can be answered here. */
    answerable: Boolean,
    onAnswer: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = remember(tool.callId) { mutableStateMapOf<String, Boolean>() }
    var submitting by remember(tool.callId) { mutableStateOf(false) }

    val chosen = prompt.options.filter { selected[it.label] == true }.map { it.label }
    val complete = answerable && !submitting && chosen.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.extendedColors.cardSurface, MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Glyphs.Sparkle,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                text = prompt.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // The question itself, at body size: it is the thing to read, not a
        // caption on the options.
        Text(
            text = prompt.question,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        prompt.options.forEach { option ->
            OptionRow(
                option = option,
                isSelected = selected[option.label] == true,
                // A single-choice question replaces the selection; a multiple
                // one accumulates it.
                onClick = {
                    if (prompt.multiple) {
                        selected[option.label] = selected[option.label] != true
                    } else {
                        selected.clear()
                        selected[option.label] = true
                    }
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!answerable) {
                Text(
                    text = when (tool.status) {
                        ToolStatus.Failed -> "This question failed and can no longer be answered."
                        else -> "Waiting for the server to open this question…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = if (prompt.multiple) "Choose any that apply" else "Choose one",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        submitting = true
                        onAnswer(chosen)
                    },
                    enabled = complete,
                ) {
                    Text("Answer", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    option: QuestionOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
                RoundedCornerShape(10.dp),
            )
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = option.label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        // The description explains the choice, which is the whole reason the
        // server sends one; dropping it left the labels ambiguous.
        if (option.description.isNotBlank()) {
            Text(
                text = option.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
