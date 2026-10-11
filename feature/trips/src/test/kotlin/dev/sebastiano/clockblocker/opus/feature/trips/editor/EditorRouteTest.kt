package dev.sebastiano.clockblocker.opus.feature.trips.editor

import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Which legs the editor's globe draws, and how its indices map back to the tickets. */
class EditorRouteTest {

    private fun leg(id: String, from: dev.sebastiano.clockblocker.opus.core.model.Place?, to: dev.sebastiano.clockblocker.opus.core.model.Place?) =
        LegDraft(id, origin = PlaceInput.of(from), destination = PlaceInput.of(to))

    @Test
    fun `a connection is drawn as two legs with three stops`() {
        val route = EditorRoute.of(listOf(leg("a", DemoData.LIS, DemoData.DXB), leg("b", DemoData.DXB, DemoData.HND)))
        route.legs.map { it.from.label to it.to.label } shouldContainExactly listOf("LIS" to "DXB", "DXB" to "HND")
        route.places.map { it.code } shouldContainExactly listOf("LIS", "DXB", "HND")
        route.legIndices shouldContainExactly listOf(0, 1)
    }

    @Test
    fun `legs still missing an airport aren't drawn, and the indices skip them`() {
        val route = EditorRoute.of(listOf(leg("a", DemoData.LIS, null), leg("b", DemoData.DXB, DemoData.HND), leg("c", DemoData.HND, DemoData.HND)))
        route.legIndices shouldContainExactly listOf(1)
        route.globeIndexOf(1) shouldBe 0
        route.globeIndexOf(0).shouldBeNull()
        route.globeIndexOf(2).shouldBeNull()
    }

    @Test
    fun `nothing to draw before any leg has both airports`() {
        EditorRoute.of(listOf(leg("a", null, DemoData.HND))).isEmpty shouldBe true
    }

    @Test
    fun `a disconnected itinerary keeps each leg's own airports`() {
        val route = EditorRoute.of(listOf(leg("a", DemoData.LIS, DemoData.LHR), leg("b", DemoData.JFK, DemoData.LAX)))
        route.legPlaces.map { (a, b) -> a.code to b.code } shouldContainExactly listOf("LIS" to "LHR", "JFK" to "LAX")
        route.places.map { it.code } shouldContainExactly listOf("LIS", "LHR", "JFK", "LAX")
    }
}
