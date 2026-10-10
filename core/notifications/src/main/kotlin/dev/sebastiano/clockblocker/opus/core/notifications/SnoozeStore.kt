package dev.sebastiano.clockblocker.opus.core.notifications

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.time.Duration
import java.time.Instant

/** A "Snooze 15 min": everything stays quiet until [until], then the [adviceId] reminder comes back. */
data class Snooze(val until: Instant, val adviceId: String?)

/**
 * Persists the current snooze across process death (alarms outlive the process). Tiny and synchronous on
 * purpose: one timestamp and one id, read at every alarm.
 */
@SingleIn(AppScope::class)
@Inject
class SnoozeStore(application: Application) {
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun current(): Snooze? {
        val until = prefs.getLong(KEY_UNTIL, 0L).takeIf { it > 0 } ?: return null
        return Snooze(Instant.ofEpochMilli(until), prefs.getString(KEY_ADVICE, null))
    }

    /** The snooze if it is still running at [now]. */
    fun active(now: Instant): Snooze? = current()?.takeIf { it.until.isAfter(now) }

    fun snooze(now: Instant, adviceId: String?, duration: Duration = DEFAULT_DURATION): Snooze {
        val snooze = Snooze(now.plus(duration), adviceId)
        prefs.edit {
            putLong(KEY_UNTIL, snooze.until.toEpochMilli())
            putString(KEY_ADVICE, adviceId)
        }
        return snooze
    }

    fun clear() = prefs.edit { clear() }

    companion object {
        val DEFAULT_DURATION: Duration = Duration.ofMinutes(15)
        /**
         * SharedPreferences file name (pre-rename prefix kept so snoozes survive). Left out of Android backup: a
         * snooze is tied to this device's alarms and expires in minutes.
         */
        const val PREFS = "opus_notifications_snooze"
        private const val KEY_UNTIL = "until"
        private const val KEY_ADVICE = "advice_id"
    }
}
