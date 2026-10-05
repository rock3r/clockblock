package dev.sebastiano.clockblocker.opus.widget.legacy

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.sebastiano.clockblocker.opus.widget.TwoClocksWidgetProvider

/**
 * The classic Two Clocks dial is a bitmap with a frozen hand, so on the fallback path we redraw it every
 * ~15 minutes with an **inexact, non-wakeup** repeating alarm (no exact-alarm permission, batched by the system,
 * skipped while the device sleeps). Remote Compose widgets don't need this: the host moves the hand.
 */
object LegacyRefresh {
    const val ACTION_REFRESH = "dev.sebastiano.clockblocker.opus.widget.action.REFRESH_LEGACY"

    fun sync(context: Context, enabled: Boolean) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val existing = pendingIntent(context, PendingIntent.FLAG_NO_CREATE)
        when {
            enabled && existing == null -> alarms.setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                pendingIntent(context, 0)!!,
            )
            !enabled && existing != null -> {
                alarms.cancel(existing)
                existing.cancel()
            }
        }
    }

    fun isScheduled(context: Context): Boolean = pendingIntent(context, PendingIntent.FLAG_NO_CREATE) != null

    private fun pendingIntent(context: Context, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, TwoClocksWidgetProvider::class.java).setAction(ACTION_REFRESH),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )
}
