package dev.opencode.mobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opencode.mobile.core.net.HttpClientFactory
import dev.opencode.mobile.core.net.ServerConnection
import dev.opencode.mobile.data.settings.ThemeChoice
import dev.opencode.mobile.ui.components.Glyphs
import dev.opencode.mobile.ui.components.panel
import dev.opencode.mobile.ui.theme.CodeType
import dev.opencode.mobile.ui.theme.ThemeFamily
import dev.opencode.mobile.ui.theme.extendedColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onDisconnected: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Previews use the mode actually in effect, so a pinned dark theme does not
    // show its light set on a light phone.
    val dark = state.theme.isDark(isSystemInDarkTheme())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Glyphs.ArrowLeft, contentDescription = "Back")
                        }
                    },
                    title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
                // The bar and the page are the same colour, so a hairline is what
                // tells the two apart once the body scrolls under the title.
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ServerSection(connection = state.connection)

            AppearanceSection(
                theme = state.theme,
                family = state.family,
                dark = dark,
                onTheme = viewModel::setTheme,
                onFamily = viewModel::setFamily,
            )

            DisconnectRow(
                disconnecting = state.disconnecting,
                onClick = { viewModel.disconnect(onDisconnected) },
            )

            Text(
                text = "The server password is stored encrypted with a key held in this device's keystore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ServerSection(connection: ServerConnection?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsSectionLabel("Server")
        ServerCard(connection)
    }
}

/**
 * The address, and whether it is answering.
 *
 * Read-only on purpose: the address is not editable here because three things
 * are keyed to it (the cached HTTP client, the view-model keys and the open event
 * stream). Changing server means disconnecting, which clears all three at once.
 */
@Composable
private fun ServerCard(connection: ServerConnection?) {
    val scheme = MaterialTheme.colorScheme
    val connected = connection != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .panel(
                fill = scheme.surfaceContainerLow,
                border = scheme.outlineVariant.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.large,
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = connection?.baseUrl ?: "No server connected",
            style = CodeType.block,
            color = scheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // The dot is not the only signal: the line beside it says the same
            // thing in words, so the state survives a colour-blind reading.
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = if (connected) {
                            MaterialTheme.extendedColors.success
                        } else {
                            scheme.onSurfaceVariant
                        },
                        shape = CircleShape,
                    ),
            )
            Text(
                text = if (connected) {
                    "Authenticated as ${HttpClientFactory.USERNAME}"
                } else {
                    "Pair again to reconnect"
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppearanceSection(
    theme: ThemeChoice,
    family: ThemeFamily,
    dark: Boolean,
    onTheme: (ThemeChoice) -> Unit,
    onFamily: (ThemeFamily) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsSectionLabel("Appearance")

        ThemeCard(theme = theme, dark = dark, onTheme = onTheme)

        Spacer(Modifier.height(4.dp))

        // The palette is a separate choice from the mode: a theme supplies both a
        // light and a dark set, so picking one does not pin the other.
        SettingsHeading(
            title = "Palette",
            subtitle = "Colours for the whole app. Each is shown in the mode in effect.",
        )

        ThemeFamilyPicker(selected = family, dark = dark, onSelect = onFamily)
    }
}

@Composable
private fun ThemeCard(
    theme: ThemeChoice,
    dark: Boolean,
    onTheme: (ThemeChoice) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .panel(
                fill = scheme.surface,
                border = scheme.outlineVariant.copy(alpha = 0.8f),
                shape = MaterialTheme.shapes.large,
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SettingsHeading(title = "Theme", subtitle = theme.subtitle)
        ThemeModeSegmented(selected = theme, dark = dark, onSelect = onTheme)
    }
}

@Composable
private fun DisconnectRow(disconnecting: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .panel(
                fill = scheme.errorContainer.copy(alpha = 0.45f),
                border = scheme.error.copy(alpha = 0.25f),
                shape = MaterialTheme.shapes.large,
            )
            .clickable(enabled = !disconnecting, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Glyphs.Power,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = scheme.onErrorContainer,
        )
        Text(
            text = if (disconnecting) "Disconnecting…" else "Disconnect and forget password",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onErrorContainer,
        )
    }
}
