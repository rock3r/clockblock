package dev.sebastiano.clockblocker.opus.core.designsystem.dial

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.float
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalTime
import kotlin.math.abs

class DialGeometryTest {

    @Test
    fun `noon is at the top and midnight at the bottom`() {
        DialGeometry.angleForMinute(12 * 60f) shouldBe (270f plusOrMinus 0.001f)
        DialGeometry.angleForMinute(0f) shouldBe (90f plusOrMinus 0.001f)
    }

    @Test
    fun `time runs clockwise, morning on the left, evening on the right`() {
        DialGeometry.angleForMinute(6 * 60f) shouldBe (180f plusOrMinus 0.001f)
        DialGeometry.angleForMinute(18 * 60f) shouldBe (0f plusOrMinus 0.001f)
        DialGeometry.angleForMinute(15 * 60f) shouldBe (315f plusOrMinus 0.001f)
    }

    @Test
    fun `angle and minute round-trip`() = runTest {
        checkAll(Arb.float(0f, 1439.99f).filter { it.isFinite() }) { m ->
            val back = DialGeometry.minuteForAngle(DialGeometry.angleForMinute(m))
            abs(DialGeometry.minuteDelta(m, back)) shouldBe (0f plusOrMinus 0.01f)
        }
    }

    @Test
    fun `point angles follow canvas coordinates (y down)`() {
        DialGeometry.angleOf(0f, -1f) shouldBe (270f plusOrMinus 0.001f) // above centre → noon
        DialGeometry.minuteForAngle(DialGeometry.angleOf(0f, 1f)) shouldBe (0f plusOrMinus 0.01f) // below → midnight
        DialGeometry.minuteForAngle(DialGeometry.angleOf(-1f, 0f)) shouldBe (360f plusOrMinus 0.01f) // left → 06:00
    }

    @Test
    fun `minute delta takes the short way round`() {
        DialGeometry.minuteDelta(23 * 60f, 60f) shouldBe (120f plusOrMinus 0.001f)
        DialGeometry.minuteDelta(60f, 23 * 60f) shouldBe (-120f plusOrMinus 0.001f)
        DialGeometry.angleDelta(350f, 10f) shouldBe (20f plusOrMinus 0.001f)
    }

    @Test
    fun `relative minutes cover 8 h of past and 16 h of future`() {
        DialGeometry.relativeMinute(14 * 60f, 8 * 60f) shouldBe (-360f plusOrMinus 0.01f)
        DialGeometry.relativeMinute(14 * 60f, 2 * 60f) shouldBe (720f plusOrMinus 0.01f) // tonight, not "12 h ago"
        DialGeometry.relativeMinute(14 * 60f, 6 * 60f) shouldBe (-480f plusOrMinus 0.01f)
        DialGeometry.relativeMinute(14 * 60f, 5 * 60f + 59f) shouldBe (959f plusOrMinus 0.01f)
    }

    @Test
    fun `body ring maps body minutes back to local minutes`() {
        val state = DialState(
            instant = java.time.Instant.EPOCH,
            displayZoneId = "UTC",
            localMinute = 14 * 60f,
            bodyAheadMinutes = -8 * 60f,
        )
        state.bodyTime shouldBe LocalTime.of(6, 0)
        state.localMinuteForBody(0f) shouldBe (8 * 60f plusOrMinus 0.01f) // body midnight happens at 08:00 local
        state.jetLagHours shouldBe (8f plusOrMinus 0.001f)
    }

    @Test
    fun `scrubbing moves local and body readouts together`() {
        val state = DialState(java.time.Instant.EPOCH, "UTC", localMinute = 23 * 60f, bodyAheadMinutes = -300f)
        val scrubbed = state.scrubbedTo(90f)
        scrubbed.localTime shouldBe LocalTime.of(0, 30)
        scrubbed.bodyTime shouldBe LocalTime.of(19, 30)
        scrubbed.instant shouldBe java.time.Instant.EPOCH.plusSeconds(90 * 60)
    }

    @Test
    fun `jet lag label rounds to half hours`() {
        formatJetLagHours(5f) shouldBe "+5 h"
        formatJetLagHours(4.6f) shouldBe "+4\u00BD h"
        formatJetLagHours(-2.1f) shouldBe "\u22122 h"
        formatJetLagHours(0.4f) shouldBe "+\u00BD h"
        formatJetLagHours(0.1f) shouldBe "0 h"
    }

    @Test
    fun `the wedge shows the body clock relative to local time`() {
        // Flew east: body 3½ h behind local → "−3½ h", the same quantity the header says in words.
        wedgeLabelText(-210f) shouldBe "\u22123\u00BD h"
        wedgeLabelText(120f) shouldBe "+2 h"
        DialState(java.time.Instant.EPOCH, "UTC", 0f, bodyAheadMinutes = -210f).bodyOffsetHours shouldBe (-3.5f plusOrMinus 0.001f)
    }

    @Test
    fun `magnitudes are unsigned half hours`() {
        formatHoursMagnitude(-3.5f) shouldBe "3\u00BD h"
        formatHoursMagnitude(8f) shouldBe "8 h"
        formatHoursMagnitude(-0.4f) shouldBe "\u00BD h"
    }

    @Test
    fun `direction uses the dial's alignment threshold`() {
        bodyOffsetDirection(-3.5f) shouldBe BodyOffsetDirection.Behind
        bodyOffsetDirection(2f) shouldBe BodyOffsetDirection.Ahead
        bodyOffsetDirection(0.49f) shouldBe BodyOffsetDirection.InSync
        bodyOffsetDirection(-0.49f) shouldBe BodyOffsetDirection.InSync
        bodyOffsetDirection(-0.5f) shouldBe BodyOffsetDirection.Behind
    }
}
