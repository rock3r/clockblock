package dev.sebastiano.clockblocker.opus.feature.trips.list

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.feature.trips.TripsSamples
import dev.sebastiano.clockblocker.opus.feature.trips.TripsScreenshotTest
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Taps through the stateless trips list and checks every affordance reaches the right callback. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w411dp-h891dp-xhdpi")
class TripsContentTest : TripsScreenshotTest() {

    private val calls = mutableListOf<String>()

    private val callbacks = TripsCallbacks(
        onOpenTrip = { calls += "open:$it" },
        onNewTrip = { calls += "new" },
        onOpenSettings = { calls += "settings" },
        onEditTrip = { calls += "edit:$it" },
        onCreateReturnTrip = { calls += "return:$it" },
        onTryDemo = { calls += "demo" },
        onDelete = { calls += "delete:$it" },
        onDuplicate = { calls += "duplicate:$it" },
    )

    private fun show(state: TripsUiState = TripsSamples.state()) = setContent { TripsContent(state, callbacks) }

    @Test
    fun emptyStateOffersPlanningAndADemo() {
        show(TripsSamples.state(trips = emptyList()))
        compose.onNodeWithTag(TripsTestTags.EmptyState).assertIsDisplayed()
        compose.onNodeWithTag(TripsTestTags.EmptyPlanTrip).performClick()
        compose.onNodeWithTag(TripsTestTags.EmptyDemoTrip).performClick()
        compose.onNodeWithTag(TripsTestTags.Fab).assertDoesNotExist()
        calls shouldContainExactly listOf("new", "demo")
    }

    @Test
    fun fabMenuOffersNewReturnAndDemoTrips() {
        show()
        compose.onNodeWithTag(TripsTestTags.Fab).performClick()
        compose.onNodeWithTag(TripsTestTags.ReturnTrip).assertIsDisplayed()
        compose.onNodeWithTag(TripsTestTags.DemoTrip).assertIsDisplayed()
        compose.onNodeWithTag(TripsTestTags.NewTrip).performClick()
        calls shouldContainExactly listOf("new")
    }

    @Test
    fun fabCarriesALabelUntilItOpens() {
        // Labels beside glyphs: closed, the FAB reads "New trip" next to its plus; open, it is the close button.
        show()
        // The visible label sits under clearAndSetSemantics in the library, so the name comes from the description.
        compose.onNodeWithTag(TripsTestTags.Fab).assert(hasContentDescription("New trip"))
        compose.onNode(hasText("New trip") and hasAnyAncestor(hasTestTag(TripsTestTags.Fab)), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(TripsTestTags.Fab).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(TripsTestTags.Fab).assert(hasContentDescription("Close menu"))
        compose.onNodeWithTag(TripsTestTags.Fab).assert(hasContentDescription("New trip").not())
    }

    @Test
    fun shortWindowsMoveTheAddMenuIntoTheAppBar() {
        // Landscape phone (#15): no floating button over the cards; the same entries sit behind an app bar action.
        setContent(qualifiers = "w540dp-h411dp") { TripsContent(TripsSamples.state(), callbacks) }
        compose.onNodeWithTag(TripsTestTags.Fab).performClick()
        compose.onNodeWithTag(TripsTestTags.ReturnTrip).assertIsDisplayed()
        compose.onNodeWithTag(TripsTestTags.DemoTrip).assertIsDisplayed()
        compose.onNodeWithTag(TripsTestTags.NewTrip).performClick()
        calls shouldContainExactly listOf("new")
        val fab = compose.onNodeWithTag(TripsTestTags.Fab).fetchSemanticsNode().boundsInRoot
        val firstCard = compose.onNodeWithTag(TripsTestTags.tripCard(DemoData.SfoLhrId)).fetchSemanticsNode().boundsInRoot
        (fab.bottom <= firstCard.top) shouldBe true
    }

    @Test
    fun tappingACardOpensItsPlan() {
        show()
        compose.onNodeWithTag(TripsTestTags.List).performScrollToNode(hasTestTag(TripsTestTags.tripCard(DemoData.LhrSydId)))
        compose.onNodeWithTag(TripsTestTags.tripCard(DemoData.LhrSydId)).performClick()
        calls shouldContainExactly listOf("open:${DemoData.LhrSydId}")
    }

    @Test
    fun cardMenuReachesEditDuplicateReturnAndDelete() {
        show()
        val id = DemoData.LhrSydId
        compose.onNodeWithTag(TripsTestTags.List).performScrollToNode(hasTestTag(TripsTestTags.tripCard(id)))
        listOf(
            TripsTestTags.MenuEdit,
            TripsTestTags.MenuDuplicate,
            TripsTestTags.MenuReturn,
            TripsTestTags.MenuDelete,
        ).forEach { item ->
            compose.onNodeWithTag(TripsTestTags.tripMenu(id)).performClick()
            compose.onNodeWithTag(item).performClick()
            compose.waitForIdle()
        }
        calls shouldContainExactly listOf("edit:$id", "duplicate:$id", "return:$id", "delete:$id")
    }

    @Test
    fun settingsGearOnlyShowsWhereThereIsNoSettingsDestination() {
        // The navigation suite always carries Settings, so by default the app bar doesn't duplicate it.
        show()
        compose.onNodeWithTag(TripsTestTags.Settings).assertDoesNotExist()
    }

    @Test
    fun settingsIsReachableFromTheAppBarWhenAsked() {
        setContent { TripsContent(TripsSamples.state(), callbacks, showSettingsAction = true) }
        compose.onNodeWithTag(TripsTestTags.Settings).performClick()
        calls shouldContainExactly listOf("settings")
    }

    private lateinit var backDispatcher: OnBackPressedDispatcher

    private fun showWithBack() = setContent {
        backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
        TripsContent(TripsSamples.state(), callbacks)
    }

    private fun backGesture(progress: Float) = compose.runOnUiThread {
        backDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 600f, 0f, BackEventCompat.EDGE_LEFT))
        backDispatcher.dispatchOnBackProgressed(BackEventCompat(80f, 600f, progress, BackEventCompat.EDGE_LEFT))
    }

    @Test
    fun fabMenuCollapsesWithThePredictiveBackGestureAndCancelReopensIt() {
        showWithBack()
        compose.onNodeWithTag(TripsTestTags.Fab).performClick()
        compose.waitForIdle()
        backGesture(progress = 0.5f)
        // Half-way: the items have shrunk toward the FAB and half-faded, the scrim with them.
        capture("trips_fab_menu_back_half")
        compose.runOnUiThread { backDispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        compose.onNodeWithTag(TripsTestTags.NewTrip).assertIsDisplayed()
        calls shouldContainExactly emptyList()
    }

    @Test
    fun committingThePredictiveBackGestureClosesTheMenu() {
        showWithBack()
        compose.onNodeWithTag(TripsTestTags.Fab).performClick()
        compose.waitForIdle()
        backGesture(progress = 0.6f)
        compose.runOnUiThread { backDispatcher.onBackPressed() }
        compose.waitForIdle()
        // The collapsed M3 menu keeps its items laid out at zero width.
        compose.onNodeWithTag(TripsTestTags.NewTrip).assertIsNotDisplayed()
        compose.onNodeWithTag(TripsTestTags.Fab).assertIsDisplayed()
    }
}
