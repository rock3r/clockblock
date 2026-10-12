package dev.sebastiano.clockblocker.opus.feature.onboarding

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalTime

/** The sample notification's second-zone time comes from the zones, not a fixed offset. */
class NotificationPreviewSampleTest {
    @Test
    fun `in Lisbon's summer the Tokyo 18-00 block ends at 10-00 there`() {
        sampleUntilInOtherZone(Instant.parse("2026-07-01T03:00:00Z")) shouldBe LocalTime.of(10, 0)
    }

    @Test
    fun `in Lisbon's winter the same block ends at 09-00 there`() {
        sampleUntilInOtherZone(Instant.parse("2026-11-02T03:00:00Z")) shouldBe LocalTime.of(9, 0)
    }
}
