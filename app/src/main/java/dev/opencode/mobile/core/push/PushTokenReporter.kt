package dev.opencode.mobile.core.push

import android.content.Context
import dev.opencode.mobile.core.net.HttpClientFactory
import dev.opencode.mobile.data.remote.OpenCodeApi
import dev.opencode.mobile.data.settings.ConnectionStore
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Tells the OpenCode server which FCM token belongs to this device.
 *
 * This is the missing half of any push design: FCM can reach the phone, but the
 * server has no way to know *which* phone to reach. The app reports the token
 * once it is connected, and the server plugin reads it back.
 *
 * It is deliberately best-effort. A server that does not support the endpoint,
 * a phone with no Firebase project configured, or a transient network failure
 * must all leave the app fully usable — push is an addition, not a dependency.
 */
class PushTokenReporter(private val context: Context) {

    private val settings = PushPreferences(context)

    /**
     * Fetches the current token, remembers it, and reports it.
     *
     * Returns the token on success, or null when push is unavailable. Callers
     * treat null as "nothing to show", never as an error to surface.
     */
    suspend fun refreshAndReport(): String? {
        val token = fetchToken() ?: return null
        settings.saveToken(token)
        report(token)
        return token
    }

    /** Re-reports the remembered token, without asking FCM for a new one. */
    suspend fun report() {
        val token = settings.token() ?: fetchToken() ?: return
        settings.saveToken(token)
        report(token)
    }

    private suspend fun report(token: String) {
        val connection = ConnectionStore(context).current() ?: return

        // Already acknowledged by this server: a fresh token or a new pairing
        // is what makes this worth repeating.
        if (settings.reportedTo() == connection.baseUrl && settings.token() == token) return

        runCatching {
            OpenCodeApi(connection, HttpClientFactory.create(connection))
                .registerPushToken(token, platform = "android", appVersion = appVersion())
        }.onSuccess {
            settings.markReported(connection.baseUrl)
        }
    }

    /**
     * The current registration token, or null when push is not configured.
     *
     * `FirebaseMessaging.getInstance()` throws `IllegalStateException` when no
     * FirebaseApp was initialised — which is exactly the state of a build made
     * without `google-services.json`. That is a supported configuration, not a
     * crash.
     */
    private suspend fun fetchToken(): String? {
        if (FirebaseApp.getApps(context).isEmpty()) return null
        return runCatching {
            // Blocking, but this runs on an IO dispatcher and the alternative is
            // another dependency just to bridge one Task.
            com.google.android.gms.tasks.Tasks.await(FirebaseMessaging.getInstance().token)
        }.getOrNull()
    }

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    /** Whether this build can receive push at all, for the settings screen. */
    fun available(): Boolean = FirebaseApp.getApps(context).isNotEmpty()
}
