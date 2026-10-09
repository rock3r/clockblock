package dev.sebastiano.clockblocker.opus.core.designsystem.dial

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
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
}
