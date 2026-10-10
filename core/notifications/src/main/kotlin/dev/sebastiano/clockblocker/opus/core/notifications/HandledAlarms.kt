package dev.sebastiano.clockblocker.opus.core.notifications

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.time.Instant

/**
 * The alarm instants [AdviceAlarmScheduler] already handled. Without exact-alarm access each instant is armed twice
 * (a 10-minute window plus an allow-while-idle backstop), and both can be delivered: the second delivery must not
 * remind again. Persisted, so a duplicate that arrives in a new process is still recognised. Keeps only the last
 * [CAPACITY] instants, which covers every alarm the scheduler holds at once.
 */
@SingleIn(AppScope::class)
@Inject
class HandledAlarms(application: Application) {
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Marks [at] handled. Returns `false` if it already was, so the caller skips the duplicate. */
    @Synchronized
    fun claim(at: Instant): Boolean {
        val millis = at.toEpochMilli()
        val handled = read()
        if (millis in handled) return false
        val updated = (handled + millis).sorted().takeLast(CAPACITY)
        prefs.edit(commit = true) { putString(KEY, updated.joinToString(",")) }
        return true
    }

    private fun read(): List<Long> =
        prefs.getString(KEY, null).orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    companion object {
        /** SharedPreferences file name. Left out of Android backup: it describes this device's alarms. */
        const val PREFS = "notifications_handled_alarms"
        private const val KEY = "instants"
        internal const val CAPACITY = 2 * AdviceAlarmScheduler.MAX_ALARMS
    }
}
