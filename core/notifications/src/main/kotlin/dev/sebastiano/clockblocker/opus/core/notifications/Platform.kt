package dev.sebastiano.clockblocker.opus.core.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.text.format.DateFormat
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import java.time.Instant
import java.util.Locale

/**
 * Time source for notifications. Deliberately has no zone: notifications show the plan's local time
 * (`JetLagPlan.localZoneAt`), the same as the plan screen and the widgets, never the device's zone (#36).
 */
interface NotificationClock {
    fun now(): Instant
}

@ContributesBinding(AppScope::class)
@Inject
class SystemNotificationClock : NotificationClock {
    override fun now(): Instant = Instant.now()
}

/**
 * Platform switches that decide how (and whether) we can notify. Behind an interface so scheduling and
 * rendering are testable for every combination, including ones Robolectric can't simulate (promotion).
 */
interface PlatformCapabilities {
    /** App-level notification switch and the POST_NOTIFICATIONS runtime permission. */
    fun areNotificationsEnabled(): Boolean

    /** `SCHEDULE_EXACT_ALARM` state. */
    fun canScheduleExactAlarms(): Boolean

    /**
     * Whether the user could grant `SCHEDULE_EXACT_ALARM` in system settings: the build asks for it and the device
     * has the screen. False in builds that get `USE_EXACT_ALARM` at install (they never need to ask).
     */
    fun canRequestExactAlarms(): Boolean = true

    /** Whether Live Updates (promoted ongoing notifications) may be posted. */
    fun canPostPromotedNotifications(): Boolean

    /** Battery optimisation exemption, which makes alarms and notifications more dependable on some OEMs. */
    fun isIgnoringBatteryOptimizations(): Boolean

    fun is24HourFormat(): Boolean

    fun locale(): Locale
}

@ContributesBinding(AppScope::class)
@Inject
class AndroidPlatformCapabilities(private val application: Application) : PlatformCapabilities {
    private val context: Context get() = application

    override fun areNotificationsEnabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    override fun canScheduleExactAlarms(): Boolean =
        context.getSystemService<AlarmManager>()?.let(AlarmManagerCompat::canScheduleExactAlarms) ?: false

    // Only the manifest: resolving the settings screen can be hidden by package visibility, and the UI already
    // tolerates a missing screen when it launches it.
    override fun canRequestExactAlarms(): Boolean = runCatching {
        context.packageManager
            .getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            .requestedPermissions.orEmpty()
            .contains(Manifest.permission.SCHEDULE_EXACT_ALARM)
    }.getOrDefault(false)

    override fun canPostPromotedNotifications(): Boolean =
        runCatching { NotificationManagerCompat.from(context).canPostPromotedNotifications() }.getOrDefault(false)

    override fun isIgnoringBatteryOptimizations(): Boolean =
        context.getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(context.packageName) ?: false

    override fun is24HourFormat(): Boolean = DateFormat.is24HourFormat(context)

    override fun locale(): Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
}
