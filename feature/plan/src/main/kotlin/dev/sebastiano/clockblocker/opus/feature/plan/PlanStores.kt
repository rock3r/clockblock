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

    private companion object {
        const val PREFS = "opus_plan_celebrations"
        const val KEY = "celebrated_trip_ids"
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
