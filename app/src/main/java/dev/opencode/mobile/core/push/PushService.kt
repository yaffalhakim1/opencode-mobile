package dev.opencode.mobile.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives push messages from FCM.
 *
 * Android starts this service itself when a message arrives; nothing in the app
 * calls it. `onMessageReceived` is only invoked for **data** messages, so the
 * server must send `data` and not a `notification` block — with a notification
 * block Android would draw the notification itself and this code would never
 * run, which would lose the deep link and the per-session grouping.
 *
 * The work is small enough to do inline: parse, check the preference, post. The
 * 10-second budget a message handler gets is not a constraint here, and a
 * WorkManager hop would only add latency to the case that matters most — being
 * told the agent is blocked.
 */
class PushService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = PushMessage.fromData(message.data) ?: return

        // A refresh message is not a notification: it is how a server asks the
        // device to re-report a token it may have missed.
        if (message.data[ACTION_KEY] == ACTION_REFRESH_TOKEN) {
            scope.launch { PushTokenReporter(this@PushService).report() }
            return
        }

        // The user turned notifications off in the app. Checked here rather than
        // only at registration so it takes effect immediately.
        scope.launch {
            if (!PushPreferences(this@PushService).enabled()) return@launch
            NotificationChannels.create(this@PushService)
            PushNotifier(this@PushService).show(payload)
        }
    }

    /**
     * A new registration token was minted.
     *
     * This is the path that keeps delivery working after the old token is
     * retired — the case that otherwise looks like "notifications just stopped"
     * with nothing to point at.
     */
    override fun onNewToken(token: String) {
        scope.launch {
            val settings = PushPreferences(this@PushService)
            settings.saveToken(token)
            // A new token has not been reported to this server, so the old
            // acknowledgement is stale.
            settings.clearReported()
            PushTokenReporter(this@PushService).report()
        }
    }

    /**
     * Never crash on a push.
     *
     * A build made without `google-services.json` has no FirebaseApp, and the
     * SDK can throw while initialising itself. Push is an addition to this app,
     * so a broken push path must not take the process down with it — the user
     * would see a crash from an app they were not even using.
     */
    override fun handleIntent(intent: Intent?) {
        try {
            super.handleIntent(intent)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Ignoring a push that could not be handled", error)
        }
    }

    private companion object {
        const val TAG = "PushService"
        const val ACTION_KEY = "action"
        const val ACTION_REFRESH_TOKEN = "refresh-token"
    }
}
