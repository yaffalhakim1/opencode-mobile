package dev.opencode.mobile.core.push

import android.content.Context
import dev.opencode.mobile.data.settings.NtfyConfig
import dev.opencode.mobile.data.settings.SettingsStore
import kotlinx.coroutines.flow.first

/**
 * The push preferences, read where the caller cannot suspend.
 *
 * [SettingsStore] is the single source of truth; this is a thin reading of it
 * for the service, whose callbacks are not all suspend functions.
 */
class PushPreferences(private val context: Context) {

    private val settings = SettingsStore(context)

    suspend fun enabled(): Boolean = settings.notificationsEnabled.first()

    /** Where to subscribe, or null when push should not run. */
    suspend fun ntfyConfig(): NtfyConfig? = settings.ntfyConfig()
}
