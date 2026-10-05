package dev.sebastiano.clockblocker.opus.core.data.time

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The dispatchers the data layer runs on, injectable so tests can substitute a `TestDispatcher` everywhere.
 *
 * @property io blocking file IO (DataStore, asset reads).
 * @property default CPU-bound work (planning, search index construction).
 * @property main UI-facing work.
 */
data class AppDispatchers(
    val io: CoroutineDispatcher = Dispatchers.IO,
    val default: CoroutineDispatcher = Dispatchers.Default,
    val main: CoroutineDispatcher = Dispatchers.Main,
) {
    companion object {
        /** All three roles on one dispatcher, e.g. a test's `StandardTestDispatcher`. */
        fun single(dispatcher: CoroutineDispatcher): AppDispatchers = AppDispatchers(dispatcher, dispatcher, dispatcher)
    }
}

/**
 * Signals that wall-clock time has moved on, so time-dependent state (which plan is "current", which advice
 * is active) should be re-evaluated. Production ticks periodically from the [Clock]; tests drive it
 * explicitly (see `MutableClock` in `:core:testing`, which is both a `Clock` and a `Ticker`).
 */
fun interface Ticker {
    /** Emits the current instant immediately on collection and then whenever time has advanced. */
    fun ticks(): Flow<Instant>
}

/** A [Ticker] that emits `clock.instant()` right away and then every [period]. */
class ClockTicker(
    private val clock: Clock,
    private val period: Duration = Duration.ofMinutes(1),
) : Ticker {
    init {
        require(!period.isNegative && !period.isZero) { "Tick period must be positive, was $period" }
    }

    override fun ticks(): Flow<Instant> = flow {
        while (true) {
            emit(clock.instant())
            delay(period.toMillis())
        }
    }
}
