package dev.opencode.mobile.core.push

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.opencode.mobile.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Holds the ntfy subscription open so a message can arrive with the app closed.
 *
 * This is the whole cost of the in-app route: Android will not let a process
 * keep a socket alive in the background without a foreground service, and a
 * foreground service requires a visible notification. That ongoing entry in the
 * shade is what buys not installing a second app.
 *
 * **Type `specialUse`, not `dataSync`.** `dataSync` is capped at six hours in
 * any twenty-four on targetSdk 35+, after which the system kills the service —
 * it would go quiet every afternoon with no explanation. `specialUse` has no
 * such cap. It is also the honest label: this is not a file transfer.
 *
 * `START_STICKY` so the system brings it back after a low-memory kill. It is
 * started only when the user turns notifications on and the topic is set, and
 * stopped the moment either changes.
 */
class PushService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var subscription: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        // The type must be given at startForeground on API 34+, and a service
        // started without it throws MissingForegroundServiceTypeException.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, ONGOING_ID, ongoing(), type)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A tap on the ongoing notification opens the app, which is the only
        // action this service offers.
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        restartSubscription()
        return START_STICKY
    }

    /**
     * (Re)opens the subscription.
     *
     * Cancelled first, so a config change or a second `onStartCommand` cannot
     * leave two sockets open to the same topic — which would show every
     * notification twice.
     */
    private fun restartSubscription() {
        subscription?.cancel()
        subscription = scope.launch {
            val settings = PushPreferences(this@PushService)

            while (isActive) {
                val config = settings.ntfyConfig()
                if (config == null) {
                    // No topic yet, or the user turned it off. Nothing to do
                    // until something restarts the service.
                    logPush("no topic configured; stopping")
                    stopSelf()
                    return@launch
                }

                NtfySubscriber(config.server, config.topic).subscribe().collect { signal ->
                    when (signal) {
                        is NtfySignal.Connected -> logPush("subscribed to ${config.server}")
                        is NtfySignal.Disconnected -> logPush("subscription lost: ${signal.reason}")
                        is NtfySignal.Message -> deliver(signal.message)
                    }
                }

                if (!isActive) break
                // The subscription ended. Back off so a wrong topic or a dead
                // server does not become a tight reconnect loop.
                delay(RECONNECT_DELAY_MS)
            }
        }
    }

    private suspend fun deliver(message: dev.opencode.mobile.core.push.dto.NtfyMessageDto) {
        val settings = PushPreferences(this@PushService)
        if (!settings.enabled()) return

        val payload = NtfyPayload.from(message) ?: return
        PushNotifier(this@PushService).show(payload)
    }

    /**
     * The ongoing notification.
     *
     * Low importance on purpose: it is a status indicator, not news, and it
     * must not buzz. Tapping it opens the app.
     */
    private fun ongoing() = NotificationCompat.Builder(this, NotificationChannels.SERVICE)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("OpenCode Mobile")
        .setContentText("Watching for notifications")
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setOngoing(true)
        .setSilent(true)
        .setShowWhen(false)
        .build()

    override fun onDestroy() {
        subscription?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ONGOING_ID = 1
        private const val RECONNECT_DELAY_MS = 5_000L
        const val ACTION_STOP = "dev.opencode.mobile.push.STOP"

        /** Starts the service, ignoring the platform's refusal when it is not allowed. */
        fun start(context: Context) {
            val intent = Intent(context, PushService::class.java)
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { logPush("could not start the push service", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PushService::class.java))
        }
    }
}
