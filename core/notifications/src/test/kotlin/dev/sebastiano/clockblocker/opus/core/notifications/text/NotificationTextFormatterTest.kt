package dev.sebastiano.clockblocker.opus.core.notifications.text

import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.AvoidCaffeine
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.AvoidLight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Flight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Melatonin
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.SeeBrightLight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Sleep
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.PhasePoint
import dev.sebastiano.clockblocker.opus.core.notifications.advice
import dev.sebastiano.clockblocker.opus.core.notifications.now.NowStateCalculator
import dev.sebastiano.clockblocker.opus.core.notifications.planOf
import dev.sebastiano.clockblocker.opus.core.notifications.planOfDays
import dev.sebastiano.clockblocker.opus.core.notifications.schedule.ReminderKind
import dev.sebastiano.clockblocker.opus.core.notifications.schedule.ReminderSpec
import dev.sebastiano.clockblocker.opus.core.notifications.utc
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveMaxLength
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.ZoneId
import java.util.Locale

class NotificationTextFormatterTest {

    // Saturday 2026-10-10. London is on BST (UTC+1), Tokyo on JST (UTC+9).
    private val avoid = advice(AvoidLight, "2026-10-10T14:00", "2026-10-10T17:00")
    private val sleep = advice(Sleep, "2026-10-10T17:00", "2026-10-11T01:00")
    private val plan = planOf(avoid, sleep, origin = "Europe/London", destination = "Asia/Tokyo")
    private val now = utc("2026-10-10T15:00")

    private fun formatter(use24Hour: Boolean = true) = NotificationTextFormatter(EnglishStrings, Locale.UK, use24Hour)

    /** [this] plan with every day in [zone]: notifications show times in the plan day's zone. */
    private fun JetLagPlan.inZone(zone: String) = copy(days = days.map { it.copy(zoneId = zone) })

    private fun nowText(zone: String, at: java.time.Instant = now, use24Hour: Boolean = true, p: JetLagPlan = plan): NotificationText {
        val zoned = p.inZone(zone)
        return formatter(use24Hour).now(NowStateCalculator.compute(zoned, at)!!, zoned, at)
    }

