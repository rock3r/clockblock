package dev.sebastiano.clockblocker.opus.feature.plan

import dev.sebastiano.clockblocker.opus.core.designsystem.time.TimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceReason
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** "Next: Avoid caffeine in 1 h 20 min", or its day and time when it's more than a day off, or nothing at all. */
class NextActionTest {

    private val now = Instant.parse("2026-06-14T12:00:00Z")

    private fun advice(id: String, type: AdviceType, startsIn: Duration, length: Duration = Duration.ofHours(2)) =
        Advice(id, type, now.plus(startsIn), now.plus(startsIn).plus(length), AdviceReason.TravelMarker)

    @Test
    fun `the next action is the earliest one still to start`() {
        val later = advice("later", AdviceType.Sleep, Duration.ofHours(5))
        val sooner = advice("sooner", AdviceType.AvoidCaffeine, Duration.ofMinutes(80))
        val running = advice("running", AdviceType.SeeLight, Duration.ofHours(-1))
        val next = nextAction(listOf(later, running, sooner), now)!!
        next.advice.id shouldBe "sooner"
        next.startsIn shouldBe Duration.ofMinutes(80)
        next.soon shouldBe true
    }

    @Test
    fun `flights are not actions`() {
        val flight = advice("flight", AdviceType.Flight, Duration.ofMinutes(30))
        val light = advice("light", AdviceType.SeeBrightLight, Duration.ofHours(3))
        nextAction(listOf(flight, light), now)!!.advice.id shouldBe "light"
    }

    @Test
    fun `more than a day away it is no longer a countdown`() {
        nextAction(listOf(advice("a", AdviceType.AvoidLight, Duration.ofHours(24))), now)!!.soon shouldBe true
        nextAction(listOf(advice("a", AdviceType.AvoidLight, Duration.ofHours(24).plusMinutes(1))), now)!!.soon shouldBe false
    }

    @Test
    fun `nothing once the last action has started`() {
        nextAction(listOf(advice("last", AdviceType.SeeLight, Duration.ofMinutes(-10))), now).shouldBeNull()
        nextAction(emptyList(), now).shouldBeNull()
    }

    @Test
    fun `ties go to the more important advice`() {
        val caffeine = advice("caffeine", AdviceType.Caffeine, Duration.ofHours(1))
        val light = advice("light", AdviceType.SeeBrightLight, Duration.ofHours(1))
        nextAction(listOf(caffeine, light), now)!!.advice.id shouldBe "light"
    }

    @Test
    fun `its time reads in local time with the other zone, and the other zone's day when it differs`() {
        val formatter = TimeFormatter(is24Hour = true, locale = Locale.UK)
        val la = ZoneId.of("America/Los_Angeles")
        val tokyo = ZoneId.of("Asia/Tokyo")
        // 20:00 in Los Angeles on Sun 14 Jun is 12:00 on Mon 15 Jun in Tokyo.
        val start = Instant.parse("2026-06-15T03:00:00Z")
        nextActionTime(start, la, tokyo, formatter, withDay = false) shouldBe NextActionTime("20:00", "12:00 Tokyo +1")
        nextActionTime(start, la, tokyo, formatter, withDay = true) shouldBe NextActionTime("Sun 14 Jun 20:00", "12:00 Tokyo +1")
        // Same day in both zones: no marker.
        val morning = Instant.parse("2026-06-14T16:00:00Z")
        nextActionTime(morning, la, ZoneId.of("Europe/London"), formatter, withDay = false) shouldBe NextActionTime("09:00", "17:00 London")
    }
}
