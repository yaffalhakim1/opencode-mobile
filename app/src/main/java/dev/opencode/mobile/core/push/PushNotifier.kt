package dev.opencode.mobile.core.push

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dev.opencode.mobile.R
import dev.opencode.mobile.core.deeplink.DeepLink

/**
 * Posts the app's own notifications.
 *
 * The tap target is a deep link into the chat, built with
 * [DeepLink.forSession] — the same link the ntfy app would follow, so both
 * transports land in the same place and `MainActivity` needs no second path.
 *
 * Every notification is posted under a per-session tag, so several about one
 * chat replace each other instead of stacking up. A permission that was
 * answered on the desktop therefore leaves one stale entry at worst, not five.
 */
class PushNotifier(private val context: Context) {

    /**
     * Shows one notification.
     *
     * Returns false when it was not shown, which is the caller's cue to stop
     * trying rather than a failure: on Android 13+ the user can deny
     * `POST_NOTIFICATIONS`, and a denied app must keep working silently.
     */
    fun show(message: PushMessage): Boolean {
        if (!canPost()) return false

        val channel = NotificationChannels.forKind(message.kind)
        val intent = Intent(Intent.ACTION_VIEW, DeepLink.forSession(message.sessionID).toUri())
            .setPackage(context.packageName)

        // FLAG_IMMUTABLE is required from API 31 and is the only safe choice
        // here: nothing about this intent needs to be filled in later, so a
        // mutable one would be an invitation to have it filled in by someone
        // else.
        val pending = PendingIntent.getActivity(
            context,
            message.sessionID.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(message.title)
            .setContentText(message.body)
            // The body can be a file path or an error; the full text is worth
            // reading when it is long, and the app has the detail anyway.
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
            .setCategory(
                when (message.kind) {
                    PushKind.Permission, PushKind.Question -> NotificationCompat.CATEGORY_REMINDER
                    PushKind.Failure -> NotificationCompat.CATEGORY_ERROR
                },
            )
            .setPriority(
                when (message.kind) {
                    PushKind.Permission, PushKind.Question -> NotificationCompat.PRIORITY_HIGH
                    PushKind.Failure -> NotificationCompat.PRIORITY_DEFAULT
                },
            )
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(message.tag, TAG_CODE, notification)
            true
        } catch (_: SecurityException) {
            // The permission was revoked between the check and the post.
            false
        }
    }

    /** Clears a session's notification, e.g. once its request was answered. */
    fun clear(tag: String) {
        NotificationManagerCompat.from(context).cancel(tag, TAG_CODE)
    }

    /**
     * Whether notifications can be posted at all.
     *
     * Checked before every post rather than cached: the user can revoke the
     * permission from system settings at any moment, and a cached true would
     * mean posting into a SecurityException.
     *
     * The runtime permission only exists from API 33. Below that it is not a
     * runtime permission at all, and asking for it reports denial — which would
     * silently disable notifications on every device older than Android 13.
     * `areNotificationsEnabled()` is the check that works on all of them.
     */
    fun canPost(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        /**
         * One id, because the tag does the separating. Two notifications with
         * the same tag and id replace each other, which is exactly the
         * per-session collapsing this wants.
         */
        const val TAG_CODE = 1
    }
}
