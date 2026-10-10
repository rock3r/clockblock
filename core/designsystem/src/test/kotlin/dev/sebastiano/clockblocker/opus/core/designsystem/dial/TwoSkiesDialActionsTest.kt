package dev.sebastiano.clockblocker.opus.core.designsystem.dial

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.designsystem.R
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** TalkBack's block actions on the dial report the instants the cards will agree with. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37])
class TwoSkiesDialActionsTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    // New York, 1 November 2026, 00:30 EDT. Sleep until 01:30 EST, the second 01:30 (an hour of the face away).
    private val now = Instant.parse("2026-11-01T04:30:00Z")
    private val end = Instant.parse("2026-11-01T06:30:00Z")
    private val fallBack = DialState(
        instant = now,
        displayZoneId = "America/New_York",
        localMinute = 30f,
        bodyAheadMinutes = 0f,
        arcs = persistentListOf(
            DialArc("sleep", AdviceType.Sleep, startMinute = 30f, sweepMinutes = 60f, startInstant = now, endInstant = end),
        ),
    )

    @Test
    fun `Next block lands on a block's real end inside the repeated hour of a fall-back night (#94)`() {
        val reported = mutableListOf<Instant>()
        compose.setContent {
            ClockblockTheme {
                TwoSkiesDial(fallBack, Modifier.size(320.dp).testTag("dial"), onScrub = { reported += it })
            }
        }

        compose.onNodeWithTag("dial").performCustomAccessibilityActionWithLabel(context.getString(R.string.dial_action_next_block))
        compose.waitForIdle()

        reported.shouldNotBeEmpty()
        reported.last() shouldBe end
        // The dial reads the same instant as the cards: two real hours on, the body clock (in step at 00:30) is 2:30.
        compose.onNodeWithTag("dial").assertContentDescriptionContains("Your body clock is 2:30 AM", substring = true)
    }

    @Test
    fun `a configuration change keeps the landing in the repeated hour`() {
        val reported = mutableListOf<Instant>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            ClockblockTheme {
                TwoSkiesDial(fallBack, Modifier.size(320.dp).testTag("dial"), onScrub = { reported += it })
            }
        }
        compose.onNodeWithTag("dial").performCustomAccessibilityActionWithLabel(context.getString(R.string.dial_action_next_block))
        compose.waitForIdle()

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        reported.last() shouldBe end
        compose.onNodeWithTag("dial").assertContentDescriptionContains("Your body clock is 2:30 AM", substring = true)
    }

    // At the first 01:30 (EDT), a block that started at 01:00 EDT ends at the second 01:30 (EST): on the face, now.
    private val firstRun = Instant.parse("2026-11-01T05:30:00Z")
    private val endsAtNowOnTheFace = fallBack.copy(
        instant = firstRun,
        localMinute = 90f,
        arcs = persistentListOf(
            DialArc("sleep", AdviceType.Sleep, startMinute = 60f, sweepMinutes = 30f, startInstant = Instant.parse("2026-11-01T05:00:00Z"), endInstant = end),
        ),
    )

    /** Previous then Next: the hand back at offset 0, landed on the second 01:30. */
    private fun landAtNowInTheSecondRun() {
        compose.onNodeWithTag("dial").performCustomAccessibilityActionWithLabel(context.getString(R.string.dial_action_previous_block))
        compose.waitForIdle()
        compose.onNodeWithTag("dial").performCustomAccessibilityActionWithLabel(context.getString(R.string.dial_action_next_block))
        compose.waitForIdle()
    }

    @Test
    fun `a configuration change re-reports a landing in the repeated hour with the hand at now`() {
        val reported = mutableListOf<Instant>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            ClockblockTheme { TwoSkiesDial(endsAtNowOnTheFace, Modifier.size(320.dp).testTag("dial"), onScrub = { reported += it }) }
        }
        landAtNowInTheSecondRun()
        reported.last() shouldBe end

        // The host's preview isn't saved: the restored dial tells it again where the hand is.
        reported.clear()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        reported.shouldNotBeEmpty()
        reported.last() shouldBe end
    }

    @Test
    fun `tapping the centre returns a landing in the repeated hour with the hand at now to now`() {
        val reported = mutableListOf<Instant>()
        compose.setContent {
            ClockblockTheme { TwoSkiesDial(endsAtNowOnTheFace, Modifier.size(320.dp).testTag("dial"), onScrub = { reported += it }) }
        }
        landAtNowInTheSecondRun()
        reported.last() shouldBe end

        compose.onNodeWithTag("dial").performTouchInput { click(center) }
        compose.waitForIdle()

        reported.last() shouldBe firstRun
        compose.onNodeWithTag("dial").assertContentDescriptionContains("Your body clock is 1:30 AM", substring = true)
    }

    @Test
    fun `when now moves on, the landing moves with the hand`() {
        var state by mutableStateOf(fallBack)
        compose.setContent {
            ClockblockTheme { TwoSkiesDial(state, Modifier.size(320.dp).testTag("dial"), onScrub = {}) }
        }
        compose.onNodeWithTag("dial").performCustomAccessibilityActionWithLabel(context.getString(R.string.dial_action_next_block))
        compose.waitForIdle()

        // Five minutes on, the hand (still an hour of the face ahead) is at the second 01:35: 2:35 on the body clock.
        state = fallBack.copy(instant = now.plusSeconds(5 * 60L), localMinute = 35f)
        compose.waitForIdle()

        compose.onNodeWithTag("dial").assertContentDescriptionContains("1:35 AM local. Your body clock is 2:35 AM", substring = true)
    }

    @Test
    fun `when now moves on while the hand is still travelling, the landing and the cards agree`() {
        var state by mutableStateOf(fallBack)
        val reported = mutableListOf<Instant>()
        compose.setContent {
            ClockblockTheme { TwoSkiesDial(state, Modifier.size(320.dp).testTag("dial"), onScrub = { reported += it }) }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("dial").performCustomAccessibilityActionWithLabel(context.getString(R.string.dial_action_next_block))
        compose.mainClock.advanceTimeByFrame()

        // The minute ticks over mid-flight: the landing resolved against 00:30 must still be the second 01:30 run.
        state = fallBack.copy(instant = now.plusSeconds(5 * 60L), localMinute = 35f)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        reported.last() shouldBe end.plusSeconds(5 * 60L)
        compose.onNodeWithTag("dial").assertContentDescriptionContains("1:35 AM local. Your body clock is 2:35 AM", substring = true)
    }

    @Test
    fun `when now moves past the change itself, the hand reads the face again`() {
        var state by mutableStateOf(fallBack)
        compose.setContent {
            ClockblockTheme { TwoSkiesDial(state, Modifier.size(320.dp).testTag("dial"), onScrub = {}) }
        }
        compose.onNodeWithTag("dial").performCustomAccessibilityActionWithLabel(context.getString(R.string.dial_action_next_block))
        compose.waitForIdle()

        // 01:00 EST, 90 real minutes on (the body, in step at 00:30 EDT, now an hour ahead): the hand an hour of the
        // face ahead is at 02:00 EST, not 90 minutes past the landing.
        state = fallBack.copy(instant = Instant.parse("2026-11-01T06:00:00Z"), localMinute = 60f, bodyAheadMinutes = 60f)
        compose.waitForIdle()

        compose.onNodeWithTag("dial").assertContentDescriptionContains("2:00 AM local. Your body clock is 3:00 AM", substring = true)
    }
}
