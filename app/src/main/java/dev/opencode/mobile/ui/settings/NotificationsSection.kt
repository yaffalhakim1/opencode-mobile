package dev.opencode.mobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions

/**
 * The notifications section.
 *
 * It says *why* notifications are off, not just that they are. There are four
 * independent causes — no topic configured, the in-app switch, the Android
 * permission, and the subscription failing to connect — and a single
 * "Notifications: off" leaves the user with no next step.
 */
@Composable
fun NotificationsSection(
    state: SettingsUiState,
    onToggle: (Boolean) -> Unit,
    onTopicChange: (String) -> Unit,
    onServerChange: (String) -> Unit,
    onSendTest: () -> Unit,
    onReconnect: () -> Unit,
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
                text = "Told when the agent is blocked or a turn fails. Keeps one quiet entry in the shade.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = state.notificationsEnabled,
            onCheckedChange = onToggle,
        )
    }

    OutlinedTextField(
        value = state.ntfyTopic,
        onValueChange = onTopicChange,
        label = { Text("Topic") },
        placeholder = { Text("opencode-…") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    )

    Text(
        text = "The topic is the password. Anyone who knows it can read these notifications and buzz this phone, so use a long random one.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )

    OutlinedTextField(
        value = state.ntfyServer,
        onValueChange = onServerChange,
        label = { Text("Server") },
        placeholder = { Text("https://ntfy.sh") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    )

    // Each blocker gets its own line, so the fix is obvious.
    if (!state.permissionGranted) {
        Notice("Android is blocking notifications for this app. Allow them in system settings, then come back.")
    } else if (state.ntfyTopic.isBlank()) {
        Notice("No topic yet. Paste the topic your OpenCode plugin publishes to.")
    } else if (!state.subscribed) {
        Notice("Not connected to the topic yet. Check the topic and the server, then reconnect.")
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
            onClick = onReconnect,
            enabled = state.ntfyTopic.isNotBlank(),
        ) {
            Text("Reconnect")
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
}

@Composable
private fun Notice(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .background(
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                MaterialTheme.shapes.medium,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
