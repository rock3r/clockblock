package dev.sebastiano.clockblocker.opus.core.notifications

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.time.Duration

/** Everything that decides whether reminders will actually reach the user. */
data class NotificationPermissionState(
    /** App notifications on, and POST_NOTIFICATIONS granted. Without this nothing shows. */
    val notificationsGranted: Boolean,
    /** Exact alarms allowed; otherwise reminders may arrive up to ~10 minutes late. */
    val exactAlarmsAllowed: Boolean,
    /** Live Updates (promoted ongoing notifications) allowed on travel day; otherwise a normal notification. */
    val promotedAllowed: Boolean,
    /** Exempt from battery optimisation (helps on aggressive OEM builds). Optional. */
    val batteryOptimizationIgnored: Boolean,
) {
    /** True when reminders are fully dependable; the UI shows a one-tap fix card otherwise (design §3.4). */
    val isReliable: Boolean get() = notificationsGranted && exactAlarmsAllowed
}

/**
 * Permission and reliability helpers for Settings / onboarding: current [state], the right settings screen for
 * each fix, and a test reminder that goes through the real pipeline.
 */
interface NotificationPermissions {
    /** Reads the current state; call again on resume (users change these in system settings). */
    fun state(): NotificationPermissionState

    /** The runtime permission to request with `ActivityResultContracts.RequestPermission`. */
    val runtimePermission: String

    /** App notification settings (channels, block/unblock). */
    fun notificationSettingsIntent(): Intent

    /** "Alarms & reminders" special access for this app. */
    fun exactAlarmSettingsIntent(): Intent

    /** Promoted notification (Live Update) settings for this app. */
    fun promotedSettingsIntent(): Intent

    /** The battery optimisation list (not the direct-exemption dialog, which Play restricts). */
    fun batteryOptimizationSettingsIntent(): Intent

    /**
     * Posts a test reminder on a real reminder channel. With a [delay] it is delivered through the same alarm
     * path as real reminders (exact or fallback), proving the whole chain works. Returns `false` if
     * notifications are blocked.
     */
    fun sendTestReminder(delay: Duration = Duration.ZERO): Boolean
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidNotificationPermissions(
    private val application: Application,
    private val capabilities: PlatformCapabilities,
    private val reminders: ReminderNotifier,
    private val scheduler: AdviceAlarmScheduler,
) : NotificationPermissions {

    private val packageUri: Uri get() = Uri.fromParts("package", application.packageName, null)

    override fun state(): NotificationPermissionState = NotificationPermissionState(
        notificationsGranted = capabilities.areNotificationsEnabled(),
        exactAlarmsAllowed = capabilities.canScheduleExactAlarms(),
        promotedAllowed = capabilities.canPostPromotedNotifications(),
        batteryOptimizationIgnored = capabilities.isIgnoringBatteryOptimizations(),
    )

    override val runtimePermission: String = Manifest.permission.POST_NOTIFICATIONS

    override fun notificationSettingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, application.packageName)
            .newTask()

    override fun exactAlarmSettingsIntent(): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri).newTask()

    override fun promotedSettingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, application.packageName)
            .newTask()

    override fun batteryOptimizationSettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).newTask()

    override fun sendTestReminder(delay: Duration): Boolean {
        if (!capabilities.areNotificationsEnabled()) return false
        if (delay.isZero || delay.isNegative) return reminders.postTest()
        scheduler.scheduleTestReminder(delay)
        return true
    }

    private fun Intent.newTask(): Intent = addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
