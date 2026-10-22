package dev.opencode.mobile.core.push

import android.content.Context
import dev.opencode.mobile.data.settings.SettingsStore
import kotlinx.coroutines.flow.first

/**
 * The push preferences, read synchronously where the caller cannot suspend.
 *
 * [SettingsStore] is the single source of truth; this is a thin reading of it
 * for `FirebaseMessagingService`, whose callbacks are not suspend functions.
 */
class PushPreferences(private val context: Context) {

    private val settings = SettingsStore(context)

    suspend fun enabled(): Boolean = settings.notificationsEnabled.first()

    suspend fun token(): String? = settings.pushToken.first()

    suspend fun saveToken(token: String) = settings.setPushToken(token)

    suspend fun reportedTo(): String? = settings.pushTokenReportedTo.first()

    suspend fun markReported(server: String) = settings.setPushTokenReportedTo(server)

    suspend fun clearReported() = settings.setPushTokenReportedTo(null)
}
