package dev.sebastiano.clockblocker.opus.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Cancels the repeating 15-minute alarm that the removed RemoteViews fallback used to redraw its Two Clocks bitmap.
 * Repeating alarms survive app updates until something cancels them, so a phone that ran the fallback before
 * updating would keep waking the provider for nothing. Cheap and idempotent: it only acts when the old
 * `PendingIntent` still exists. Safe to delete once no install can still carry that alarm.
 */
internal object RetiredLegacyRefresh {
    const val ACTION = "dev.sebastiano.clockblocker.opus.widget.action.REFRESH_LEGACY"

    fun cancel(context: Context) {
        val existing = pendingIntent(context, PendingIntent.FLAG_NO_CREATE) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(existing)
        existing.cancel()
    }

    /** The exact `PendingIntent` the fallback scheduled (same component, action and request code). */
    internal fun pendingIntent(context: Context, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, TwoClocksWidgetProvider::class.java).setAction(ACTION),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )
}