    @Nested
    inner class Now {
        @Test
        fun `title is the advice label, text says until in the plan's local zone`() {
            val text = nowText("Europe/London")

            text.title shouldBe "Avoid light"
            text.text shouldBe "until 18:00"
        }

        @Test
        fun `the other zone is a short tail, not a second copy of every line`() {
            val text = nowText("Europe/London")

            text.secondary shouldBe "02:00 Tokyo"
            text.line shouldBe "until 18:00 · 02:00 Tokyo"
        }

        @Test
        fun `what starts next is a detail line of its own`() {
            nowText("Europe/London").details shouldContainExactly listOf("Next: Sleep at 18:00")
        }

        @Test
        fun `expanded text reads top to bottom - until, details, tip`() {
            nowText("Europe/London").bigText shouldBe "until 18:00 · 02:00 Tokyo\nNext: Sleep at 18:00\ntip:AvoidLight"
        }

        @Test
        fun `at the destination the secondary zone is home`() {
            val text = nowText("Asia/Tokyo")

            text.line shouldBe "until 02:00 · 18:00 London"
            text.details shouldContainExactly listOf("Next: Sleep at 02:00")
        }

        @Test
        fun `no secondary tail when every zone agrees`() {
            val sameZones = planOf(avoid, sleep, origin = "Asia/Seoul", destination = "Asia/Tokyo")

            nowText("Asia/Tokyo", p = sameZones).let {
                it.secondary.shouldBeNull()
                it.line shouldBe "until 02:00"
            }
        }

        @Test
        fun `12-hour clocks are respected`() {
            // en-GB writes "pm" in lower case; the day period comes from the locale.
            val text = nowText("America/New_York", use24Hour = false)

            text.text shouldBe "until 1:00 pm"
            text.details shouldContainExactly listOf("Next: Sleep at 1:00 pm")
        }

        @Test
        fun `times on another day get a weekday`() {
            val longSleep = planOf(advice(Sleep, "2026-10-10T20:00", "2026-10-11T08:00"))

            nowText("Europe/London", utc("2026-10-10T22:00"), p = longSleep).text shouldBe "until Sun 09:00"
        }

        @Test
        fun `a logged outcome leads the line`() {
            val state = NowStateCalculator.compute(plan, now)!!.copy(outcome = AdviceOutcome.Done)

            formatter().now(state, plan, now).line shouldBe "Done · until 18:00 · 02:00 Tokyo"
        }

        @Test
        fun `in a gap the next window is named, with its start in the other zone`() {
            val gappy = planOf(advice(SeeBrightLight, "2026-10-10T08:00", "2026-10-10T10:00"), sleep)
            val text = nowText("Europe/London", utc("2026-10-10T12:00"), p = gappy)

            text.title shouldBe "Nothing right now"
            text.line shouldBe "Next: Sleep at 18:00 · 02:00 Tokyo"
            text.details.shouldBeEmpty()
            text.tip.shouldBeNull()
        }

        @Test
        fun `flights show their number`() {
            val flight = planOf(advice(Flight, "2026-10-10T12:00", "2026-10-10T23:00", detail = "BA7"))

            nowText("Europe/London", utc("2026-10-10T13:00"), p = flight).title shouldBe "In flight · BA7"
        }

        @Test
        fun `wall-clock times follow DST rules per instant`() {
            // London falls back at 01:00Z on 2026-10-25: 00:30Z is 01:30 BST, 02:30Z is 02:30 GMT.
            val dst = planOf(
                advice(AvoidLight, "2026-10-25T00:30", "2026-10-25T02:30"),
                advice(SeeBrightLight, "2026-10-25T02:30", "2026-10-25T04:00"),
            )

            nowText("Europe/London", utc("2026-10-25T00:45"), p = dst).let {
                it.text shouldBe "until 02:30"
                it.details shouldContainExactly listOf("Next: See bright light at 02:30")
            }
            ClockFormat(ZoneId.of("Europe/London"), Locale.UK)
                .range(utc("2026-10-25T00:30"), utc("2026-10-25T02:30"), utc("2026-10-25T00:30")) shouldBe "01:30–02:30"
        }

        /** Issue #43, with the plan from the user guide's screenshot (London → Tokyo, evening of day 1). */
        @Nested
        inner class Overlapping {
            private val noCaffeine = advice(AvoidCaffeine, "2026-10-10T12:00", "2026-10-10T19:00")
            private val avoidLight = advice(AvoidLight, "2026-10-10T16:00", "2026-10-10T19:00")
            private val bed = advice(Sleep, "2026-10-10T20:00", "2026-10-11T04:00")
            private val evening = planOf(noCaffeine, avoidLight, bed)

            @Test
            fun `until is the advice's own end, like the Now card, and the overlapping block is next`() {
                val text = nowText("Europe/London", utc("2026-10-10T14:00"), p = evening)

                text.title shouldBe "Avoid caffeine"
                text.line shouldBe "until 20:00 · 04:00 Tokyo"
                text.details shouldContainExactly listOf("Next: Avoid light at 17:00")
            }

            @Test
            fun `once Avoid light leads, the caffeine block still running is mentioned with its own end`() {
                val text = nowText("Europe/London", utc("2026-10-10T16:30"), p = evening)

                text.title shouldBe "Avoid light"
                text.line shouldBe "until 20:00 · 04:00 Tokyo"
                text.details shouldContainExactly listOf("Also now: Avoid caffeine until 20:00", "Next: Sleep at 21:00")
            }
        }
    }

    @Nested
    inner class Reminders {
        @Test
        fun `upcoming reminder uses absolute times, never a stale 'in 15 min'`() {
            val spec = ReminderSpec(ReminderKind.Upcoming, avoid, expiresAt = avoid.end)

            val early = formatter().reminder(spec, null, plan, utc("2026-10-10T13:45"))
            val late = formatter().reminder(spec, null, plan, utc("2026-10-10T14:05"))

            early.title shouldBe "Avoid light at 15:00"
            early.text shouldBe "15:00–18:00"
            early.secondary shouldBe "23:00–02:00 Tokyo"
            early.line shouldBe "15:00–18:00 · 23:00–02:00 Tokyo"
            late.title shouldBe "Avoid light now"
        }

        @Test
        fun `wake-up says what to do now`() {
            val light = advice(SeeBrightLight, "2026-10-11T01:00", "2026-10-11T03:00")
            val morning = planOf(sleep, light, dayZone = "Asia/Tokyo")
            val at = utc("2026-10-11T01:00")
            val spec = ReminderSpec(ReminderKind.WakeUp, sleep, expiresAt = at.plusSeconds(3600))

            val text = formatter().reminder(spec, NowStateCalculator.compute(morning, at), morning, at)

            text.title shouldBe "Sleep over"
            text.text shouldBe "Now: See bright light · until 12:00"
            text.tip shouldBe "tip:SeeBrightLight"
        }

        @Test
        fun `melatonin moment shows its dose`() {
            val melatonin = advice(Melatonin, "2026-10-10T20:00", detail = "0.5 mg")
            val spec = ReminderSpec(ReminderKind.Moment, melatonin, expiresAt = melatonin.start.plusSeconds(7200))

            val text = formatter().reminder(spec, null, plan, utc("2026-10-10T20:00"))

            text.title shouldBe "Melatonin now"
            text.text shouldBe "0.5 mg · tip:Melatonin"
        }

        @Test
        fun `snoozed reminder restates until when`() {
            val spec = ReminderSpec(ReminderKind.Snoozed, avoid, expiresAt = avoid.end)

            formatter().reminder(spec, null, plan, utc("2026-10-10T15:15")).let {
                it.title shouldBe "Reminder: Avoid light"
                it.text shouldBe "until 18:00"
            }
        }

        @Test
        fun `simultaneous reminders are listed`() {
            val spec = ReminderSpec(ReminderKind.Upcoming, avoid, alsoStarting = listOf(sleep), expiresAt = avoid.end)

            formatter().reminder(spec, null, plan, utc("2026-10-10T13:45")).tip shouldBe
                "tip:AvoidLight\nAlso: Sleep"
        }
    }

