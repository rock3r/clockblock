package dev.sebastiano.clockblocker.opus.core.notifications

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService

/**
 * Our notifications (Now, reminder, test reminder) share one explicit group with a summary while two or more of
 * them show, so the shade bundles them on our terms: the bundle opens the current plan. With a single
 * notification there is no summary (a lone summary would show as an empty notification of its own).
 */
internal object NotificationGroup {
    const val KEY: String = "opus.plan"

    private val children = setOf(NotificationIds.NOW, NotificationIds.REMINDER, NotificationIds.TEST)

    /** Posts, refreshes or removes the summary to match the children showing now. Call after every post/cancel. */
    @SuppressLint("MissingPermission") // Callers only post after areNotificationsEnabled(); SecurityException is caught.
    fun sync(context: Context, factory: NotificationFactory) {
        val active = context.getSystemService<NotificationManager>()?.activeNotifications.orEmpty()
            .filter { it.id in children && it.notification.group == KEY }
        val manager = NotificationManagerCompat.from(context)
        if (active.size < 2) {
            manager.cancel(NotificationIds.SUMMARY)
            return
        }
        // Ongoing children never expire; otherwise the summary leaves with the last child.
        val now = System.currentTimeMillis()
        val timeout = if (active.any { it.notification.timeoutAfter <= 0L }) {
            null
        } else {
            active.maxOf { it.postTime + it.notification.timeoutAfter - now }
        }
        try {
            manager.notify(NotificationIds.SUMMARY, factory.summary(timeout))
        } catch (_: SecurityException) {
            // Permission revoked between checks: nothing to group.
        }
    }
}
