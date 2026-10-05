package dev.sebastiano.clockblocker.opus.core.notifications

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.os.PowerManager
import android.text.format.DateFormat
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Wall-clock source for notifications; the user's *current* zone is read fresh every time (they travel!). */
interface NotificationClock {
    fun now(): Instant
    fun zone(): ZoneId
}

@ContributesBinding(AppScope::class)
@Inject
class SystemNotificationClock : NotificationClock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/**
 * Platform switches that decide how (and whether) we can notify. Behind an interface so scheduling and
 * rendering are testable for every combination, including ones Robolectric can't simulate (promotion).
 */
interface PlatformCapabilities {
    /** App-level notification switch and, on API 33+, the POST_NOTIFICATIONS runtime permission. */
    fun areNotificationsEnabled(): Boolean

    /** `SCHEDULE_EXACT_ALARM` state (always true below API 31). */
    fun canScheduleExactAlarms(): Boolean

    /** Whether Live Updates (promoted ongoing notifications) may be posted (false below API 36). */
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

    override fun canPostPromotedNotifications(): Boolean =
        runCatching { NotificationManagerCompat.from(context).canPostPromotedNotifications() }.getOrDefault(false)

    override fun isIgnoringBatteryOptimizations(): Boolean =
        context.getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(context.packageName) ?: false

    override fun is24HourFormat(): Boolean = DateFormat.is24HourFormat(context)

    override fun locale(): Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
}
