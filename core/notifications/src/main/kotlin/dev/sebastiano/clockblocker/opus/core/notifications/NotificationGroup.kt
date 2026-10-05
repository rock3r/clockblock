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
        // The summary is only useful while two or more children show, so it expires when the group would drop
        // below two by timeouts alone. An ongoing Now never expires, but an expiring reminder next to it does.
        val now = System.currentTimeMillis()
        val timeout = summaryLifetime(
            active.map { n -> n.notification.timeoutAfter.takeIf { it > 0L }?.let { n.postTime + it - now } },
        )
        try {
            manager.notify(NotificationIds.SUMMARY, factory.summary(timeout))
        } catch (_: SecurityException) {
            // Permission revoked between checks: nothing to group.
        }
    }

    /**
     * How long the summary should live, given each child's remaining lifetime (`null` = never expires): the
     * second-longest one, which is when fewer than two children remain. `null` if two children never expire.
     */
    fun summaryLifetime(childLifetimes: List<Long?>): Long? =
        childLifetimes.sortedWith(compareByDescending<Long?> { it ?: Long.MAX_VALUE }).getOrNull(1)
}
