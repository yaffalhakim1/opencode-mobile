package dev.opencode.mobile.core.push

import android.content.Context
import dev.opencode.mobile.core.net.HttpClientFactory
import dev.opencode.mobile.data.remote.OpenCodeApi
import dev.opencode.mobile.data.settings.ConnectionStore
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tells the OpenCode server which FCM token belongs to this device.
 *
 * This is the missing half of any push design: FCM can reach the phone, but the
 * server has no way to know *which* phone to reach. The app reports the token
 * once it is connected, and the server plugin reads it back.
 *
 * **No new endpoint is needed.** OpenCode already exposes
 * `POST /api/experimental/fs/write`, which writes any file on the server, and
 * the plugin reads that file. Verified against a live 2.0.32 server: it accepts
 * an absolute path outside the project, so the token lands in
 * `~/.config/opencode/push-device.json` where the plugin looks for it.
 *
 * It is deliberately best-effort. A server too old to have the endpoint, a phone
 * with no Firebase project configured, or a transient network failure must all
 * leave the app fully usable — push is an addition, not a dependency.
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
        logPush("refreshing the FCM token")
        return try {
            val token = fetchToken() ?: run {
                logPush("no FCM token available")
                return null
            }
            settings.saveToken(token)
            report(token)
            token
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            // A cancelled coroutine is the quietest failure of all: the timeout
            // never fires, nothing is written, and the only trace is that the
            // log simply stops. If this line appears, the caller's scope died
            // mid-request — which is what a recomposition or an activity
            // recreation does to a `LaunchedEffect`.
            logPush("token request cancelled before it finished")
            throw cancelled
        } catch (error: Throwable) {
            logPush("token request failed unexpectedly", error)
            null
        }
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
                .writeServerFile(PUSH_DEVICE_FILE, payload(token))
        }.onSuccess {
            settings.markReported(connection.baseUrl)
            logPush("token reported to ${connection.baseUrl}")
        }.onFailure {
            // Worth logging: a token that never reached the server means the
            // plugin has no device to send to, and the phone looks simply
            // "not notified".
            logPush("could not report the token to ${connection.baseUrl}", it)
        }
    }

    /**
     * What the plugin reads.
     *
     * A single device, not a list: the file is rewritten on every report, and
     * one install is one device. The plugin tolerates both shapes.
     */
    private fun payload(token: String): String = buildString {
        append('{')
        append("\"token\":\"").append(token.replace("\"", "")).append("\",")
        append("\"platform\":\"android\",")
        append("\"appVersion\":\"").append(appVersion().replace("\"", "")).append("\",")
        append("\"reportedAt\":").append(System.currentTimeMillis())
        append('}')
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
        if (FirebaseApp.getApps(context).isEmpty()) {
            logPush("no FirebaseApp: build has no google-services.json")
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val task = FirebaseMessaging.getInstance().token
                // `Tasks.await(task, timeout, unit)` bounds the wait **inside**
                // the Task API rather than relying on coroutine cancellation.
                //
                // That distinction matters: a plain `await(task)` blocks the
                // thread, and coroutine cancellation only happens at suspension
                // points, so `withTimeoutOrNull` cannot interrupt it. Two
                // attempts at that hung silently. This form always returns —
                // with a token, or with a TimeoutException.
                val token = com.google.android.gms.tasks.Tasks.await(
                    task,
                    TOKEN_TIMEOUT_MS,
                    java.util.concurrent.TimeUnit.MILLISECONDS,
                )
                if (token.isNullOrBlank()) {
                    logPush("FCM returned an empty token")
                    null
                } else {
                    logPush("got an FCM token (${token.length} chars)")
                    token
                }
            } catch (timeout: java.util.concurrent.TimeoutException) {
                logPush("no token after ${TOKEN_TIMEOUT_MS}ms", timeout)
                null
            } catch (error: Throwable) {
                logPush("the token request failed", error)
                null
            }
        }
    }

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    /** Whether this build can receive push at all, for the settings screen. */
    fun available(): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private companion object {
        /**
         * Where the plugin looks for the token.
         *
         * **Relative on purpose.** The app cannot know the server's home
         * directory, and `~` is not expanded by the endpoint — verified against
         * a live server, where it created a literal `~` folder inside the
         * project. A relative path resolves to the project directory on every
         * platform, and the plugin searches there first.
         */
        const val PUSH_DEVICE_FILE = ".opencode/push-device.json"

        /** How long to wait for Google Play Services to mint a token. */
        const val TOKEN_TIMEOUT_MS = 15_000L
    }
}