    @Nested
    inner class BodyClock {
        private fun withBody(offsetMinutes: Int, p: JetLagPlan = plan) =
            p.copy(phase = listOf(PhasePoint(utc("2026-10-10T00:00"), offsetMinutes, utc("2026-10-10T04:00"))))

        @Test
        fun `body clock on local time reads in sync`() {
            // No trajectory: the body is on home (London) time, and so is the plan day.
            formatter().bodyClock(plan, now) shouldBe "Body clock in sync"
        }

        @Test
        fun `after landing the body is behind local time`() {
            formatter().bodyClock(plan.inZone("Asia/Tokyo"), now) shouldBe "Body 8 h behind"
        }

        @Test
        fun `offsets round to the nearest half hour, ahead or behind`() {
            formatter().bodyClock(withBody(5 * 60 + 30), now) shouldBe "Body 4½ h ahead"
            formatter().bodyClock(withBody(45), now) shouldBe "Body clock in sync"
            formatter().bodyClock(withBody(20), now) shouldBe "Body ½ h behind"
        }

        @Test
        fun `offsets take the short way round the clock`() {
            // Body on UTC+13, plan day in Los Angeles (UTC−7): 20 h ahead is 4 h behind.
            formatter().bodyClock(withBody(13 * 60, plan.inZone("America/Los_Angeles")), now) shouldBe "Body 4 h behind"
        }

        @Test
        fun `the next header change is where the rounded reading moves`() {
            // Body from London time to 1 h ahead over 24 h: in sync until +30 min (12 h in), ½ h until +45 min.
            val drifting = plan.copy(
                phase = listOf(
                    PhasePoint(utc("2026-10-10T00:00"), 60, utc("2026-10-10T00:00")),
                    PhasePoint(utc("2026-10-11T00:00"), 120, utc("2026-10-11T00:00")),
                ),
            )

            BodyClockHeader.nextChange(drifting, utc("2026-10-10T00:00")) shouldBe utc("2026-10-10T12:00")
            BodyClockHeader.step(drifting, utc("2026-10-10T12:00")) shouldBe 1
            BodyClockHeader.nextChange(drifting, utc("2026-10-10T12:00")) shouldBe utc("2026-10-10T18:00")
        }

        @Test
        fun `a steady body clock has no next header change`() {
            BodyClockHeader.nextChange(plan.inZone("Asia/Tokyo"), now) shouldBe null
        }

        @Test
        fun `moving to the next plan day's zone changes the header`() {
            // Day 0 in London, day 1 in Tokyo from Tokyo midnight (15:00Z); the body stays on London time.
            val days = planOfDays(listOf(listOf(avoid), listOf(sleep))).let { p ->
                p.copy(days = listOf(p.days[0], p.days[1].copy(zoneId = "Asia/Tokyo")))
            }

            BodyClockHeader.step(days, utc("2026-10-10T12:00")) shouldBe 0
            BodyClockHeader.nextChange(days, utc("2026-10-10T12:00")) shouldBe utc("2026-10-10T15:00")
            formatter().bodyClock(days, utc("2026-10-10T15:00")) shouldBe "Body 8 h behind"
        }

        @Test
        fun `the body clock ignores the device's zone`() {
            val device = java.util.TimeZone.getDefault()
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Rome"))
            try {
                formatter().bodyClock(plan, now) shouldBe "Body clock in sync"
            } finally {
                java.util.TimeZone.setDefault(device)
            }
        }

        @Test
        fun `the Now notification carries the body clock as its subtext`() {
            nowText("Asia/Tokyo").subText shouldBe "Body 8 h behind"
        }
    }

