package dev.sebastiano.clockblocker.opus.core.data.places

import android.app.Application
import dev.sebastiano.clockblocker.opus.core.data.PlaceSearch
import dev.sebastiano.clockblocker.opus.core.data.time.AppDispatchers
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.time.Duration

/**
 * [PlaceSearch] over the bundled `places.tsv` asset (~4,100 airports with IANA zones; fully offline).
 *
 * The index is built lazily on first use, off the main thread, exactly once (concurrent first callers wait
 * for the same load). Call [warmUp] early (e.g. when the trip editor opens) to hide the one-off cost.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AssetPlaceSearch internal constructor(
    private val openDataset: () -> InputStream,
    private val dispatchers: AppDispatchers,
) : PlaceSearch {

    @Inject
    constructor(application: Application, dispatchers: AppDispatchers) :
        this({ application.assets.open(PlaceTsv.AssetName) }, dispatchers)

    private val mutex = Mutex()

    @Volatile
    private var index: PlaceIndex? = null

    /** How long reading + indexing the asset took, once loaded (for diagnostics and perf tests). */
    @Volatile
    var loadDuration: Duration? = null
        private set

    /** The loaded index, loading it if needed. */
    suspend fun index(): PlaceIndex = index ?: mutex.withLock {
        index ?: withContext(dispatchers.io) { load() }.also { index = it }
    }

    /** Loads the index now if it isn't loaded yet. */
    suspend fun warmUp() {
        index()
    }

    override suspend fun search(query: String, limit: Int): List<Place> {
        val idx = index()
        return withContext(dispatchers.default) { idx.search(query, limit) }
    }

    override suspend fun byCode(code: String): Place? = index().byCode(code)

    private fun load(): PlaceIndex {
        val start = System.nanoTime()
        val records = openDataset().bufferedReader(Charsets.UTF_8).use(PlaceTsv::parse)
        return PlaceIndex(records).also { loadDuration = Duration.ofNanos(System.nanoTime() - start) }
    }
}
