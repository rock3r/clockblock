package dev.sebastiano.clockblocker.opus.shell

import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.PhasePoint
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class BodyNightTest {
    private val sleep = SleepWindow(LocalTime.of(23, 0), LocalTime.of(7, 0))
    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    /** Noon in Tokyo (03:00 UTC, 04:00 in Lisbon summer time). */
    private val tokyoNoon = Instant.parse("2026-06-12T03:00:00Z")

    /** A plan whose body clock sits at [bodyOffset] (a one-point trajectory clamps to it). */
    private fun planWithBodyAt(bodyOffset: ZoneOffset) = JetLagPlan(
        tripId = "t",
        generatedAt = tokyoNoon,
        strategy = AdaptationStrategy.Adapt,
        direction = ShiftDirection.Advance,
        shiftHours = 8.0,
        originZoneId = lisbon.id,
        destinationZoneId = tokyo.id,
        days = emptyList(),
        phase = listOf(PhasePoint(tokyoNoon, bodyOffset.totalSeconds / 60, tokyoNoon)),
        estimatedDaysToAdapt = 5.0,
        estimatedDaysWithoutPlan = 8.0,
    )

    @Test
    fun `no profile is never calm`() {
        isBodyNight(plan = null, sleep = null, now = tokyoNoon, localZone = lisbon) shouldBe false
        isBodyNight(planWithBodyAt(ZoneOffset.ofHours(1)), sleep = null, now = tokyoNoon, localZone = lisbon) shouldBe false
    }

    @Test
    fun `with a plan, the body clock decides even at a bright local noon`() {
        // Body still on Lisbon time: 04:00 → inside 23:00–07:00, although the device (Tokyo) reads 12:00.
        isBodyNight(planWithBodyAt(ZoneOffset.ofHours(1)), sleep, tokyoNoon, localZone = tokyo) shouldBe true
        // Body adapted to Tokyo: noon → awake.
        isBodyNight(planWithBodyAt(ZoneOffset.ofHours(9)), sleep, tokyoNoon, localZone = tokyo) shouldBe false
    }

    @Test
    fun `without a plan, the local wall clock is compared with the sleep habit`() {
        isBodyNight(plan = null, sleep, tokyoNoon, localZone = lisbon) shouldBe true // 04:00
        isBodyNight(plan = null, sleep, tokyoNoon, localZone = tokyo) shouldBe false // 12:00
    }

    @Test
    fun `the sleep window is half-open, bedtime inclusive and wake exclusive`() {
        val bedtime = Instant.parse("2026-06-12T22:00:00Z") // 23:00 Lisbon
        val wake = Instant.parse("2026-06-13T06:00:00Z") // 07:00 Lisbon
        isBodyNight(null, sleep, bedtime.minusSeconds(60), lisbon) shouldBe false
        isBodyNight(null, sleep, bedtime, lisbon) shouldBe true
        isBodyNight(null, sleep, wake.minusSeconds(60), lisbon) shouldBe true
        isBodyNight(null, sleep, wake, lisbon) shouldBe false
    }

    @Test
    fun `next minute boundary`() {
        millisToNextMinute(Instant.parse("2026-06-12T03:00:00Z")) shouldBe 60_000L
        millisToNextMinute(Instant.parse("2026-06-12T03:00:59.250Z")) shouldBe 750L
    }

    @Test
    fun `waiting millisToNextMinute always lands exactly on a minute boundary`() = runTest {
        checkAll(Arb.long(0L, 4_000_000_000_000L)) { epochMillis ->
            val now = Instant.ofEpochMilli(epochMillis)
            val wait = millisToNextMinute(now)
            wait shouldBeInRange 1L..60_000L
            now.plusMillis(wait).toEpochMilli() % 60_000L shouldBe 0L
        }
    }
}