    @Nested
    inner class Travel {
        private val ba7 = advice(Flight, "2026-10-10T12:00", "2026-10-10T18:00", detail = "BA7")
        private val jl44 = advice(Flight, "2026-10-10T20:00", "2026-10-11T02:00", detail = "JL44")
        private val trip = planOf(ba7, jl44, avoid, sleep)

        private fun sub(at: String, route: String? = "LHR → HND") =
            formatter().travelSubText(trip, utc(at), route)

        @Test
        fun `before take-off it names the route and the departure`() {
            sub("2026-10-10T10:00") shouldBe "LHR → HND · Departs 13:00 · Body clock in sync"
        }

        @Test
        fun `on board it says when the leg lands`() {
            sub("2026-10-10T13:00") shouldBe "LHR → HND · Lands 19:00 · Body clock in sync"
        }

        @Test
        fun `between legs it names the next flight`() {
            sub("2026-10-10T19:00") shouldBe "LHR → HND · Next flight 21:00 · Body clock in sync"
        }

        @Test
        fun `after the last landing it says landed`() {
            sub("2026-10-11T03:00", route = null) shouldBe "Landed · Body clock in sync"
        }

        @Test
        fun `route uses an arrow between codes`() {
            formatter().route("LHR", "HND") shouldBe "LHR → HND"
        }
    }

    @Nested
    inner class Redaction {
        private fun redacting() = NotificationTextFormatter(EnglishStrings, Locale.UK, redact = true)

        @Test
        fun `the public Now version keeps times but drops places, flight numbers and tips`() {
            val flight = advice(Flight, "2026-10-10T12:00", "2026-10-10T23:00", detail = "BA7")
            val p = planOf(flight, sleep)
            val at = utc("2026-10-10T13:00")

            val text = redacting().now(NowStateCalculator.compute(p, at)!!, p, at)

            text.title shouldBe "In flight"
            text.text shouldBe "until Sun 00:00"
            text.details shouldContainExactly listOf("Next: Sleep at 18:00")
            text.secondary.shouldBeNull()
            text.tip.shouldBeNull()
            text.subText shouldBe "Body clock in sync"
        }

        @Test
        fun `melatonin becomes a plain plan step without its dose`() {
            val melatonin = advice(Melatonin, "2026-10-10T20:00", detail = "0.5 mg")
            val spec = ReminderSpec(ReminderKind.Moment, melatonin, expiresAt = melatonin.start.plusSeconds(7200))

            val text = redacting().reminder(spec, null, plan, utc("2026-10-10T19:45"))

            text.title shouldBe "Plan step at 21:00"
            text.text shouldBe "Unlock to see details"
            text.bigText shouldBe "Unlock to see details"
        }

        @Test
        fun `simultaneous melatonin is not named in other reminders`() {
            val melatonin = advice(Melatonin, "2026-10-10T14:00", detail = "0.5 mg")
            val spec = ReminderSpec(ReminderKind.Upcoming, avoid, alsoStarting = listOf(melatonin), expiresAt = avoid.end)

            val text = redacting().reminder(spec, null, plan, utc("2026-10-10T13:45"))

            text.title shouldBe "Avoid light at 15:00"
            text.secondary.shouldBeNull()
            text.bigText shouldBe "15:00–18:00"
        }

        @Test
        fun `the travel subtext drops the route`() {
            val flight = advice(Flight, "2026-10-10T12:00", "2026-10-10T18:00", detail = "BA7")
            redacting().travelSubText(planOf(flight), utc("2026-10-10T10:00"), "LHR → HND") shouldBe
                "Departs 13:00 · Body clock in sync"
        }
    }

    @Nested
    inner class Clock {
        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `compact time fits the Live Update chip`(use24Hour: Boolean) {
            val format = ClockFormat(ZoneId.of("America/New_York"), Locale.US, use24Hour)

            format.compact(utc("2026-10-10T03:30")) shouldHaveMaxLength 7 // 23:30 / 11:30PM
            format.compact(utc("2026-10-10T16:05")) shouldHaveMaxLength 7
        }

        @Test
        fun `city names come from IANA ids`() {
            ClockFormat.cityOf("America/Argentina/Buenos_Aires") shouldBe "Buenos Aires"
            ClockFormat.cityOf("Asia/Tokyo") shouldBe "Tokyo"
        }

        @Test
        fun `ranges spanning more than a day name the end day`() {
            ClockFormat(ZoneId.of("UTC"), Locale.UK)
                .range(utc("2026-10-10T10:00"), utc("2026-10-11T12:00"), utc("2026-10-10T09:00")) shouldBe "10:00–Sun 12:00"
        }
    }
}
