package dev.opencode.mobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.ui.theme.CodeType

/**
 * The notifications section.
 *
 * It says *why* notifications are off, not just that they are. There are four
 * independent causes — no Firebase configuration in this build, the in-app
 * switch, the Android permission, and the server never acknowledging the token
 * — and a single "Notifications: off" leaves the user with no next step.
 */
@Composable
fun NotificationsSection(
    state: SettingsUiState,
    onToggle: (Boolean) -> Unit,
    onSendTest: () -> Unit,
    onRefreshToken: () -> Unit,
) {
    Text(
        text = "NOTIFICATIONS",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Push notifications", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Told when the agent is blocked or a turn fails.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = state.notificationsEnabled,
            onCheckedChange = onToggle,
            enabled = state.pushAvailable,
        )
    }

    // Each blocker gets its own line, so the fix is obvious.
    if (!state.pushAvailable) {
        Notice("This build has no Firebase configuration, so it cannot receive push. Rebuild with google-services.json in app/.")
    } else if (!state.permissionGranted) {
        Notice("Android is blocking notifications for this app. Allow them in system settings, then come back.")
    } else if (!state.pushReported) {
        Notice("The server has not acknowledged this device yet. Open a session, or refresh the token below.")
    }

    if (state.pushTokenHint != null) {
        Text(
            text = "Device token ${state.pushTokenHint}",
            style = CodeType.block,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onSendTest,
            enabled = state.permissionGranted,
        ) {
            Text("Send test")
        }
        OutlinedButton(
            onClick = onRefreshToken,
            enabled = state.pushAvailable,
        ) {
            Text("Refresh token")
        }
    }

    if (state.testResult != null) {
        Text(
            text = state.testResult,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Notice(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                MaterialTheme.shapes.medium,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(2.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
