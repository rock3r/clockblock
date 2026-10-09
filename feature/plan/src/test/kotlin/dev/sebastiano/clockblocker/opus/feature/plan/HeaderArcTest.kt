package dev.sebastiano.clockblocker.opus.feature.plan

import dev.sebastiano.clockblocker.opus.core.model.Sun
import dev.sebastiano.clockblocker.opus.feature.plan.PlanFixtures.realPlan
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.numericFloat
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

/** The header's sun path and local-time ghost (issue #21). */
class HeaderArcTest {
    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val midsummer = LocalDate.of(2026, 6, 21)
    private val day = HeaderDaylight(sunriseHour = 6f, sunsetHour = 20f)

    @Test
    fun `the header's day is the real sun where the traveller is`() {
        val daylight = HeaderDaylight.of(Sun.on(midsummer, lisbon, 38.77, -9.13), lisbon)
        daylight.sunriseHour shouldBeIn 6.0f..6.5f
        daylight.sunsetHour shouldBeIn 20.8f..21.3f
    }

    @Test
    fun `without a place, or a sun that never sets or rises, the header keeps the default day`() {
        HeaderDaylight.of(null, lisbon) shouldBe HeaderDaylight.Default
        val tromso = ZoneId.of("Europe/Oslo")
        HeaderDaylight.of(Sun.on(midsummer, tromso, 69.65, 18.96), tromso) shouldBe HeaderDaylight.Default
        HeaderDaylight.of(Sun.on(LocalDate.of(2026, 12, 21), tromso, 69.65, 18.96), tromso) shouldBe HeaderDaylight.Default
    }

    @Test
    fun `a plan moment takes the daylight of the trip's place in its zone and date`() {
        val moment = realPlan.momentAt(PlanFixtures.MidAdaptation)
        headerDaylight(null, moment) shouldBe HeaderDaylight.Default
        val london = headerDaylight(PlanFixtures.trip, moment)
        london shouldNotBe HeaderDaylight.Default
        london.sunriseHour shouldBeIn 4.5f..5.0f
        london.sunsetHour shouldBeIn 21.0f..21.5f
    }

    @Test
    fun `the sun rides the arc from sunrise to sunset, the moon from sunset to sunrise`() {
        day.arcPoint(6f) shouldBe ArcPoint(0f, isSun = true)
        day.arcPoint(13f) shouldBe ArcPoint(0.5f, isSun = true)
        day.arcPoint(20f).isSun.shouldBeTrue()
        day.arcPoint(20f).t shouldBe (1f plusOrMinus 1e-6f)
        day.arcPoint(1f) shouldBe ArcPoint(0.5f, isSun = false)
        day.arcPoint(23f).isSun.shouldBeFalse()
    }

    @Test
    fun `every point lies on the arc`() = runTest {
        checkAll(Arb.numericFloat(-48f, 48f)) { hour -> day.arcPoint(hour).t shouldBeIn 0f..1f }
    }

    @Test
    fun `the ghost is joined to the body's sun or moon when both are on the same half, half an hour or more apart`() {
        day.joins(bodyHour = 9f, localHour = 17f).shouldBeTrue()
        day.joins(bodyHour = 9f, localHour = 9.5f).shouldBeTrue()
        day.joins(bodyHour = 9f, localHour = 9.4f).shouldBeFalse()
        day.joins(bodyHour = 23.5f, localHour = 0.25f).shouldBeTrue()
        day.joins(bodyHour = 23.9f, localHour = 0.1f).shouldBeFalse()
    }

    @Test
    fun `the ghost is not joined across sunrise or sunset`() {
        day.joins(bodyHour = 3f, localHour = 11f).shouldBeFalse()
        day.joins(bodyHour = 19f, localHour = 21f).shouldBeFalse()
    }

    @Test
    fun `on the same half the ghost sits at the wall clock's spot on the arc`() {
        day.ghostT(bodyHour = 9f, localHour = 13f) shouldBe 0.5f
        day.ghostT(bodyHour = 23f, localHour = 1f) shouldBe 0.5f
    }

    @Test
    fun `in step, under half an hour apart, the ghost sits exactly on the body's sun or moon`() {
        day.ghostT(bodyHour = 13f, localHour = 13.4f) shouldBe day.arcPoint(13f).t
        day.ghostT(bodyHour = 23.9f, localHour = 0.2f) shouldBe day.arcPoint(23.9f).t
        // Across sunrise too: a few minutes either side of it is still in step.
        day.ghostT(bodyHour = 6.1f, localHour = 5.9f) shouldBe day.arcPoint(6.1f).t
        day.ghostT(bodyHour = 13f, localHour = 13.5f) shouldBe day.arcPoint(13.5f).t
    }

    @Test
    fun `a ghost that would sit inside its own ring of the sun is drawn exactly around it`() {
        // An hour is 1/14 of this day's arc: within a snap of 0.1 it circles the sun, beyond it it stands apart.
        day.ghostT(bodyHour = 13f, localHour = 14f, snapT = 0.1f) shouldBe day.arcPoint(13f).t
        day.ghostT(bodyHour = 13f, localHour = 14f, snapT = 0.05f) shouldBe day.arcPoint(14f).t
        day.ghostT(bodyHour = 13f, localHour = 16f, snapT = 0.1f) shouldBe day.arcPoint(16f).t
        // A wall clock past sunset waits at the end even when the evening sun is near it: hours apart, never in step.
        day.ghostT(bodyHour = 19.5f, localHour = 22.5f, snapT = 0.1f) shouldBe 1f
    }

    @Test
    fun `across sunrise or sunset the ghost waits at the nearer end of the body's half`() {
        // Body just after sunrise, wall clock just before it: the ring waits at the start, by the sun.
        day.ghostT(bodyHour = 7f, localHour = 5.5f) shouldBe 0f
        // Body in the evening sun, wall clock after dark: the ring waits at the sunset end.
        day.ghostT(bodyHour = 19f, localHour = 22.5f) shouldBe 1f
        // Body at night, wall clock mid-morning: nearer to the sunrise end of the night.
        day.ghostT(bodyHour = 2f, localHour = 9f) shouldBe 1f
        day.ghostT(bodyHour = 2f, localHour = 18f) shouldBe 0f
    }
}
