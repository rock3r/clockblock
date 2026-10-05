package dev.sebastiano.clockblocker.opus.core.data

import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import kotlinx.coroutines.flow.Flow

/** The user's profile. `null` until onboarding completes. */
interface ProfileRepository {
    val profile: Flow<UserProfile?>
    suspend fun save(profile: UserProfile)
}

interface TripRepository {
    /** All trips, sorted by departure ascending. */
    val trips: Flow<List<Trip>>
    fun trip(id: String): Flow<Trip?>
    suspend fun upsert(trip: Trip)
    suspend fun delete(id: String)
}

/**
 * Plans are *derived* data: computed from trips + profile by the circadian engine and cached. Never edited
 * directly, so they can't drift from their inputs.
 */
interface PlanRepository {
    fun plan(tripId: String): Flow<JetLagPlan?>

    /** The plan relevant right now: in progress, or the next upcoming one. */
    val currentPlan: Flow<JetLagPlan?>
}

interface AdviceLogRepository {
    fun logs(tripId: String): Flow<List<AdviceLog>>
    suspend fun log(tripId: String, adviceId: String, outcome: AdviceOutcome)

    /** Forgets the outcome logged for [adviceId] in [tripId] (Undo). No-op when nothing was logged. */
    suspend fun clear(tripId: String, adviceId: String)
}

/** Offline airport/city search (bundled dataset). */
interface PlaceSearch {
    suspend fun search(query: String, limit: Int = 20): List<Place>
    suspend fun byCode(code: String): Place?
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
