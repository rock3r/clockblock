package dev.sebastiano.clockblocker.opus.core.testing

import dev.sebastiano.clockblocker.opus.core.data.time.Ticker
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A [Clock] you can move by hand, which is also a [Ticker]: every [advanceBy] / [set] emits, so flows that
 * re-evaluate on ticks (e.g. `PlanRepository.currentPlan`) react immediately and deterministically.
 *
 * ```
 * val clock = MutableClock(Instant.parse("2026-06-12T16:00:00Z"))
 * val repo = DefaultPlanRepository(..., clock = clock, ticker = clock, ...)
 * clock.advanceBy(Duration.ofDays(3))
 * ```
 */
class MutableClock(
    start: Instant = DemoData.Now,
    private val zone: ZoneId = ZoneOffset.UTC,
    private val state: MutableStateFlow<Instant> = MutableStateFlow(start),
) : Clock(), Ticker {

    /** The current instant as a flow (conflated; emits on every change). */
    val instants: StateFlow<Instant> get() = state.asStateFlow()

    override fun instant(): Instant = state.value

    override fun getZone(): ZoneId = zone

    /** A view with another zone that shares (and moves with) this clock's time. */
    override fun withZone(zone: ZoneId): MutableClock = MutableClock(state.value, zone, state)

    override fun ticks(): Flow<Instant> = state

    fun set(instant: Instant) {
        state.value = instant
    }

    fun advanceBy(duration: Duration) {
        state.value = state.value.plus(duration)
    }

    override fun toString(): String = "MutableClock(${state.value}, $zone)"
}
