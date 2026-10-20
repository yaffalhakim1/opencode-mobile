package dev.opencode.mobile.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.data.remote.dto.PermissionDecision
import dev.opencode.mobile.data.remote.dto.PermissionRequestDto
import dev.opencode.mobile.ui.theme.extendedColors

/**
 * A permission request the agent is blocked on.
 *
 * The turn cannot continue until this is answered, so it is rendered as a card
 * in the timeline rather than a toast: a missed prompt would look like a hung
 * session. The choices mirror the server's decisions — allow once, remember, or
 * deny — and "always" names the pattern it would remember, because that is the
 * part with lasting consequences.
 */
@Composable
fun PermissionCard(
    request: PermissionRequestDto,
    onDecide: (PermissionDecision) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                imageVector = Glyphs.Alert,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                text = "Needs permission",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        request.message?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // What is being asked, in monospace so a path reads as a path.
        Text(
            text = request.summary,
            style = dev.opencode.mobile.ui.theme.CodeType.inline,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.extendedColors.codeBackground,
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )

        if (request.save.isNotEmpty()) {
            Text(
                text = "Remembering this allows ${request.save.joinToString(", ")} in future.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { onDecide(PermissionDecision.Reject) }) {
                Text(
                    text = PermissionDecision.Reject.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = { onDecide(PermissionDecision.Once) }) {
                Text(PermissionDecision.Once.label, style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = { onDecide(PermissionDecision.Always) }) {
                Text(PermissionDecision.Always.label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
