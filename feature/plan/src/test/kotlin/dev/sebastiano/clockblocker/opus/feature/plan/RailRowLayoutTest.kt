package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The rail's time lines at the width a block row's text column really gets on a 411 dp phone (review of #149: at
 * the default font size a "3 h 30 min" duration dropped to its own line behind a stray " ·").
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w411dp-h891dp-xxhdpi")
class RailRowLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun timeLine(fontScale: Float, range: String = "10:00 – 13:30", duration: String = "3 h 30 min") {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ClockblockTheme(dynamicColor = false, reduceMotion = true) {
                    // The text column of a block row: the window less the row's paddings, the time and rail columns
                    // and the gap (RailRowLayout); the check-off slot only narrows the title above.
                    val window = LocalConfiguration.current.screenWidthDp.dp
                    val text = window - (RowOuterPadding + RowInnerPadding) * 2 - rememberTimeColumnWidth() - RailColumnWidth - RailTextGap
                    Box(Modifier.width(text)) {
                        RangeAndDuration(range, duration, ClockblockTheme.textStyles.timeLabel, MaterialTheme.colorScheme.onSurface, Color.Gray)
                    }
                }
            }
        }
    }

    @Test
    fun `range and duration share a line at the default font size`() {
        timeLine(fontScale = 1f)
        compose.onNodeWithTag(InlineSeparatorTag, useUnmergedTree = true).assertIsDisplayed()
        val range = compose.onNodeWithText(railRange("10:00 – 13:30")).getUnclippedBoundsInRoot()
        val duration = compose.onNodeWithText(railDuration("3 h 30 min")).getUnclippedBoundsInRoot()
        // Baseline-aligned on one line.
        (duration.top < range.bottom && range.top < duration.bottom) shouldBe true
        (duration.left >= range.right) shouldBe true
    }

    @Test
    fun `a large font moves the whole duration down with no separator left behind`() {
        timeLine(fontScale = 1.5f, range = "10:00 PM – 1:30 PM")
        compose.onNodeWithTag(InlineSeparatorTag, useUnmergedTree = true).assertIsNotDisplayed()
        val range = compose.onNodeWithText(railRange("10:00 PM – 1:30 PM")).getUnclippedBoundsInRoot()
        val duration = compose.onNodeWithText(railDuration("3 h 30 min")).getUnclippedBoundsInRoot()
        (duration.top >= range.bottom) shouldBe true
        duration.left shouldBe range.left
    }

    @Test
    fun `the other zone's city moves whole under its times`() {
        compose.setContent {
            ClockblockTheme(dynamicColor = false, reduceMotion = true) {
                Box(Modifier.width(150.dp)) {
                    ZonedTimes("02:00 – 02:30", "Los Angeles", MaterialTheme.typography.bodySmall, Color.Gray)
                }
            }
        }
        val range = compose.onNodeWithText(railRange("02:00 – 02:30")).getUnclippedBoundsInRoot()
        val city = compose.onNodeWithText("Los Angeles").getUnclippedBoundsInRoot()
        (city.top >= range.bottom) shouldBe true
        // One line, not "Los" / "Angeles".
        ((city.bottom - city.top) < (range.bottom - range.top) * 1.5f) shouldBe true
    }
}
