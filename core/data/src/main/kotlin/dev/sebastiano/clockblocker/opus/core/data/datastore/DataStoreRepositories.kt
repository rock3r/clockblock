package dev.sebastiano.clockblocker.opus.core.data.datastore

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import dev.sebastiano.clockblocker.opus.core.data.AdviceLogRepository
import dev.sebastiano.clockblocker.opus.core.data.ProfileRepository
import dev.sebastiano.clockblocker.opus.core.data.SettingsRepository
import dev.sebastiano.clockblocker.opus.core.data.TripRepository
import dev.sebastiano.clockblocker.opus.core.data.time.AppDispatchers
import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock

/** File names of the stores inside `filesDir/datastore/`. Part of the on-disk format: never rename. */
internal object StoreFiles {
    const val Profile = "profile.json"
    const val Trips = "trips.json"
    const val Settings = "settings.json"
    const val AdviceLogs = "advice_logs.json"
}

private fun ioScope(dispatchers: AppDispatchers) = CoroutineScope(dispatchers.io + SupervisorJob())

/** Ordering contract of [TripRepository.trips]: departure instant, then id for a stable order. */
internal val TripOrder: Comparator<Trip> = compareBy<Trip> { it.departure }.thenBy { it.id }

/** [ProfileRepository] persisted as `profile.json`. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DataStoreProfileRepository internal constructor(
    private val store: DataStore<ProfileDocument>,
) : ProfileRepository {

    @Inject
    constructor(application: Application, dispatchers: AppDispatchers, clock: Clock) : this(
        JsonDataStores.create(
            file = application.dataStoreFile(StoreFiles.Profile),
            serializer = ProfileDocument.serializer(),
            default = ProfileDocument(),
            scope = ioScope(dispatchers),
            clock = clock,
        ),
    )

    override val profile: Flow<UserProfile?> = store.data.map { it.profile }.distinctUntilChanged()

    override suspend fun save(profile: UserProfile) {
        store.updateData { it.copy(profile = profile) }
    }
}

/** [TripRepository] persisted as `trips.json`. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DataStoreTripRepository internal constructor(
    private val store: DataStore<TripsDocument>,
) : TripRepository {

    @Inject
    constructor(application: Application, dispatchers: AppDispatchers, clock: Clock) : this(
        JsonDataStores.create(
            file = application.dataStoreFile(StoreFiles.Trips),
            serializer = TripsDocument.serializer(),
            default = TripsDocument(),
            scope = ioScope(dispatchers),
            clock = clock,
        ),
    )

    override val trips: Flow<List<Trip>> =
        store.data.map { doc -> doc.trips.sortedWith(TripOrder) }.distinctUntilChanged()

    override fun trip(id: String): Flow<Trip?> =
        store.data.map { doc -> doc.trips.firstOrNull { it.id == id } }.distinctUntilChanged()

    override suspend fun upsert(trip: Trip) {
        store.updateData { doc ->
            val index = doc.trips.indexOfFirst { it.id == trip.id }
            val updated = if (index == -1) doc.trips + trip else doc.trips.toMutableList().also { it[index] = trip }
            doc.copy(trips = updated)
        }
    }

    override suspend fun delete(id: String) {
        store.updateData { doc -> doc.copy(trips = doc.trips.filterNot { it.id == id }) }
    }
}

/** [SettingsRepository] persisted as `settings.json`. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DataStoreSettingsRepository internal constructor(
    private val store: DataStore<SettingsDocument>,
) : SettingsRepository {

    @Inject
    constructor(application: Application, dispatchers: AppDispatchers, clock: Clock) : this(
        JsonDataStores.create(
            file = application.dataStoreFile(StoreFiles.Settings),
            serializer = SettingsDocument.serializer(),
            default = SettingsDocument(),
            scope = ioScope(dispatchers),
            clock = clock,
        ),
    )

    override val settings: Flow<AppSettings> = store.data.map { it.settings }.distinctUntilChanged()

    /** Atomic read-modify-write: concurrent updates are serialised, none is lost. */
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.updateData { it.copy(settings = transform(it.settings)) }
    }
}

/** [AdviceLogRepository] persisted as `advice_logs.json`. One entry per advice id; re-logging replaces it. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DataStoreAdviceLogRepository internal constructor(
    private val store: DataStore<AdviceLogDocument>,
) : AdviceLogRepository {

    @Inject
    constructor(application: Application, dispatchers: AppDispatchers, clock: Clock) : this(
        JsonDataStores.create(
            file = application.dataStoreFile(StoreFiles.AdviceLogs),
            serializer = AdviceLogDocument.serializer(),
            default = AdviceLogDocument(),
            scope = ioScope(dispatchers),
            clock = clock,
        ),
    )

    override fun logs(tripId: String): Flow<List<AdviceLog>> =
        store.data.map { it.logs[tripId].orEmpty() }.distinctUntilChanged()

    override suspend fun log(tripId: String, adviceId: String, outcome: AdviceOutcome) {
        store.updateData { doc ->
            val entries = doc.logs[tripId].orEmpty().filterNot { it.adviceId == adviceId } + AdviceLog(adviceId, outcome)
            doc.copy(logs = doc.logs + (tripId to entries))
        }
    }

    override suspend fun clear(tripId: String, adviceId: String) {
        store.updateData { doc ->
            val entries = doc.logs[tripId] ?: return@updateData doc
            if (entries.none { it.adviceId == adviceId }) return@updateData doc
            doc.copy(logs = doc.logs + (tripId to entries.filterNot { it.adviceId == adviceId }))
        }
    }
}
