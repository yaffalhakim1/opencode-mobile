package dev.opencode.mobile.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.ui.chat.ChatSelection
import dev.opencode.mobile.ui.chat.PendingAttachment
import dev.opencode.mobile.ui.theme.extendedColors

/**
 * The prompt field.
 *
 * `adjustResize` on the activity plus `imePadding` at the scaffold level keeps
 * this above the keyboard. Enter sends and Shift+Enter is not available on a
 * phone keyboard, so the field stays multiline with an explicit send button
 * instead of relying on the IME action alone.
 */
@Composable
fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isRunning: Boolean,
    enabled: Boolean,
    selection: ChatSelection,
    selectionLabel: String,
    attachments: List<PendingAttachment>,
    onAttach: () -> Unit,
    onDetach: (PendingAttachment) -> Unit,
    onOpenSelector: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Ask OpenCode…",
    queued: Int = 0,
) {
    val canSend = enabled && value.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (queued > 0) {
            Text(
                text = if (queued == 1) {
                    "1 prompt queued — it runs when the agent is free"
                } else {
                    "$queued prompts queued — they run when the agent is free"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (attachments.isNotEmpty()) {
            AttachmentStrip(attachments = attachments, onDetach = onDetach)
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp, max = 160.dp),
                    enabled = enabled,
                    placeholder = {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = MaterialTheme.shapes.extraLarge,
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    keyboardActions = KeyboardActions(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        cursorColor = MaterialTheme.extendedColors.brand,
                    ),
                )
            }

            Box(contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = isRunning,
                    transitionSpec = {
                        (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith
                            (fadeOut() + scaleOut(targetScale = 0.8f))
                    },
                    label = "send-stop",
                ) { running ->
                    if (running) {
                        // Send stays available while the agent works: the prompt is
                        // queued, and Stop is the separate action.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledIconButton(
                                onClick = onSend,
                                enabled = canSend,
                                modifier = Modifier.size(52.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Icon(
                                    imageVector = Glyphs.Send,
                                    contentDescription = "Queue prompt",
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            FilledIconButton(
                                onClick = onStop,
                                modifier = Modifier.size(52.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                            ) {
                                Icon(
                                    imageVector = Glyphs.Stop,
                                    contentDescription = "Stop the agent",
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    } else {
                        FilledIconButton(
                            onClick = onSend,
                            enabled = canSend,
                            modifier = Modifier.size(52.dp),
                        ) {
                            Icon(
                                imageVector = Glyphs.Send,
                                contentDescription = "Send prompt",
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = onAttach,
                enabled = enabled && !isRunning,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Glyphs.Add,
                    contentDescription = "Attach a file",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // One control rather than three chips: agent, model and effort are a
            // single decision about "who answers this", and the sheet shows all
            // three together so the trade-offs are visible at once.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(10.dp),
                    )
                    .clickable(enabled = enabled && !isRunning, onClick = onOpenSelector)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Glyphs.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = selectionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (selection.variant != null) {
                Text(
                    text = selection.variant.replaceFirstChar(Char::uppercaseChar),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/** A horizontal strip of queued files, each dismissible. */
@Composable
private fun AttachmentStrip(
    attachments: List<PendingAttachment>,
    onDetach: (PendingAttachment) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEach { attachment ->
            Row(
                modifier = Modifier
                    .widthIn(max = 180.dp)
                    .background(
                        MaterialTheme.extendedColors.cardSurface,
                        RoundedCornerShape(10.dp),
                    )
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Glyphs.Paperclip,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                IconButton(
                    onClick = { onDetach(attachment) },
                    modifier = Modifier.size(22.dp),
                ) {
                    Icon(
                        imageVector = Glyphs.Close,
                        contentDescription = "Remove ${attachment.name}",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
