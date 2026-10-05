package dev.sebastiano.clockblocker.opus.core.notifications

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.getSystemService

/**
 * Notification channels, grouped so users can mute one kind of advice (say caffeine) without losing the
 * others (design §3.4). Reminders alert; the ongoing channels never make a sound.
 */
enum class OpusChannel(
    val id: String,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val group: OpusChannelGroup,
    val importance: Int,
    val silent: Boolean,
) {
    Light(
        id = "advice_light",
        title = R.string.notif_channel_light,
        description = R.string.notif_channel_light_desc,
        group = OpusChannelGroup.Reminders,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        silent = false,
    ),
    Sleep(
        id = "advice_sleep",
        title = R.string.notif_channel_sleep,
        description = R.string.notif_channel_sleep_desc,
        group = OpusChannelGroup.Reminders,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        silent = false,
    ),
    SupplementsAndCaffeine(
        id = "advice_supplements_caffeine",
        title = R.string.notif_channel_supplements,
        description = R.string.notif_channel_supplements_desc,
        group = OpusChannelGroup.Reminders,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        silent = false,
    ),

    /** Must not be IMPORTANCE_MIN, or the Live Update can't be promoted. */
    TravelLive(
        id = "travel_live",
        title = R.string.notif_channel_travel_live,
        description = R.string.notif_channel_travel_live_desc,
        group = OpusChannelGroup.Ongoing,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        silent = true,
    ),

    /** Default importance (visible on the lock screen and status bar) but no sound or vibration, ever. */
    Now(
        id = "now",
        title = R.string.notif_channel_now,
        description = R.string.notif_channel_now_desc,
        group = OpusChannelGroup.Ongoing,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        silent = true,
    ),
}

enum class OpusChannelGroup(val id: String, @param:StringRes val title: Int) {
    Reminders("reminders", R.string.notif_group_reminders),
    Ongoing("ongoing", R.string.notif_group_ongoing),
}

object NotificationChannels {
    /** Idempotent; re-running also refreshes names/descriptions after a locale change. */
    fun ensureCreated(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannelGroups(
            OpusChannelGroup.entries.map { NotificationChannelGroup(it.id, context.getString(it.title)) },
        )
        manager.createNotificationChannels(
            OpusChannel.entries.map { channel ->
                NotificationChannel(channel.id, context.getString(channel.title), channel.importance).apply {
                    description = context.getString(channel.description)
                    group = channel.group.id
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setShowBadge(!channel.silent)
                    if (channel.silent) {
                        setSound(null, null)
                        enableVibration(false)
                    }
                }
            },
        )
    }
}
