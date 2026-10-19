package dev.opencode.mobile.ui.connect

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opencode.mobile.ui.components.Glyphs
import dev.opencode.mobile.ui.theme.CodeType
import dev.opencode.mobile.ui.theme.extendedColors

/**
 * Entry screen.
 *
 * Pairing is the default because it is the only flow that works through a
 * tunnel: scanning the code hands back the right host *and* a token, so nothing
 * has to be retyped. The manual form stays one tap away for `opencode serve` on
 * a LAN, which needs no pairing at all.
 */
@Composable
fun ConnectScreen(
    viewModel: ConnectViewModel,
    onConnected: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.connected) {
        if (state.connected != null) {
            viewModel.consumeConnected()
            onConnected()
        }
    }

    if (state.scanning) {
        QrScanner(
            onResult = viewModel::onScanned,
            onCancel = viewModel::stopScanning,
        )
        return
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))

            Icon(
                imageVector = Glyphs.Terminal,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.extendedColors.brand,
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Connect to your server",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "OpenCode runs on your machine. Point this app at it — nothing leaves your network.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))

            ModeChoice(mode = state.mode, onSelect = viewModel::setMode)

            Spacer(Modifier.height(24.dp))

            when (state.mode) {
                ConnectMode.Pair -> PairForm(
                    link = state.pairingLink,
                    onLinkChange = viewModel::onPairingLinkChange,
                    onScan = viewModel::startScanning,
                    onPair = viewModel::pair,
                    canPair = state.canPair,
                    connecting = state.isConnecting,
                )

                ConnectMode.Manual -> ManualForm(
                    url = state.url,
                    password = state.password,
                    onUrlChange = viewModel::onUrlChange,
                    onPasswordChange = viewModel::onPasswordChange,
                    onConnect = viewModel::connectManually,
                    canConnect = state.canConnectManually,
                    connecting = state.isConnecting,
                )
            }

            AnimatedVisibility(
                visible = state.error != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text = state.error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
            }

            Spacer(Modifier.height(32.dp))

            ServerHint(mode = state.mode)

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun ModeChoice(mode: ConnectMode, onSelect: (ConnectMode) -> Unit) {
    val options = listOf(ConnectMode.Pair to "Scan QR", ConnectMode.Manual to "URL + password")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PairForm(
    link: String,
    onLinkChange: (String) -> Unit,
    onScan: () -> Unit,
    onPair: () -> Unit,
    canPair: Boolean,
    connecting: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onScan,
            enabled = !connecting,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(Glyphs.QrCode, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
            Text("Scan the pairing code", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Or paste the link",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = link,
            onValueChange = onLinkChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://host/auth/connect/abc123", style = CodeType.inline) },
            textStyle = CodeType.inline,
            minLines = 2,
            maxLines = 3,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
            enabled = !connecting,
        )

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onPair,
            enabled = canPair,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            if (connecting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Pair and connect", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ManualForm(
    url: String,
    password: String,
    onUrlChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit,
    canConnect: Boolean,
    connecting: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Server URL") },
            placeholder = { Text("http://192.168.1.20:4096") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
            ),
            enabled = !connecting,
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            placeholder = { Text("Printed by opencode serve") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            enabled = !connecting,
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onConnect,
            enabled = canConnect,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            if (connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Connect", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ServerHint(mode: ConnectMode) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (mode == ConnectMode.Pair) "On your computer" else "Plain server, no tunnel",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (mode == ConnectMode.Pair) {
                "Run `opencode tunnel` (or share from the desktop app) and scan the QR it shows."
            } else {
                "opencode serve --hostname 0.0.0.0 --port 4096"
            },
            style = if (mode == ConnectMode.Pair) {
                MaterialTheme.typography.bodySmall
            } else {
                CodeType.block
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            Text(
                text = if (mode == ConnectMode.Pair) {
                    "The code is single-use and expires in a few minutes."
                } else {
                    "Paste the printed password above. For access from anywhere, pair through a tunnel instead."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
}
