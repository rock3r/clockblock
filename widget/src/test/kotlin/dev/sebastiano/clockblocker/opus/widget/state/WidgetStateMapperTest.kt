package dev.sebastiano.clockblocker.opus.widget.state

import dev.sebastiano.clockblocker.opus.core.circadian.adaptationProgressAt
import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class WidgetStateMapperTest {

    private val tokyo = "Asia/Tokyo"
    private val lisbon = "Europe/Lisbon"

    private val tokyoDay = plan {
        day(1, "2026-10-06", tokyo) {
            advice(AdviceType.SeeBrightLight, "2026-10-06T09:00", "2026-10-06T12:00")
            advice(AdviceType.AvoidLight, "2026-10-06T15:00", "2026-10-06T18:00")
            advice(AdviceType.Caffeine, "2026-10-06T16:00", "2026-10-06T17:00")
            advice(AdviceType.Sleep, "2026-10-06T22:00", "2026-10-07T06:00")
            advice(AdviceType.Melatonin, "2026-10-06T20:30", "2026-10-06T20:30")
        }
        phase(at(tokyo, "2026-10-06T00:00"), bodyOffsetMinutes = 240, cbtMin = at(tokyo, "2026-10-06T04:00"))
        phase(at(tokyo, "2026-10-07T00:00"), bodyOffsetMinutes = 300, cbtMin = at(tokyo, "2026-10-07T03:00"))
    }

    private fun active(state: WidgetState): WidgetState.Active = state.shouldBeInstanceOf<WidgetState.Active>()

    @Test
    fun `no plan maps to the empty state`() {
        WidgetStateMapper.map(null, Instant.now()) shouldBe WidgetState.NoTrip
    }

    @Test
    fun `current advice wins by priority and next starts when it ends`() {
        val state = active(WidgetStateMapper.map(tokyoDay, at(tokyo, "2026-10-06T16:30")))

        state.current.shouldNotBeNull().type shouldBe AdviceType.AvoidLight
        state.current!!.startMinute shouldBe 15 * 60
        state.current!!.endMinute shouldBe 18 * 60
        // Caffeine started inside the window; "then" means after the current block ends.
        state.next.shouldNotBeNull().type shouldBe AdviceType.Melatonin
        state.stage shouldBe WidgetState.Stage.InProgress
        state.displayZoneId shouldBe tokyo
        state.secondaryZoneId shouldBe lisbon
        state.destinationName shouldBe "Tokyo"
    }

    @Test
    fun `between blocks there is no current advice and next is the upcoming one`() {
        val state = active(WidgetStateMapper.map(tokyoDay, at(tokyo, "2026-10-06T13:00")))

        state.current.shouldBeNull()
        state.next.shouldNotBeNull().type shouldBe AdviceType.AvoidLight
        state.stage shouldBe WidgetState.Stage.InProgress
    }

    @Test
    fun `stage is upcoming before the first advice and done after the last`() {
        active(WidgetStateMapper.map(tokyoDay, at(tokyo, "2026-10-05T08:00"))).stage shouldBe
            WidgetState.Stage.Upcoming
        val done = active(WidgetStateMapper.map(tokyoDay, at(tokyo, "2026-10-08T08:00")))
        done.stage shouldBe WidgetState.Stage.Done
        done.current.shouldBeNull()
        done.next.shouldBeNull()
    }

    @Test
    fun `arcs cover the next 24 hours in display wall-clock minutes`() {
        val state = active(WidgetStateMapper.map(tokyoDay, at(tokyo, "2026-10-06T16:30")))

        // The current block is clipped to start at "now".
        state.arcs shouldContain DialArc(AdviceType.AvoidLight, 16 * 60 + 30, 90)
        state.arcs shouldContain DialArc(AdviceType.Sleep, 22 * 60, 8 * 60)
        state.arcs shouldContain DialArc(AdviceType.Melatonin, 20 * 60 + 30, 0)
        // Overlapping blocks are all drawn; the dial layers them by priority.
        state.arcs shouldContain DialArc(AdviceType.Caffeine, 16 * 60 + 30, 30)
        // This morning's light (09:00–12:00) is in the past and must not be drawn.
        state.arcs.none { it.type == AdviceType.SeeBrightLight } shouldBe true
    }

    @Test
    fun `arcs longer than the window are capped at a full turn`() {
        val p = plan {
            day(0, "2026-10-05", tokyo) { advice(AdviceType.Flight, "2026-10-05T10:00", "2026-10-07T10:00") }
        }
        val state = active(WidgetStateMapper.map(p, at(tokyo, "2026-10-05T12:00")))
        state.arcs shouldBe listOf(DialArc(AdviceType.Flight, 12 * 60, 1440))
    }

    @Test
    fun `sleep across spring-forward keeps its wall-clock span`() {
        // Europe/Lisbon jumps 01:00 -> 02:00 on 2026-03-29.
        val p = plan(origin = "Asia/Tokyo", destination = lisbon) {
            day(1, "2026-03-28", lisbon) { advice(AdviceType.Sleep, "2026-03-28T22:00", "2026-03-29T06:00") }
        }
        val state = active(WidgetStateMapper.map(p, at(lisbon, "2026-03-28T20:00")))
        val sleep = state.arcs.single()
        sleep.startMinute shouldBe 22 * 60
        sleep.sweepMinutes shouldBe 8 * 60 // 22:00 -> 06:00 on the wall, although only 7 h elapse
        state.current.shouldBeNull()
        state.next.shouldNotBeNull().endMinute shouldBe 6 * 60
    }

    @Test
    fun `display offset follows DST at the capture instant`() {
        val p = plan(origin = "Asia/Tokyo", destination = lisbon) {
            day(1, "2026-07-01", lisbon) { advice(AdviceType.SeeLight, "2026-07-01T08:00", "2026-07-01T10:00") }
            day(2, "2026-12-01", lisbon) { advice(AdviceType.SeeLight, "2026-12-01T08:00", "2026-12-01T10:00") }
        }
        active(WidgetStateMapper.map(p, at(lisbon, "2026-07-01T09:00"))).displayOffsetMinutes shouldBe 60
        active(WidgetStateMapper.map(p, at(lisbon, "2026-12-01T09:00"))).displayOffsetMinutes shouldBe 0
    }

    @Test
    fun `misalignment handles half-hour zones and interpolated body offsets`() {
        val kolkata = "Asia/Kolkata"
        val p = plan(origin = "Europe/London", destination = kolkata) {
            day(1, "2026-10-06", kolkata) { advice(AdviceType.SeeLight, "2026-10-06T08:00", "2026-10-06T10:00") }
            phase(at(kolkata, "2026-10-06T00:00"), bodyOffsetMinutes = 60, cbtMin = at(kolkata, "2026-10-06T08:00"))
            phase(at(kolkata, "2026-10-07T00:00"), bodyOffsetMinutes = 120, cbtMin = at(kolkata, "2026-10-07T07:00"))
        }
        val state = active(WidgetStateMapper.map(p, at(kolkata, "2026-10-06T12:00")))
        state.displayOffsetMinutes shouldBe 330
        state.bodyOffsetMinutes shouldBe 90 // halfway between +60 and +120
        state.misalignmentMinutes shouldBe 240
        // Labels follow the app-wide convention: body relative to local time, "−4 h" = body 4 h behind.
        state.bodyRelativeMinutes shouldBe -240
        DialMath.formatMisalignment(state.bodyRelativeMinutes) shouldBe "\u22124 h"
        DialMath.formatMisalignment(270) shouldBe "+4\u00BD h"
    }

    @Test
    fun `body relative minutes wrap into half a day either side`() {
        val p = plan {
            day(1, "2026-10-06", tokyo) { advice(AdviceType.SeeLight, "2026-10-06T08:00", "2026-10-06T10:00") }
            phase(at(tokyo, "2026-10-06T00:00"), bodyOffsetMinutes = -600, cbtMin = at(tokyo, "2026-10-06T08:00"))
            phase(at(tokyo, "2026-10-07T00:00"), bodyOffsetMinutes = -600, cbtMin = at(tokyo, "2026-10-07T08:00"))
        }
        val state = active(WidgetStateMapper.map(p, at(tokyo, "2026-10-06T09:00")))
        // -600 - 540 = -1140 → +300: the body is effectively 5 h ahead, not 19 h behind.
        state.bodyRelativeMinutes shouldBe 300
        DialMath.formatHoursMagnitude(300) shouldBe "5 h"
        DialMath.formatHoursMagnitude(-90) shouldBe "1\u00BD h"
    }

    @Test
    fun `body night is anchored on the nearest CBTmin`() {
        val state = active(WidgetStateMapper.map(tokyoDay, at(tokyo, "2026-10-06T05:00")))
        state.cbtMinMinute shouldBe 4 * 60
        state.bodyNight shouldBe DialArc(null, 22 * 60, 8 * 60)
    }

    @Test
    fun `without a phase trajectory the body clock stays on origin time`() {
        val p = plan {
            day(1, "2026-10-06", tokyo) { advice(AdviceType.SeeLight, "2026-10-06T08:00", "2026-10-06T10:00") }
        }
        val state = active(WidgetStateMapper.map(p, at(tokyo, "2026-10-06T09:00")))
        state.bodyOffsetMinutes shouldBe 60 // Lisbon summer time on 2026-10-06
        state.misalignmentMinutes shouldBe 480
        state.cbtMinMinute.shouldBeNull()
        // Body 23:00-07:00 shown on the local dial: 23:00 + 8 h = 07:00 local.
        state.bodyNight shouldBe DialArc(null, 7 * 60, 8 * 60)
    }

    @Test
    fun `upcoming lists what starts after now, without the current block, at most three`() {
        val state = active(WidgetStateMapper.map(tokyoDay, at(tokyo, "2026-10-06T16:30")))
        // Caffeine started at 16:00 (before now): it is running, not "up next".
        state.upcoming.map { it.type } shouldBe listOf(AdviceType.Melatonin, AdviceType.Sleep)

        val morning = active(WidgetStateMapper.map(tokyoDay, at(tokyo, "2026-10-06T08:00")))
        morning.upcoming.map { it.type } shouldBe
            listOf(AdviceType.SeeBrightLight, AdviceType.AvoidLight, AdviceType.Caffeine)
    }

    @Test
    fun `the logged outcome of the current block is carried`() {
        val now = at(tokyo, "2026-10-06T16:30")
        active(WidgetStateMapper.map(tokyoDay, now)).currentOutcome.shouldBeNull()

        val current = active(WidgetStateMapper.map(tokyoDay, now)).current!!
        val logs = listOf(AdviceLog("other", AdviceOutcome.Skipped), AdviceLog(current.adviceId, AdviceOutcome.Done))
        active(WidgetStateMapper.map(tokyoDay, now, logs)).currentOutcome shouldBe AdviceOutcome.Done
    }

    @Test
    fun `plan day and adaptation progress come from the plan`() {
        val now = at(tokyo, "2026-10-06T16:30")
        val state = active(WidgetStateMapper.map(tokyoDay, now))
        state.dayKind shouldBe DayKind.Arrival
        state.dayIndex shouldBe 1
        state.adaptation shouldBe tokyoDay.adaptationProgressAt(now)

        val travel = plan { day(0, "2026-10-05", lisbon, DayKind.Travel) { advice(AdviceType.Flight, "2026-10-05T10:00", "2026-10-05T20:00") } }
        val onTravel = active(WidgetStateMapper.map(travel, at(lisbon, "2026-10-05T12:00")))
        onTravel.dayKind shouldBe DayKind.Travel
        onTravel.dayIndex shouldBe 0
    }

    @Test
    fun `the trip route is carried for the larger sizes`() {
        val now = at(tokyo, "2026-10-06T16:30")
        active(WidgetStateMapper.map(tokyoDay, now)).route.shouldBeNull()
        active(WidgetStateMapper.map(tokyoDay, now, route = WidgetRoute("LIS", "HND"))).route shouldBe
            WidgetRoute("LIS", "HND")
    }

    @Test
    fun `redacting for the lock screen drops places, the route and the melatonin dot`() {
        val now = at(tokyo, "2026-10-06T16:30")
        val full = active(WidgetStateMapper.map(tokyoDay, now, route = WidgetRoute("LIS", "HND")))
        val redacted = active(WidgetStateMapper.redact(full))

        redacted.redacted shouldBe true
        redacted.secondaryZoneId.shouldBeNull()
        redacted.route.shouldBeNull()
        redacted.arcs.none { it.type == AdviceType.Melatonin } shouldBe true
        // Times, block kinds and the dial stay.
        redacted.current shouldBe full.current
        redacted.upcoming shouldBe full.upcoming
        redacted.arcs shouldBe full.arcs.filter { it.type != AdviceType.Melatonin }
        WidgetStateMapper.redact(WidgetState.NoTrip) shouldBe WidgetState.NoTrip
    }

    @Test
    fun `display zone follows the plan day the user is in`() {
        val p = plan {
            day(0, "2026-10-05", lisbon) { advice(AdviceType.SeeLight, "2026-10-05T08:00", "2026-10-05T10:00") }
            day(1, "2026-10-06", tokyo) { advice(AdviceType.SeeLight, "2026-10-06T08:00", "2026-10-06T10:00") }
        }
        val onDay0 = active(WidgetStateMapper.map(p, at(lisbon, "2026-10-05T09:00")))
        onDay0.displayZoneId shouldBe lisbon
        onDay0.secondaryZoneId shouldBe tokyo
        active(WidgetStateMapper.map(p, at(tokyo, "2026-10-06T09:00"))).displayZoneId shouldBe tokyo
    }

    @Test
    fun `the travel day keeps the departure zone until landing, like the plan screen and the notifications`() {
        // Lisbon 09:00 departure, Tokyo 08:00 next-day arrival: Tokyo's midnight comes long before the landing.
        val p = plan {
            day(0, "2026-10-05", lisbon, kind = DayKind.Travel) {
                advice(AdviceType.Flight, "2026-10-05T09:00", "2026-10-06T00:00")
            }
            day(1, "2026-10-06", tokyo) { advice(AdviceType.SeeLight, "2026-10-06T09:00", "2026-10-06T11:00") }
        }
        val beforeLanding = active(WidgetStateMapper.map(p, at(tokyo, "2026-10-06T07:00")))
        beforeLanding.displayZoneId shouldBe lisbon
        beforeLanding.secondaryZoneId shouldBe tokyo
        active(WidgetStateMapper.map(p, at(tokyo, "2026-10-06T08:00"))).displayZoneId shouldBe tokyo
    }

    @Test
    fun `arcs always stay on the dial`() = runTest {
        val start = at(tokyo, "2026-10-05T00:00").epochSecond
        checkAll(200, Arb.long(start, start + Duration.ofDays(3).seconds)) { epoch ->
            val state = WidgetStateMapper.map(tokyoDay, Instant.ofEpochSecond(epoch)) as WidgetState.Active
            state.arcs.forEach { arc ->
                (arc.startMinute in 0 until 1440) shouldBe true
                (arc.sweepMinutes in 0..1440) shouldBe true
            }
            state.current?.let { (it.startMinute in 0 until 1440) shouldBe true }
        }
    }

    @Test
    fun `helpers`() {
        DialMath.canvasDegrees(12 * 60) shouldBe 270f // noon at the top
        DialMath.canvasDegrees(0) shouldBe 90f // midnight at the bottom
        DialMath.canvasDegrees(18 * 60) shouldBe 0f // evening on the right
        DialMath.formatMisalignment(-180) shouldBe "\u22123 h"
        DialMath.formatMisalignment(10).shouldBeNull()
        DialMath.formatMisalignment(20) shouldBe "+\u00BD h"
        DialMath.cityName("America/Argentina/Buenos_Aires") shouldBe "Buenos Aires"
        DialMath.minuteOfDay(Instant.parse("2026-10-06T23:30:00Z"), 330) shouldBe 5 * 60
    }
}
