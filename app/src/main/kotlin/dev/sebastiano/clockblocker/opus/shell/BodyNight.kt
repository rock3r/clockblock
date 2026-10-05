package dev.sebastiano.clockblocker.opus.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import dev.sebastiano.clockblocker.opus.core.circadian.isBodyNightAt
import dev.sebastiano.clockblocker.opus.core.circadian.isWithin
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Whether the app should move calmly at [now]: true during "body night", when the body clock reads inside the
 * habitual [sleep] window.
 *
 * - With a [plan] (in progress or upcoming), the body clock is the plan's estimate, so a traveller whose body
 *   still thinks it's 03:00 gets calm motion even at a bright local noon.
 * - Without a plan the body is assumed entrained to where the device is: the [localZone] wall clock is compared
 *   with the sleep habit.
 * - No profile yet ([sleep] null, i.e. onboarding) → never calm.
 */
internal fun isBodyNight(plan: JetLagPlan?, sleep: SleepWindow?, now: Instant, localZone: ZoneId): Boolean {
    if (sleep == null) return false
    return plan?.isBodyNightAt(now, sleep) ?: now.atZone(localZone).toLocalTime().isWithin(sleep)
}

/** Milliseconds from [now] to the start of the next wall-clock minute (always in `1..60_000`). */
internal fun millisToNextMinute(now: Instant): Long {
    val next = now.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES)
    return (next.toEpochMilli() - now.toEpochMilli()).coerceIn(1L, 60_000L)
}

/**
 * [base]'s instants in the zone the device is in *now*: unlike `Clock.systemDefaultZone()` it follows time-zone
 * changes while the app runs (a traveller landing), which the no-plan body-night fallback depends on.
 */
internal class DeviceZoneClock(private val base: Clock) : Clock() {
    override fun instant(): Instant = base.instant()

    override fun millis(): Long = base.millis()

    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = base.withZone(zone)
}

/**
 * [isBodyNight] for the current time, re-evaluated at every minute boundary of [clock] (the sleep window has
 * minute precision, so this is exactly as often as the answer can change) and whenever the inputs change.
 * The zone of [clock] is the device's local zone used for the no-plan fallback.
 */
@Composable
internal fun rememberBodyNight(plan: JetLagPlan?, sleep: SleepWindow?, clock: Clock): Boolean {
    val bodyNight by produceState(isBodyNight(plan, sleep, clock.instant(), clock.zone), plan, sleep, clock) {
        if (sleep == null) {
            value = false
            return@produceState
        }
        while (true) {
            val now = clock.instant()
            value = isBodyNight(plan, sleep, now, clock.zone)
            delay(millisToNextMinute(now))
        }
    }
    return bodyNight
}
