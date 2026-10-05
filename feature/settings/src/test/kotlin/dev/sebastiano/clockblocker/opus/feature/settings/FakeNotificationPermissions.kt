package dev.sebastiano.clockblocker.opus.feature.settings

import android.content.Intent
import dev.sebastiano.clockblocker.opus.core.notifications.NotificationPermissionState
import dev.sebastiano.clockblocker.opus.core.notifications.NotificationPermissions
import java.time.Duration

/** Scriptable [NotificationPermissions] for ViewModel and UI tests. */
class FakeNotificationPermissions(
    var current: NotificationPermissionState = NotificationPermissionState(
        notificationsGranted = false,
        exactAlarmsAllowed = false,
        promotedAllowed = false,
        batteryOptimizationIgnored = false,
    ),
    override val runtimePermission: String? = "android.permission.POST_NOTIFICATIONS",
) : NotificationPermissions {
    var testReminders: Int = 0
        private set

    override fun state(): NotificationPermissionState = current
    override fun notificationSettingsIntent(): Intent = Intent("test.NOTIFICATION_SETTINGS")
    override fun exactAlarmSettingsIntent(): Intent = Intent("test.EXACT_ALARM_SETTINGS")
    override fun promotedSettingsIntent(): Intent = Intent("test.PROMOTED_SETTINGS")
    override fun batteryOptimizationSettingsIntent(): Intent = Intent("test.BATTERY_SETTINGS")

    override fun sendTestReminder(delay: Duration): Boolean {
        testReminders++
        return current.notificationsGranted
    }

    companion object {
        val AllGranted = NotificationPermissionState(
            notificationsGranted = true,
            exactAlarmsAllowed = true,
            promotedAllowed = true,
            batteryOptimizationIgnored = true,
        )
    }
}
