package dev.sebastiano.clockblocker.opus.feature.trips.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import dev.sebastiano.clockblocker.opus.feature.trips.R
import dev.sebastiano.clockblocker.opus.feature.trips.TripsScreenshotTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Which copy the preview card picks for the planner's result. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class ShiftPreviewCardTest : TripsScreenshotTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun homeTimePlansExplainHomeTimeRatherThanNoJetLag() {
        // The planner reports a home-time plan as no shift (0 h, direction None), even for a long-haul trip.
        setContent {
            ShiftPreviewCard(ShiftPreview(0.0, ShiftDirection.None, AdaptationStrategy.StayOnHomeTime, 0.0, 0.0))
        }
        compose.onNodeWithText(context.getString(R.string.editor_preview_home)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.editor_preview_no_shift)).assertDoesNotExist()
    }

    @Test
    fun smallShiftsSayNoJetLag() {
        setContent {
            ShiftPreviewCard(ShiftPreview(0.5, ShiftDirection.None, AdaptationStrategy.Adapt, 0.0, 0.0))
        }
        compose.onNodeWithText(context.getString(R.string.editor_preview_no_shift)).assertIsDisplayed()
    }
}
