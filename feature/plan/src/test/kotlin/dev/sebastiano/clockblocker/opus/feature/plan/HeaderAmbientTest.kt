package dev.sebastiano.clockblocker.opus.feature.plan

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The header's ambient plane: when it crosses the sky inside an ambient session, and how its contrail fades. */
class HeaderAmbientTest {

    @Test
    fun `no plane at rest or before it takes off`() {
        planeAt(-1L).shouldBeNull()
        planeAt(PlaneStartMillis - 1L).shouldBeNull()
    }

    @Test
    fun `it crosses at a steady pace with its contrail fully drawn`() {
        val half = planeAt(PlaneStartMillis + PlaneCrossMillis / 2)!!
        half.progress shouldBe (0.5f plusOrMinus 0.001f)
        half.trailAlpha shouldBe 1f
    }

    @Test
    fun `once it has gone the contrail fades, then nothing is left`() {
        val gone = PlaneStartMillis + PlaneCrossMillis
        val fading = planeAt(gone + PlaneTrailFadeMillis / 2)!!
        fading.progress shouldBe 1f
        fading.trailAlpha shouldBe (0.5f plusOrMinus 0.001f)
        planeAt(gone + PlaneTrailFadeMillis + 1L).shouldBeNull()
    }

    @Test
    fun `the whole flight fits inside one session`() {
        (PlaneStartMillis + PlaneCrossMillis + PlaneTrailFadeMillis <= AmbientSessionMillis) shouldBe true
    }
}
