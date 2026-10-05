package dev.sebastiano.clockblocker.opus.core.notifications

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo

/**
 * Accessors contributed to the app graph. The app calls `graph.adviceAlarmScheduler.start(appScope)` once from
 * `Application.onCreate`; Settings/onboarding use [notificationPermissions].
 */
@ContributesTo(AppScope::class)
interface NotificationsGraph {
    val adviceAlarmScheduler: AdviceAlarmScheduler
    val notificationPermissions: NotificationPermissions
}
