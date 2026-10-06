package dev.sebastiano.clockblocker.opus.navigation

import dev.sebastiano.clockblocker.opus.core.model.DeepLinks
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DeepLinkParserTest {

    @Test
    fun `trips opens the trips list`() {
        DeepLinkParser.parse(DeepLinks.TRIPS) shouldBe
            DeepLinkTarget(TopLevelDestination.Trips, listOf(TripsRoute))
    }

    @Test
    fun `new trip opens the editor on top of the trips list`() {
        DeepLinkParser.parse(DeepLinks.NEW_TRIP) shouldBe
            DeepLinkTarget(TopLevelDestination.Trips, listOf(TripsRoute, TripEditorRoute()))
    }

    @Test
    fun `current plan opens the Now destination`() {
        DeepLinkParser.parse(DeepLinks.CURRENT_PLAN) shouldBe
            DeepLinkTarget(TopLevelDestination.Now, listOf(NowRoute))
    }

    @Test
    fun `a trip plan gets the trips list as its synthetic parent`() {
        DeepLinkParser.parse(DeepLinks.plan("lisbon-tokyo")) shouldBe
            DeepLinkTarget(TopLevelDestination.Trips, listOf(TripsRoute, PlanRoute("lisbon-tokyo")))
    }

    @Test
    fun `any uuid trip id round-trips`() = runTest {
        checkAll(Arb.uuid()) { uuid ->
            val id = uuid.toString()
            DeepLinkParser.parse(DeepLinks.plan(id))?.backStack shouldBe listOf(TripsRoute, PlanRoute(id))
        }
    }

    @Test
    fun `percent-encoded ids are decoded`() {
        DeepLinkParser.parse("clockblock://plan/trip%20one")?.backStack shouldBe
            listOf(TripsRoute, PlanRoute("trip one"))
    }

    @Test
    fun `trailing slashes, queries and fragments are ignored`() {
        DeepLinkParser.parse("clockblock://trips/")?.backStack shouldBe listOf(TripsRoute)
        DeepLinkParser.parse("clockblock://plan/abc?source=widget#now")?.backStack shouldBe
            listOf(TripsRoute, PlanRoute("abc"))
    }

    @Test
    fun `scheme and host are case-insensitive, ids are not`() {
        DeepLinkParser.parse("Clockblock://PLAN/AbC")?.backStack shouldBe listOf(TripsRoute, PlanRoute("AbC"))
        DeepLinkParser.parse("clockblock://Plan/Current")?.destination shouldBe TopLevelDestination.Now
    }

    @Test
    fun `links with the pre-rename scheme still open`() {
        DeepLinkParser.parse("opusclockblock://plan/abc")?.backStack shouldBe listOf(TripsRoute, PlanRoute("abc"))
        DeepLinkParser.parse("OpusClockblock://trips/new")?.backStack shouldBe listOf(TripsRoute, TripEditorRoute())
        DeepLinkParser.parse("opusclockblock://plan/current")?.destination shouldBe TopLevelDestination.Now
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "not a uri",
            "https://trips",
            "clockblock:trips",
            "clockblock://",
            "clockblock://settings",
            "clockblock://plan",
            "clockblock://plan/",
            "clockblock://plan/a/b",
            "clockblock://plan/%zz",
            "clockblock://trips/old",
            "clockblock://trips/new/extra",
        ],
    )
    fun `anything else is not a deep link`(uri: String) {
        DeepLinkParser.parse(uri).shouldBeNull()
    }

    @Test
    fun `null is not a deep link`() {
        DeepLinkParser.parse(null).shouldBeNull()
    }
}
