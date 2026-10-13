package dev.sebastiano.clockblocker.opus.feature.plan

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import dev.sebastiano.clockblocker.opus.core.data.time.AppDispatchers
import dev.sebastiano.clockblocker.opus.core.notifications.AdviceAlarmScheduler
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

/**
 * Remembers which trips already had their "Clockblocked." celebration, so it fires exactly once per trip and
 * survives process death.
 */
interface CelebrationStore {
    fun celebrated(tripId: String): Flow<Boolean>
    suspend fun markCelebrated(tripId: String)
}

/**
 * [CelebrationStore] in a tiny private SharedPreferences file (one string set). Deliberately outside the
 * DataStore documents: it is UI bookkeeping, not user data worth exporting.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class PreferencesCelebrationStore(
    private val application: Application,
    private val dispatchers: AppDispatchers,
) : CelebrationStore {
    private val prefs by lazy { application.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val state by lazy { MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty().toSet()) }

    override fun celebrated(tripId: String): Flow<Boolean> =
        flow { state.collect { emit(tripId in it) } }.flowOn(dispatchers.io).distinctUntilChanged()

    override suspend fun markCelebrated(tripId: String) = withContext(dispatchers.io) {
        state.update { it + tripId }
        prefs.edit { putStringSet(KEY, state.value) }
    }

    companion object {
        /**
         * SharedPreferences file name (pre-rename prefix). Included in Android backup and device transfer with the
         * trips, so a restored phone doesn't replay celebrations that already played.
         */
        const val PREFS = "opus_plan_celebrations"
        private const val KEY = "celebrated_trip_ids"
    }
}

/** "Snooze 15 min" from the Now card: quiet reminders for a while, then remind about the advice again. */
fun interface AdviceSnoozer {
    suspend fun snooze(adviceId: String)
}

/** Routes the Now card's snooze through the alarm scheduler, exactly like the notification's Snooze action. */
@ContributesBinding(AppScope::class)
@Inject
class SchedulerAdviceSnoozer(private val scheduler: AdviceAlarmScheduler) : AdviceSnoozer {
    override suspend fun snooze(adviceId: String) = scheduler.snooze(adviceId)
}

/**
 * When the plan header's ambient sky (stars breathing, a small plane crossing) last played, so it stays rare on a
 * screen opened many times a day: see [ambientSkyDue].
 */
interface AmbientSkyStore {
    /** The last session's start, or null if it never played. */
    val lastSession: Flow<Instant?>

    /**
     * Atomically: if a session is due at [at] ([ambientSkyDue]), marks one as started then and returns true; otherwise
     * returns false. Two headers asking at once get one true between them.
     */
    suspend fun reserve(at: Instant): Boolean
}

/** [AmbientSkyStore] in a tiny private SharedPreferences file: UI bookkeeping, like [PreferencesCelebrationStore]. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class PreferencesAmbientSkyStore(
    private val application: Application,
    private val dispatchers: AppDispatchers,
) : AmbientSkyStore {
    private val prefs by lazy { application.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val state by lazy { MutableStateFlow(prefs.getLong(KEY, -1L).takeIf { it >= 0L }?.let(Instant::ofEpochMilli)) }

    override val lastSession: Flow<Instant?> get() = flow { state.collect { emit(it) } }.flowOn(dispatchers.io).distinctUntilChanged()

    override suspend fun reserve(at: Instant): Boolean = withContext(dispatchers.io) {
        synchronized(this@PreferencesAmbientSkyStore) {
            if (!ambientSkyDue(state.value, at)) return@withContext false
            state.value = at
            prefs.edit { putLong(KEY, at.toEpochMilli()) }
            true
        }
    }

    companion object {
        const val PREFS = "opus_plan_ambient_sky"
        private const val KEY = "last_session_epoch_millis"
    }
}

/**
 * Whether the header's ambient sky may play at [now], having last played at [lastSession]: at most once per
 * [AmbientSkyGap] (rare delight on a 10+/day screen). A session stamped further ahead than the gap (the clock was
 * moved back a long way) no longer blocks it.
 */
internal fun ambientSkyDue(lastSession: Instant?, now: Instant): Boolean {
    if (lastSession == null) return true
    val since = Duration.between(lastSession, now)
    return since >= AmbientSkyGap || since <= AmbientSkyGap.negated()
}

/** The ambient sky plays at most once in this long. */
internal val AmbientSkyGap: Duration = Duration.ofHours(12)
