package dev.sebastiano.clockblocker.opus.core.notifications

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.time.Instant

/**
 * Which alarm instants [AdviceAlarmScheduler] armed, and which it already handled. Persisted, so it holds across
 * processes.
 *
 * Without exact-alarm access each instant is armed twice (a 10-minute window plus an allow-while-idle backstop).
 * Inexact alarms can arrive late and out of order: an instant's Start can come before the reminder due 15 minutes
 * earlier, and handling it re-arms the chain from now, which would cancel the earlier one. So each delivery claims
 * every armed instant that is due ([claimDue]), and the scheduler reminds from all of them. A later delivery of an
 * instant already claimed gets nothing back, so it never reminds twice.
 */
@SingleIn(AppScope::class)
@Inject
class HandledAlarms(application: Application) {
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Records the schedule just armed: [instants] replace the future ones armed before, which the new schedule
     * cancelled (a block deleted, reminders turned off). Instants that were already due and not yet delivered are
     * kept, so the next delivery still catches up on them. Handled instants later than [now] are forgotten: they were
     * delivered under a wall clock that has since been set back, so they are due again.
     */
    @Synchronized
    fun recordArmed(instants: Collection<Instant>, now: Instant) {
        val nowMillis = now.toEpochMilli()
        val handled = read(KEY_HANDLED).filter { it <= nowMillis }
        val stillDue = read(KEY_ARMED).filter { it <= nowMillis }
        val armed = (stillDue + instants.map { it.toEpochMilli() }).filterNot { it in handled }
        prefs.edit(commit = true) {
            putString(KEY_ARMED, armed.joined())
            putString(KEY_HANDLED, handled.joined())
        }
    }

    /**
     * Claims the delivered instant [at] and every armed instant up to [upTo] (now) that no delivery claimed yet.
     * Returns them in time order; empty when [at] was already handled and nothing else is due (a duplicate delivery),
     * or when [at] is still in the future because the clock was set back after the alarm went off: it stays armed and
     * the re-arm that follows the clock change schedules it again.
     */
    @Synchronized
    fun claimDue(at: Instant, upTo: Instant): List<Instant> {
        if (at.isAfter(upTo)) return emptyList()
        val handled = read(KEY_HANDLED)
        val armed = read(KEY_ARMED)
        val limit = upTo.toEpochMilli()
        val due = (armed.filter { it <= limit } + at.toEpochMilli()).filterNot { it in handled }.distinct().sorted()
        if (due.isEmpty()) return emptyList()
        prefs.edit(commit = true) {
            putString(KEY_ARMED, (armed - due.toSet()).joined())
            putString(KEY_HANDLED, (handled + due).joined())
        }
        return due.map(Instant::ofEpochMilli)
    }

    private fun read(key: String): List<Long> =
        prefs.getString(key, null).orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    /** Keeps the latest [CAPACITY] instants: more than the scheduler ever holds at once. */
    private fun List<Long>.joined(): String = distinct().sorted().takeLast(CAPACITY).joinToString(",")

    companion object {
        /** SharedPreferences file name. Left out of Android backup: it describes this device's alarms. */
        const val PREFS = "notifications_handled_alarms"
        private const val KEY_ARMED = "armed"
        private const val KEY_HANDLED = "instants"
        internal const val CAPACITY = 4 * AdviceAlarmScheduler.MAX_ALARMS
    }
}
