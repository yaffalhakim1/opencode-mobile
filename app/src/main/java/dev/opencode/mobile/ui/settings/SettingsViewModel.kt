package dev.opencode.mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opencode.mobile.core.net.ServerConnection
import dev.opencode.mobile.core.push.PushKind
import dev.opencode.mobile.core.push.PushMessage
import dev.opencode.mobile.core.push.PushNotifier
import dev.opencode.mobile.core.push.PushTokenReporter
import dev.opencode.mobile.data.settings.ConnectionStore
import dev.opencode.mobile.data.settings.SettingsStore
import dev.opencode.mobile.data.settings.ThemeChoice
import dev.opencode.mobile.ui.theme.ThemeFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val connection: ServerConnection? = null,
    val theme: ThemeChoice = ThemeChoice.System,
    val family: ThemeFamily = ThemeFamily.Default,
    val disconnecting: Boolean = false,
    val notificationsEnabled: Boolean = true,
    /** Whether this build was compiled with a Firebase project. */
    val pushAvailable: Boolean = false,
    /** The FCM token, shortened for display. */
    val pushTokenHint: String? = null,
    /** True when the current server acknowledged the token. */
    val pushReported: Boolean = false,
    /** Whether the system will actually show a notification. */
    val permissionGranted: Boolean = false,
    /** Result of the last "send test", shown under the button. */
    val testResult: String? = null,
)

class SettingsViewModel(
    private val store: ConnectionStore,
    private val settings: SettingsStore,
    private val pushNotifier: PushNotifier,
    private val pushTokens: PushTokenReporter,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(connection = store.current()) }
        }
        viewModelScope.launch {
            settings.theme.collect { theme ->
                _state.update { it.copy(theme = theme) }
            }
        }
        viewModelScope.launch {
            settings.family.collect { family ->
                _state.update { it.copy(family = family) }
            }
        }
        viewModelScope.launch {
            settings.notificationsEnabled.collect { enabled ->
                _state.update { it.copy(notificationsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settings.pushToken.collect { token ->
                _state.update { it.copy(pushTokenHint = token?.let(::shortToken)) }
            }
        }
        viewModelScope.launch {
            settings.pushTokenReportedTo.collect { server ->
                _state.update { it.copy(pushReported = server != null && server == it.connection?.baseUrl) }
            }
        }
        refreshPushAvailability()
    }

    /**
     * Everything the notifications section has to say about why it is not
     * working: no Firebase project, no permission, no token yet.
     *
     * Gathered in one place because "notifications don't work" has four
     * different causes and only one of them is the server's fault.
     */
    private fun refreshPushAvailability() {
        _state.update {
            it.copy(
                pushAvailable = pushTokens.available(),
                permissionGranted = pushNotifier.canPost(),
            )
        }
    }

    /** Called when the screen resumes, since permission can change in settings. */
    fun refreshPushState() {
        refreshPushAvailability()
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _state.update { it.copy(notificationsEnabled = enabled, testResult = null) }
        viewModelScope.launch {
            settings.setNotificationsEnabled(enabled)
            // Turning it on re-reports the token: the server may have been
            // restarted, or the token may have rotated while it was off.
            if (enabled) pushTokens.refreshAndReport()
        }
    }

    /**
     * Posts a local notification.
     *
     * Without this, "notifications don't work" is indistinguishable from a
     * denied permission, a missing token, or a server that cannot reach FCM.
     * This isolates the local half: if the test appears, the app is fine and
     * the problem is upstream.
     */
    fun sendTest() {
        val shown = pushNotifier.show(
            PushMessage(
                kind = PushKind.Permission,
                sessionID = "test",
                title = "Test notification",
                body = "The app can post notifications. If you see this, only delivery from the server is in question.",
                tag = "test",
            ),
        )
        _state.update {
            it.copy(
                testResult = if (shown) {
                    "Posted. Check your notification shade."
                } else {
                    "Blocked by the system. Allow notifications for this app in Android settings."
                },
            )
        }
    }

    /** Re-asks FCM for a token and reports it, for the retry button. */
    fun refreshToken() {
        _state.update { it.copy(testResult = "Requesting a token…") }
        viewModelScope.launch {
            val token = pushTokens.refreshAndReport()
            refreshPushAvailability()
            _state.update {
                it.copy(
                    testResult = if (token == null) {
                        "No token. This build has no Firebase configuration."
                    } else {
                        "Token reported to the server."
                    },
                )
            }
        }
    }

    /** Only the tail is shown: the full token is long and is not a secret to display. */
    private fun shortToken(token: String): String =
        if (token.length <= 12) token else "…${token.takeLast(12)}"

    /**
     * The theme the user picked.
     *
     * The choice is written straight away: it is a preference, and a
     * preference that only lives in memory is a preference that is lost the
     * moment the app is closed.
     */
    fun setTheme(choice: ThemeChoice) {
        _state.update { it.copy(theme = choice) }
        viewModelScope.launch { settings.setTheme(choice) }
    }

    /**
     * The palette the user picked.
     *
     * Applied to the screen immediately and written in the background: the whole
     * screen recolours as soon as the state changes, so waiting for the disk
     * would make the picker feel like it missed the tap.
     */
    fun setFamily(family: ThemeFamily) {
        _state.update { it.copy(family = family) }
        viewModelScope.launch { settings.setFamily(family) }
    }

    fun disconnect(onDone: () -> Unit) {
        if (_state.value.disconnecting) return
        _state.update { it.copy(disconnecting = true) }
        viewModelScope.launch {
            store.clear()
            _state.update { it.copy(disconnecting = false) }
            onDone()
        }
    }
}
