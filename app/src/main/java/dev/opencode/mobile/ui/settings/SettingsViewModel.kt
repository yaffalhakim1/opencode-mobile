package dev.opencode.mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opencode.mobile.core.net.ServerConnection
import dev.opencode.mobile.core.push.PushKind
import dev.opencode.mobile.core.push.PushMessage
import dev.opencode.mobile.core.push.PushNotifier
import dev.opencode.mobile.core.push.PushService
import dev.opencode.mobile.data.settings.ConnectionStore
import dev.opencode.mobile.data.settings.DEFAULT_NTFY_SERVER
import dev.opencode.mobile.data.settings.SettingsStore
import dev.opencode.mobile.data.settings.ThemeChoice
import dev.opencode.mobile.ui.theme.ThemeFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Long enough for the service to open a socket, short enough not to feel stuck. */
private const val SUBSCRIBE_GRACE_MS = 1_500L

data class SettingsUiState(
    val connection: ServerConnection? = null,
    val theme: ThemeChoice = ThemeChoice.System,
    val family: ThemeFamily = ThemeFamily.Default,
    val disconnecting: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val ntfyTopic: String = "",
    val ntfyServer: String = DEFAULT_NTFY_SERVER,
    /** Whether the system will actually show a notification. */
    val permissionGranted: Boolean = false,
    /** True while the foreground service holds a live subscription. */
    val subscribed: Boolean = false,
    /** Result of the last "send test", shown under the button. */
    val testResult: String? = null,
)

class SettingsViewModel(
    private val store: ConnectionStore,
    private val settings: SettingsStore,
    private val pushNotifier: PushNotifier,
    /** Application context, for starting and stopping the push service. */
    private val appContext: android.content.Context,
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
            settings.ntfyTopic.collect { topic ->
                _state.update { it.copy(ntfyTopic = topic.orEmpty()) }
            }
        }
        viewModelScope.launch {
            settings.ntfyServer.collect { server ->
                _state.update { it.copy(ntfyServer = server) }
            }
        }
        refreshPushState()
    }

    /** Called when the screen resumes, since permission can change in settings. */
    fun refreshPushState() {
        _state.update { it.copy(permissionGranted = pushNotifier.canPost()) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _state.update { it.copy(notificationsEnabled = enabled, testResult = null) }
        viewModelScope.launch {
            settings.setNotificationsEnabled(enabled)
            if (enabled) PushService.start(appContext) else PushService.stop(appContext)
        }
    }

    /**
     * The topic changed.
     *
     * Written on every keystroke and applied once the field settles: restarting
     * the subscription per character would open a socket for every prefix.
     */
    fun setTopic(topic: String) {
        _state.update { it.copy(ntfyTopic = topic) }
        viewModelScope.launch {
            settings.setNtfyTopic(topic)
            if (topic.isNotBlank()) restartService()
        }
    }

    fun setServer(server: String) {
        _state.update { it.copy(ntfyServer = server) }
        viewModelScope.launch {
            settings.setNtfyServer(server)
            if (server.isNotBlank()) restartService()
        }
    }

    /** Re-opens the subscription, for when the topic or server was wrong. */
    fun reconnect() {
        _state.update { it.copy(testResult = "Reconnecting…") }
        restartService()
        viewModelScope.launch {
            delay(SUBSCRIBE_GRACE_MS)
            _state.update {
                it.copy(testResult = "Reconnected. Send a test from the plugin, or wait for a turn to block.")
            }
        }
    }

    /**
     * Posts a local notification.
     *
     * Without this, "notifications don't work" is indistinguishable from a
     * denied permission, a wrong topic, or a dead server. This isolates the
     * local half: if the test appears, the app can notify and the problem is
     * the subscription.
     */
    fun sendTest() {
        val shown = pushNotifier.show(
            PushMessage(
                kind = PushKind.Permission,
                sessionID = "test",
                title = "Test notification",
                body = "The app can post notifications. If you see this, only delivery from the topic is in question.",
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

    private fun restartService() {
        PushService.stop(appContext)
        PushService.start(appContext)
    }

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
