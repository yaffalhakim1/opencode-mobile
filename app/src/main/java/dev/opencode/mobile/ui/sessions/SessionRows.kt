package dev.opencode.mobile.ui.sessions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.data.remote.dto.SessionDto
import dev.opencode.mobile.ui.components.Glyphs
import dev.opencode.mobile.ui.components.panel
import dev.opencode.mobile.ui.theme.CodeType
import dev.opencode.mobile.ui.theme.extendedColors

@Composable
internal fun LoadMoreRow(loading: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (loading) "Loading…" else "Load older sessions",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Shown when the event feed is down, so a stale list is not mistaken for a
 * quiet one.
 */
@Composable
internal fun LiveUpdatesPausedBanner(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .panel(
                fill = MaterialTheme.colorScheme.secondaryContainer,
                border = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.large,
            )
            .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Live updates paused",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRetry) {
            Icon(
                imageVector = Glyphs.Refresh,
                contentDescription = "Reload the list",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
internal fun SeeMoreRow(hidden: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "See $hidden more",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            imageVector = Glyphs.ChevronDown,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

/**
 * One session.
 *
 * Tap opens it, long-press pins it. Pinning has no glyph of its own until it is
 * on, which is why a pinned row carries a pin next to its title: the state has to
 * be visible somewhere, and a permanent pin button on every row would be a second
 * target competing with the delete X.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SessionRow(
    session: SessionDto,
    running: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .panel(
                fill = scheme.surfaceContainerHigh,
                border = scheme.outlineVariant.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.large,
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    // The press has to be felt, because the result is a state
                    // change in a list that may be off-screen behind the finger.
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTogglePin()
                },
            )
            .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (pinned) {
                    Icon(
                        imageVector = Glyphs.Pin,
                        // Long-press is the gesture, not something a screen
                        // reader can perform, so the row states the fact.
                        contentDescription = "Pinned",
                        modifier = Modifier.size(13.dp),
                        tint = scheme.onSurface,
                    )
                } else if (running) {
                    // A live session is the one state worth colouring.
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(MaterialTheme.extendedColors.brand, CircleShape),
                    )
                }
                Text(
                    text = session.title?.takeIf { it.isNotBlank() } ?: "Untitled session",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = scheme.onSurface,
                    // One line: a wrapped title is what made rows tall.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // No folder icon: the project name already says where it is, and
                // the glyph on every row was visual noise.
                session.location?.displayName?.takeIf { it.isNotBlank() }?.let { folder ->
                    Text(
                        text = folder,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Dot()
                }
                Text(
                    text = relativeTime(session.time.updated ?: session.time.created),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (session.cost > 0.0) {
                    Dot()
                    // Monospace: money is a number, and a column of proportional
                    // digits does not line up.
                    Text(
                        text = "$${"%.2f".format(session.cost)}",
                        style = CodeType.meta,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Glyphs.Close,
                contentDescription = "Delete session",
                modifier = Modifier.size(15.dp),
                tint = scheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
internal fun Dot() {
    Box(
        modifier = Modifier
            .size(3.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
    )
}

/** Server timestamps are epoch milliseconds. */
internal fun relativeTime(epochMillis: Double): String {
    if (epochMillis <= 0.0) return "just now"
    val millis = epochMillis.toLong()
    val delta = System.currentTimeMillis() - millis
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        delta < minute -> "just now"
        delta < hour -> "${delta / minute}m ago"
        delta < day -> "${delta / hour}h ago"
        delta < 7 * day -> "${delta / day}d ago"
        else -> "${delta / (7 * day)}w ago"
    }
}
