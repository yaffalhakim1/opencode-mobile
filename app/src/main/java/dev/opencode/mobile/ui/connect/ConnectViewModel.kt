package dev.opencode.mobile.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opencode.mobile.core.net.HttpClientFactory
import dev.opencode.mobile.core.net.OpenCodeException
import dev.opencode.mobile.core.net.ServerConnection
import dev.opencode.mobile.data.remote.OpenCodeApi
import dev.opencode.mobile.data.remote.PairingClient
import dev.opencode.mobile.data.settings.ConnectionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How the user is signing in: a scanned/pasted pairing link, or URL + password. */
enum class ConnectMode { Pair, Manual }

data class ConnectUiState(
    val mode: ConnectMode = ConnectMode.Pair,
    val url: String = "",
    val password: String = "",
    val pairingLink: String = "",
    val isConnecting: Boolean = false,
    val error: String? = null,
    val connected: ServerConnection? = null,
    val serverVersion: String? = null,
    val restoring: Boolean = true,
    val scanning: Boolean = false,
) {
    val canConnectManually: Boolean
        get() = !isConnecting && url.isNotBlank() && password.isNotBlank()

    val canPair: Boolean
        get() = !isConnecting && pairingLink.isNotBlank()
}

class ConnectViewModel(
    private val store: ConnectionStore,
    private val pairing: PairingClient = PairingClient(),
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = store.current()
            _state.update { current ->
                if (saved == null) {
                    current.copy(restoring = false)
                } else {
                    current.copy(
                        url = saved.baseUrl,
                        password = saved.password,
                        mode = ConnectMode.Manual,
                        restoring = false,
                    )
                }
            }
        }
    }

    fun setMode(mode: ConnectMode) {
        _state.update { it.copy(mode = mode, error = null) }
    }

    fun onUrlChange(value: String) {
        _state.update { it.copy(url = value, error = null) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, error = null) }
    }

    fun onPairingLinkChange(value: String) {
        _state.update { it.copy(pairingLink = value, error = null) }
    }

    fun startScanning() {
        _state.update { it.copy(scanning = true, error = null) }
    }

    fun stopScanning() {
        _state.update { it.copy(scanning = false) }
    }

    /**
     * A scanned code is redeemed immediately — scanning should feel like one action.
     *
     * Guarded because a single frame can contain several copies of the same code:
     * the detector reports each one, and redeeming a single-use code more than
     * once turns a success into an "expired" error.
     */
    fun onScanned(value: String) {
        if (_state.value.isConnecting) return
        _state.update { it.copy(scanning = false, pairingLink = value) }
        pair()
    }

    fun connectManually() {
        val current = _state.value
        if (!current.canConnectManually) return
        _state.update { it.copy(isConnecting = true, error = null) }

        viewModelScope.launch {
            val connection = ServerConnection.from(current.url, current.password)
            verifyAndStore(connection)
        }
    }

    /**
     * Redeems the pairing link and keeps the returned token as the password.
     * The link itself is never persisted.
     */
    fun pair() {
        val current = _state.value
        if (!current.canPair) return
        _state.update { it.copy(isConnecting = true, error = null) }
        viewModelScope.launch {
            try {
                val result = pairing.redeem(current.pairingLink)
                val connection = ServerConnection(ServerConnection.normalize(result.baseUrl), result.token)
                verifyAndStore(connection)
            } catch (e: OpenCodeException) {
                _state.update { it.copy(isConnecting = false, error = e.message) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isConnecting = false, error = e.message ?: "Could not redeem that link.")
                }
            }
        }
    }

    private suspend fun verifyAndStore(connection: ServerConnection) {
        try {
            val info = OpenCodeApi(connection, HttpClientFactory.create(connection)).serverInfo()
            store.save(connection)
            _state.update {
                it.copy(
                    isConnecting = false,
                    connected = connection,
                    serverVersion = info.version,
                    error = null,
                )
            }
        } catch (e: OpenCodeException) {
            _state.update { it.copy(isConnecting = false, error = e.message) }
        } catch (e: Exception) {
            _state.update { it.copy(isConnecting = false, error = e.message ?: "Connection failed.") }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            store.clear()
            _state.update { ConnectUiState(restoring = false) }
        }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }

    /** Called after navigation to the session list so back does not re-trigger it. */
    fun consumeConnected() {
        _state.update { it.copy(connected = null) }
    }
}
