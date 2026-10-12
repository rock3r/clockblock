package dev.sebastiano.clockblocker.opus.feature.plan

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Test

/** The header sky's ambient session is rare: at most once per [AmbientSkyGap], however often the plan is opened. */
class AmbientSkyGateTest {
    private val now = Instant.parse("2026-06-15T10:00:00Z")

    @Test
    fun `never played means it is due`() {
        ambientSkyDue(lastSession = null, now = now).shouldBeTrue()
    }

    @Test
    fun `reopening the plan soon after a session doesn't replay it`() {
        ambientSkyDue(now.minus(Duration.ofMinutes(3)), now).shouldBeFalse()
        ambientSkyDue(now.minus(AmbientSkyGap).plusSeconds(60), now).shouldBeFalse()
    }

    @Test
    fun `it comes back once the gap has passed`() {
        ambientSkyDue(now.minus(AmbientSkyGap), now).shouldBeTrue()
        ambientSkyDue(now.minus(Duration.ofDays(3)), now).shouldBeTrue()
    }

    @Test
    fun `a session stamped in the future (the clock moved back) doesn't block it for long`() {
        ambientSkyDue(now.plus(Duration.ofMinutes(5)), now).shouldBeFalse()
        ambientSkyDue(now.plus(AmbientSkyGap).plusSeconds(60), now).shouldBeTrue()
    }
}
