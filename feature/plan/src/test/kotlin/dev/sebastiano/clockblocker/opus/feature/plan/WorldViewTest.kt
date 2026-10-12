package dev.sebastiano.clockblocker.opus.feature.plan

import android.content.Context
import android.provider.Settings
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.format.DateTimeFormatter

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
