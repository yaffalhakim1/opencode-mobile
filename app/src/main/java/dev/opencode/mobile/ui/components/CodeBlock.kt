package dev.opencode.mobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.ui.theme.CodeType
import dev.opencode.mobile.ui.theme.extendedColors
import kotlinx.coroutines.delay

/**
 * Collapsible monospace surface for tool output and shell results.
 *
 * Long output is clamped with an explicit "show all" affordance rather than an
 * inner scroll region: nested scrolling inside a `LazyColumn` traps gestures.
 * The copy action sits in the footer rather than floating over the text, so it
 * is reachable whether the block is clamped or expanded, and it never covers
 * the first characters of a line — which is exactly the part of code that
 * matters.
 */
@Composable
fun CodeBlock(
    text: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    tone: Color? = null,
    collapsedLines: Int = 12,
) {
    val lines = remember(text) { text.lines() }
    val isLong = lines.size > collapsedLines
    var expanded by remember(text) { mutableStateOf(false) }
    val visible = if (isLong && !expanded) lines.take(collapsedLines) else lines

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = tone ?: MaterialTheme.extendedColors.codeBackground,
                shape = MaterialTheme.shapes.small,
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.extendedColors.codeBorder,
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Text(
            text = visible.joinToString("\n"),
            style = CodeType.block,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // The whole block is copied, not the clamped part: what the user wants
        // is the code, and the "show N more lines" is about reading it.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLong) {
                Text(
                    text = if (expanded) "Show less" else "Show ${lines.size - collapsedLines} more lines",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded },
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            CopyAction(text = text)
        }
    }
}

/**
 * Copies a block to the clipboard and says so.
 *
 * The confirmation is a word rather than a toast: a toast at the bottom of the
 * screen is under the keyboard the user is about to type with, and "Copied"
 * next to the button that was tapped needs no interpretation.
 */
@Composable
private fun CopyAction(text: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(text) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (!copied) return@LaunchedEffect
        delay(COPIED_LABEL_MS)
        copied = false
    }

    Row(
        modifier = Modifier
            // A label is a small target; the padding makes the whole pill
            // tappable without making it taller than the block's footer.
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable {
                clipboard.setText(AnnotatedString(text))
                copied = true
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = if (copied) Glyphs.Check else Glyphs.Copy,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (copied) MaterialTheme.extendedColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (copied) "Copied" else "Copy",
            style = MaterialTheme.typography.labelSmall,
            color = if (copied) MaterialTheme.extendedColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** How long "Copied" stays on the button. */
private const val COPIED_LABEL_MS = 1_600L

/**
 * A one-line collapsible header, used by reasoning and tool rows.
 *
 * Header only by design: a second line of preview text makes a run of cards look
 * like a stack of paragraphs, and the body is one tap away anyway.
 */
@Composable
fun DisclosureRow(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "disclosure",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                // Tight vertical padding: a run of these cards should read as a
                // compact activity log, not a stack of panels.
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leading?.invoke()
            Text(
                text = title,
                // Normal weight, not bold: the header is a label on a row, and
                // bold made a stack of rows compete with the prose around it.
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
            Icon(
                imageVector = Glyphs.ChevronDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier
                    .size(16.dp)
                    .rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            // Top padding separates the header from the body; at 0 the first line
            // of reasoning sat directly against the header.
            Box(modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)) { content() }
        }
    }
}

/** Small pill used for tool status and metadata. */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.14f), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, androidx.compose.foundation.shape.CircleShape),
        )
        Spacer(Modifier.width(0.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}
