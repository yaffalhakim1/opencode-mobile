package dev.opencode.mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opencode.mobile.core.net.ServerConnection
import dev.opencode.mobile.data.settings.ConnectionStore
import dev.opencode.mobile.data.settings.SettingsStore
import dev.opencode.mobile.data.settings.ThemeChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val connection: ServerConnection? = null,
    val theme: ThemeChoice = ThemeChoice.System,
    val disconnecting: Boolean = false,
)

class SettingsViewModel(
    private val store: ConnectionStore,
    private val settings: SettingsStore,
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
