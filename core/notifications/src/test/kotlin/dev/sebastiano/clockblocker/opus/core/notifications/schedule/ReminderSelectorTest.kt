package dev.sebastiano.clockblocker.opus.core.notifications.schedule

import dev.sebastiano.clockblocker.opus.core.model.AdviceType.AvoidLight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Caffeine
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Melatonin
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.SeeBrightLight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Sleep
import dev.sebastiano.clockblocker.opus.core.notifications.advice
import dev.sebastiano.clockblocker.opus.core.notifications.utc
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReminderSelectorTest {

    private val sleep = advice(Sleep, "2026-10-10T23:00", "2026-10-11T07:00")
    private val light = advice(SeeBrightLight, "2026-10-11T07:00", "2026-10-11T09:00")
    private val caffeine = advice(Caffeine, "2026-10-11T07:00", "2026-10-11T08:00")
    private val melatonin = advice(Melatonin, "2026-10-11T07:00")

    private fun t(kind: TransitionKind, a: dev.sebastiano.clockblocker.opus.core.model.Advice, at: String) = Transition(utc(at), kind, a)

    @Test
    fun `wake-up beats a moment beats upcoming windows`() {
        val spec = ReminderSelector.select(
            listOf(
                t(TransitionKind.Upcoming, light, "2026-10-11T07:00"),
                t(TransitionKind.Moment, melatonin, "2026-10-11T07:00"),
                t(TransitionKind.WakeUp, sleep, "2026-10-11T07:00"),
                t(TransitionKind.Start, light, "2026-10-11T07:00"),
            ),
            now = utc("2026-10-11T07:00"),
        ).shouldNotBeNull()

        spec.kind shouldBe ReminderKind.WakeUp
        spec.advice shouldBe sleep
        spec.alsoStarting shouldContainExactly listOf(melatonin, light)
        spec.expiresAt shouldBe utc("2026-10-11T08:00")
    }

    @Test
    fun `several upcoming windows are ordered by advice priority`() {
        val spec = ReminderSelector.select(
            listOf(
                t(TransitionKind.Upcoming, caffeine, "2026-10-11T06:45"),
                t(TransitionKind.Upcoming, light, "2026-10-11T06:45"),
            ),
            now = utc("2026-10-11T06:45"),
        ).shouldNotBeNull()

        spec.advice shouldBe light
        spec.alsoStarting shouldContainExactly listOf(caffeine)
        spec.expiresAt shouldBe utc("2026-10-11T07:30")
    }

    @Test
    fun `silent transitions never produce a reminder`() {
        ReminderSelector.select(listOf(t(TransitionKind.Start, light, "2026-10-11T07:00")), utc("2026-10-11T07:00"))
            .shouldBeNull()
    }

    @Test
    fun `late alarms drop reminders that are no longer true`() {
        val avoid = advice(AvoidLight, "2026-10-10T18:00", "2026-10-10T18:20")
        val upcoming = listOf(t(TransitionKind.Upcoming, avoid, "2026-10-10T17:45"))

        ReminderSelector.select(upcoming, utc("2026-10-10T18:10")).shouldNotBeNull()
        // Window already over (e.g. the phone was off): say nothing rather than something wrong.
        ReminderSelector.select(upcoming, utc("2026-10-10T18:20")).shouldBeNull()
        ReminderSelector.select(listOf(t(TransitionKind.WakeUp, sleep, "2026-10-11T07:00")), utc("2026-10-11T08:01"))
            .shouldBeNull()
        ReminderSelector.select(listOf(t(TransitionKind.Moment, melatonin, "2026-10-11T07:00")), utc("2026-10-11T09:00"))
            .shouldBeNull()
    }

    @Test
    fun `snoozed reminders exist until the advice is over`() {
        ReminderSelector.snoozed(light, utc("2026-10-11T08:00"))!!.kind shouldBe ReminderKind.Snoozed
        ReminderSelector.snoozed(light, utc("2026-10-11T09:00")).shouldBeNull()
        ReminderSelector.snoozed(melatonin, utc("2026-10-11T08:59"))!!.expiresAt shouldBe utc("2026-10-11T09:00")
    }
}
