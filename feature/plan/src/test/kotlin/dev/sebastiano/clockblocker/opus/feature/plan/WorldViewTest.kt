package dev.sebastiano.clockblocker.opus.feature.plan

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeTags
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.feature.plan.PlanFixtures.ready
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.format.DateTimeFormatter
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.model.FlightLeg
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.testing.FakeJetLagPlanner
import java.time.Instant
import java.time.LocalDateTime

/** The world behind the dial: the pinch zoom, its TalkBack equivalents, the caption and the full-screen globe. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w400dp-h880dp-xhdpi")
class WorldViewTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val state = ready(PlanFixtures.MidAdaptation)
    private val trip get() = state.trip.shouldNotBeNull()

    @Before
    fun use24Hour() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
    }

    private fun show(reduceMotion: Boolean = true) {
        compose.setContent { ClockblockTheme(dynamicColor = false, reduceMotion = reduceMotion) { PlanContent(state, PlanActions(onBack = {})) } }
    }

    private fun caption(): String =
        compose.onNodeWithTag(PlanTags.WorldCaption).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    /** Now, where the traveller is, as the caption writes it. */
    private fun nowThere(): String {
        val zone = trip.positionAt(state.now).place.zone
        return state.now.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    @Test
    fun `Show the world swaps the dial for the globe, says where you are now in two zones, and Back to the dial returns`() {
        show()
        compose.onNodeWithTag(PlanTags.Dial).performCustomAccessibilityActionWithLabel("Show the world")
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.World).assertExists()
        val text = caption()
        text shouldContain "It's ${nowThere()}"
        // Local time plus the trip's other zone.
        text shouldContain " · "
        compose.onNodeWithTag(PlanTags.WorldGlobe).performCustomAccessibilityActionWithLabel("Back to the dial")
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.World).assertDoesNotExist()
        compose.onNodeWithTag(PlanTags.Dial).assertExists()
    }

    @Test
    fun `TalkBack lands on the globe, which carries the trip in words and every action`() {
        show()
        compose.onNodeWithTag(PlanTags.Dial).performCustomAccessibilityActionWithLabel("Show the world")
        compose.waitForIdle()
        val globe = compose.onNodeWithTag(PlanTags.WorldGlobe).fetchSemanticsNode()
        // A content description makes the node a TalkBack focus target; the actions ride on it.
        globe.config.getOrNull(SemanticsProperties.ContentDescription).shouldNotBeNull().single() shouldContain " to "
        val labels = globe.config[SemanticsActions.CustomActions].map { it.label }
        labels shouldContain "Back to the dial"
        labels shouldContain "Show the globe full screen"
        labels shouldContain "Turn the globe east"
    }

    @Test
    fun `the caption isn't a live region, so the minute ticking never interrupts TalkBack`() {
        show()
        compose.onNodeWithTag(PlanTags.Dial).performCustomAccessibilityActionWithLabel("Show the world")
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.WorldCaption).fetchSemanticsNode().config.getOrNull(SemanticsProperties.LiveRegion) shouldBe null
        // The world announces itself once instead, as a pane titled with what the caption said when it opened.
        compose.onNodeWithTag(PlanTags.World).fetchSemanticsNode().config[SemanticsProperties.PaneTitle] shouldContain "It's"
    }

    @Test
    fun `pinching out on the dial shows the world, pinching in on it returns`() {
        show()
        compose.onNodeWithTag(PlanTags.Dial).performTouchInput {
            pinch(center - Offset(20f, 0f), center - Offset(150f, 0f), center + Offset(20f, 0f), center + Offset(150f, 0f))
        }
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.World).assertExists()
        compose.onNodeWithTag(PlanTags.World).performTouchInput {
            pinch(center - Offset(150f, 0f), center - Offset(20f, 0f), center + Offset(150f, 0f), center + Offset(20f, 0f))
        }
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.World).assertDoesNotExist()
    }

    @Test
    fun `a pinch that starts with one finger on the ring never scrubs, so the world opens on now`() {
        show()
        compose.onNodeWithTag(PlanTags.Dial).performTouchInput {
            // The first finger lands on the ring (not the hub), the second a moment later: a real pinch.
            val left = centerLeft + Offset(30f, 0f)
            val right = centerRight - Offset(30f, 0f)
            down(0, left)
            advanceEventTime(40)
            down(1, right)
            repeat(8) { step ->
                advanceEventTime(16)
                moveTo(0, left - Offset(12f * (step + 1), 0f))
                moveTo(1, right + Offset(12f * (step + 1), 0f))
            }
            up(0)
            up(1)
        }
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.World).assertExists()
        caption() shouldContain "It's ${nowThere()}"
    }

    @Test
    fun `a second finger landing mid-scrub undoes the scrub`() {
        show()
        compose.onNodeWithTag(PlanTags.Dial).performTouchInput {
            val start = centerLeft + Offset(30f, 0f)
            down(0, start)
            // Past the slop: the scrub starts and previews another time.
            repeat(4) {
                advanceEventTime(16)
                moveBy(0, Offset(0f, -40f))
            }
            down(1, centerRight - Offset(30f, 0f))
            repeat(8) { step ->
                advanceEventTime(16)
                moveBy(0, Offset(-12f, 0f))
                moveBy(1, Offset(12f, 0f))
            }
            up(0)
            up(1)
        }
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.World).assertExists()
        caption() shouldContain "It's ${nowThere()}"
    }

    @Test
    fun `a pinch that starts on the hub keeps a preview instead of treating the first finger as a tap`() {
        show()
        // A preview another time (TalkBack's Next block), then a pinch whose first finger lands on the hub.
        compose.onNodeWithTag(PlanTags.Dial).performCustomAccessibilityActionWithLabel("Next block")
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.Dial).performTouchInput {
            down(0, center - Offset(10f, 0f))
            advanceEventTime(40)
            down(1, center + Offset(60f, 0f))
            repeat(8) { step ->
                advanceEventTime(16)
                moveTo(0, center - Offset(10f + 20f * (step + 1), 0f))
                moveTo(1, center + Offset(60f + 20f * (step + 1), 0f))
            }
            up(0)
            up(1)
        }
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.World).assertExists()
        // The world shows the previewed time, not now: the hub press wasn't a tap back to now.
        caption() shouldNotContain "It's ${nowThere()}"
    }

    @Test
    fun `one finger on the dial still scrubs, and never opens the world`() {
        show()
        compose.onNodeWithTag(PlanTags.Dial).performTouchInput { swipe(centerRight, bottomCenter) }
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.World).assertDoesNotExist()
    }

    @Test
    fun `with motion on, a small pinch springs back to the dial and a full one zooms to the world`() {
        show(reduceMotion = false)
        compose.onNodeWithTag(PlanTags.Dial).performTouchInput {
            // ×1.15 and a slow release: not far enough, so it settles back on the dial.
            down(0, center - Offset(100f, 0f))
            down(1, center + Offset(100f, 0f))
            repeat(10) { step ->
                advanceEventTime(50)
                moveTo(0, center - Offset(100f + 1.5f * (step + 1), 0f))
                moveTo(1, center + Offset(100f + 1.5f * (step + 1), 0f))
            }
            advanceEventTime(200)
            up(0)
            up(1)
        }
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.World).assertDoesNotExist()

        compose.onNodeWithTag(PlanTags.Dial).performTouchInput {
            pinch(center - Offset(20f, 0f), center - Offset(150f, 0f), center + Offset(20f, 0f), center + Offset(150f, 0f))
        }
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.World).assertExists()
        compose.onNodeWithTag(PlanTags.WorldCaption).assertIsDisplayed()
    }

    @Test
    fun `fingers that pause before lifting settle on the nearer side, whatever way they last moved`() {
        show(reduceMotion = false)
        compose.onNodeWithTag(PlanTags.Dial).performTouchInput {
            down(0, center - Offset(100f, 0f))
            down(1, center + Offset(100f, 0f))
            // Out fast to ×1.7, then quickly back to ×1.45 (still most of the way to the world)…
            listOf(130f, 150f, 170f, 160f, 150f, 145f).forEach { d ->
                advanceEventTime(16)
                moveTo(0, center - Offset(d, 0f))
                moveTo(1, center + Offset(d, 0f))
            }
            // …then still for a moment before lifting: no speed left, so the nearer side (the world) wins.
            advanceEventTime(300)
            up(1)
            up(0)
        }
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.World).assertExists()
    }

    @Test
    fun `the full-screen button opens the world over the plan, and its close button closes it`() {
        show()
        compose.onNodeWithTag(PlanTags.Dial).performCustomAccessibilityActionWithLabel("Show the world")
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.WorldFullScreen).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.WorldOverlay).assertIsDisplayed()
        compose.onNodeWithTag(PlanTags.WorldOverlayGlobe).assertIsDisplayed()
        // The plan underneath is out of TalkBack's reach while the globe covers it.
        compose.onNodeWithTag(PlanTags.Dial).assertDoesNotExist()
        compose.onNodeWithTag(GlobeTags.OverlayClose).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.WorldOverlay).assertDoesNotExist()
        compose.onNodeWithTag(PlanTags.World).assertExists()
    }

    @Test
    fun `zones that share an offset still show the other city's time`() {
        val lisbonLondon = Trip(
            id = "lis-lhr",
            title = "Lisbon → London",
            legs = listOf(
                FlightLeg(
                    id = "lis-lhr-1",
                    origin = DemoData.LIS,
                    destination = DemoData.LHR,
                    departureLocal = LocalDateTime.of(2026, 6, 15, 10, 0),
                    arrivalLocal = LocalDateTime.of(2026, 6, 15, 12, 40),
                ),
            ),
            createdAt = Instant.parse("2026-05-15T00:00:00Z"),
        )
        val plan = FakeJetLagPlanner().plan(lisbonLondon, DemoData.profile, DemoData.Now)
        var text = ""
        compose.setContent { ClockblockTheme(dynamicColor = false) { text = whereYouAre(lisbonLondon, plan, Instant.parse("2026-06-16T10:00:00Z")) } }
        compose.waitForIdle()
        text shouldContain "London"
        text shouldContain "Lisbon"
    }

    @Test
    fun `if the trip goes away while the full-screen world is open, the world closes and the plan is reachable again`() {
        var shown by mutableStateOf(state)
        compose.setContent { ClockblockTheme(dynamicColor = false, reduceMotion = true) { PlanContent(shown, PlanActions(onBack = {})) } }
        compose.onNodeWithTag(PlanTags.Dial).performCustomAccessibilityActionWithLabel("Show the world")
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.WorldFullScreen).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.Dial).assertDoesNotExist()
        // E.g. deleted from the Trips pane: the cached plan stays, its trip doesn't.
        shown = state.copy(trip = null)
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.WorldOverlay).assertDoesNotExist()
        compose.onNodeWithTag(PlanTags.Dial).assertExists()
        // A trip coming back doesn't reopen the full-screen world by itself: it's back in the dial's place.
        shown = state
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.WorldOverlay).assertDoesNotExist()
        compose.onNodeWithTag(PlanTags.World).assertExists()
    }

    @Test
    fun `picking a flight on the full-screen globe names it`() {
        show()
        compose.onNodeWithTag(PlanTags.Dial).performCustomAccessibilityActionWithLabel("Show the world")
        compose.waitForIdle()
        compose.onNodeWithTag(PlanTags.WorldFullScreen).performClick()
        compose.waitForIdle()
        val globe = compose.onNodeWithTag(PlanTags.WorldOverlayGlobe)
        val actions = globe.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        actions shouldContain "Zoom in"
        // A flight's action picks it; the callout names it with its airport codes.
        val first = trip.legs.first()
        val legLabel = actions.first { it.contains(first.origin.city.ifBlank { first.origin.name }) }
        globe.performCustomAccessibilityActionWithLabel(legLabel)
        compose.waitForIdle()
        compose.onNodeWithTag(GlobeTags.Callout).assertIsDisplayed()
        compose.onNodeWithText("${first.origin.displayCode} → ${first.destination.displayCode}", useUnmergedTree = true).assertIsDisplayed()
    }
}
