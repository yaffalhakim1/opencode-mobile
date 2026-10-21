package dev.opencode.mobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.data.chat.QueuedPrompt
import dev.opencode.mobile.ui.components.Glyphs
import dev.opencode.mobile.ui.theme.extendedColors

/**
 * A prompt that is waiting to be delivered.
 *
 * It is the user's own message, so it is shown as one: the text they wrote,
 * where it is in the queue, and what they can do about it. A count of "2
 * prompts queued" told them nothing they could act on.
 *
 * The two actions are the two things a waiting message can become: withdrawn,
 * or pushed into the turn that is running.
 */
@Composable
internal fun QueuedPromptRow(
    prompt: QueuedPrompt,
    onSteer: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    MaterialTheme.extendedColors.userBubble.copy(alpha = 0.55f),
                    MaterialTheme.shapes.large,
                )
                .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = prompt.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.extendedColors.userBubbleText,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = when {
                        prompt.steering -> "Steering — runs at the next step"
                        prompt.attachments > 0 -> "Queued · ${prompt.attachments} file(s)"
                        else -> "Queued — waits for this turn"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.extendedColors.userBubbleText.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )

                // Steering is the point of showing this row at all: it is how a
                // correction reaches a turn that is already running.
                if (!prompt.steering) {
                    IconButton(onClick = onSteer, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Glyphs.Send,
                            contentDescription = "Send now, without waiting",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.extendedColors.userBubbleText,
                        )
                    }
                }

                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Glyphs.Close,
                        contentDescription = "Withdraw this message",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.extendedColors.userBubbleText.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}
