package dev.opencode.mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opencode.mobile.core.net.ServerConnection
import dev.opencode.mobile.data.settings.ConnectionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val connection: ServerConnection? = null,
    val isDark: Boolean = false,
    val disconnecting: Boolean = false,
)

class SettingsViewModel(
    private val store: ConnectionStore,
    isDark: Boolean,
    private val onThemeOverride: (Boolean?) -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(isDark = isDark))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(connection = store.current()) }
        }
    }

    /** null follows the system; true/false pin the choice for this process. */
    fun setDarkMode(enabled: Boolean?) {
        onThemeOverride(enabled)
        if (enabled != null) _state.update { it.copy(isDark = enabled) }
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
