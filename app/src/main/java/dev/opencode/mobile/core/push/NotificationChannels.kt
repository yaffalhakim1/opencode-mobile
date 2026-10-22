package dev.opencode.mobile.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * The app's notification channels.
 *
 * Two, because the two cases deserve different interruption. A permission
 * blocks the agent and the user can unblock it from the notification's target
 * screen, so it is High. A failure is news and nothing is blocked, so it is
 * Default.
 *
 * The ids are the contract with [PushNotifier]; the labels are the only part
 * the user sees, and Android keeps whatever was set when the channel was first
 * created — changing a label here does not rename it on an existing install.
 */
object NotificationChannels {

    /** The agent cannot continue. */
    const val BLOCKED = "blocked"

    /** A turn ended in an error. */
    const val FAILURES = "failures"

    /** The channel for a message, by what it is about. */
    fun forKind(kind: PushKind): String = when (kind) {
        PushKind.Permission, PushKind.Question -> BLOCKED
        PushKind.Failure -> FAILURES
    }

    /**
     * Creates both channels. Idempotent, so it is safe to call on every start
     * and cheap enough not to guard.
     */
    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                BLOCKED,
                "Blocked agent",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "A permission or a question is stopping the agent."
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                FAILURES,
                "Turn failures",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "A turn ended with an error."
            },
        )
    }
}
