package dev.sebastiano.clockblocker.opus.core.notifications.text

import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.AvoidLight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Flight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Melatonin
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.SeeBrightLight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Sleep
import dev.sebastiano.clockblocker.opus.core.notifications.advice
import dev.sebastiano.clockblocker.opus.core.notifications.now.NowStateCalculator
import dev.sebastiano.clockblocker.opus.core.notifications.planOf
import dev.sebastiano.clockblocker.opus.core.notifications.schedule.ReminderKind
import dev.sebastiano.clockblocker.opus.core.notifications.schedule.ReminderSpec
import dev.sebastiano.clockblocker.opus.core.notifications.utc
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

    private fun formatter(zone: String, use24Hour: Boolean = true) =
        NotificationTextFormatter(EnglishStrings, ClockFormat(ZoneId.of(zone), Locale.UK, use24Hour))

    private fun nowText(zone: String, at: java.time.Instant = now, use24Hour: Boolean = true, p: dev.sebastiano.clockblocker.opus.core.model.JetLagPlan = plan) =
        formatter(zone, use24Hour).now(NowStateCalculator.compute(p, at)!!, p, at)

    @Nested
    inner class Now {
        @Test
        fun `title is the advice label, text says until and then in the current zone`() {
            val text = nowText("Europe/London")

            text.title shouldBe "Avoid light"
            text.text shouldBe "until 18:00 · then Sleep 18:00–02:00"
        }

        @Test
        fun `secondary line repeats it in the destination zone`() {
            nowText("Europe/London").secondary shouldBe "Tokyo: until 02:00 · then Sleep 02:00–10:00"
        }

        @Test
        fun `at the destination the secondary zone is home`() {
            val text = nowText("Asia/Tokyo")

            text.text shouldBe "until 02:00 · then Sleep 02:00–10:00"
            text.secondary shouldBe "London: until 18:00 · then Sleep 18:00–02:00"
        }

        @Test
        fun `no secondary line when every zone agrees`() {
            val sameZones = planOf(avoid, sleep, origin = "Asia/Seoul", destination = "Asia/Tokyo")

            nowText("Asia/Tokyo", p = sameZones).secondary.shouldBeNull()
        }

        @Test
        fun `12-hour clocks are respected`() {
            // en-GB writes "pm" in lower case; the day period comes from the locale.
            nowText("America/New_York", use24Hour = false).text shouldBe "until 1:00 pm · then Sleep 1:00 pm–9:00 pm"
        }

        @Test
        fun `times on another day get a weekday`() {
            val longSleep = planOf(advice(Sleep, "2026-10-10T20:00", "2026-10-11T08:00"))

            nowText("Europe/London", utc("2026-10-10T22:00"), p = longSleep).text shouldBe "until Sun 09:00"
        }

        @Test
        fun `a logged outcome leads the sentence`() {
            val state = NowStateCalculator.compute(plan, now)!!.copy(outcome = AdviceOutcome.Done)

            formatter("Europe/London").now(state, plan, now).text shouldBe "Done · until 18:00 · then Sleep 18:00–02:00"
        }

        @Test
        fun `in a gap the next window is named`() {
            val gappy = planOf(advice(SeeBrightLight, "2026-10-10T08:00", "2026-10-10T10:00"), sleep)
            val text = nowText("Europe/London", utc("2026-10-10T12:00"), p = gappy)

            text.title shouldBe "Nothing right now"
            text.text shouldBe "Next: Sleep 18:00–02:00"
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

            nowText("Europe/London", utc("2026-10-25T00:45"), p = dst).text shouldBe
                "until 02:30 · then See bright light 02:30–04:00"
            ClockFormat(ZoneId.of("Europe/London"), Locale.UK)
                .range(utc("2026-10-25T00:30"), utc("2026-10-25T02:30"), utc("2026-10-25T00:30")) shouldBe "01:30–02:30"
        }
    }

    @Nested
    inner class Reminders {
        @Test
        fun `upcoming reminder uses absolute times, never a stale 'in 15 min'`() {
            val spec = ReminderSpec(ReminderKind.Upcoming, avoid, expiresAt = avoid.end)

            val early = formatter("Europe/London").reminder(spec, null, plan, utc("2026-10-10T13:45"))
            val late = formatter("Europe/London").reminder(spec, null, plan, utc("2026-10-10T14:05"))

            early.title shouldBe "Avoid light at 15:00"
            early.text shouldBe "15:00–18:00"
            early.secondary shouldBe "Tokyo: 23:00–02:00"
            late.title shouldBe "Avoid light now"
        }

        @Test
        fun `wake-up says what to do now`() {
            val light = advice(SeeBrightLight, "2026-10-11T01:00", "2026-10-11T03:00")
            val morning = planOf(sleep, light)
            val at = utc("2026-10-11T01:00")
            val spec = ReminderSpec(ReminderKind.WakeUp, sleep, expiresAt = at.plusSeconds(3600))

            val text = formatter("Asia/Tokyo").reminder(spec, NowStateCalculator.compute(morning, at), morning, at)

            text.title shouldBe "Sleep over"
            text.text shouldBe "Now: See bright light · until 12:00"
            text.tip shouldBe "tip:SeeBrightLight"
        }

        @Test
        fun `melatonin moment shows its dose`() {
            val melatonin = advice(Melatonin, "2026-10-10T20:00", detail = "0.5 mg")
            val spec = ReminderSpec(ReminderKind.Moment, melatonin, expiresAt = melatonin.start.plusSeconds(7200))

            val text = formatter("Europe/London").reminder(spec, null, plan, utc("2026-10-10T20:00"))

            text.title shouldBe "Melatonin now"
            text.text shouldBe "0.5 mg · tip:Melatonin"
        }

        @Test
        fun `snoozed reminder restates until when`() {
            val spec = ReminderSpec(ReminderKind.Snoozed, avoid, expiresAt = avoid.end)

            formatter("Europe/London").reminder(spec, null, plan, utc("2026-10-10T15:15")).let {
                it.title shouldBe "Reminder: Avoid light"
                it.text shouldBe "until 18:00"
            }
        }

        @Test
        fun `simultaneous reminders are listed`() {
            val spec = ReminderSpec(ReminderKind.Upcoming, avoid, alsoStarting = listOf(sleep), expiresAt = avoid.end)

            formatter("Europe/London").reminder(spec, null, plan, utc("2026-10-10T13:45")).tip shouldBe
                "tip:AvoidLight\nAlso: Sleep"
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
