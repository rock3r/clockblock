package dev.sebastiano.clockblocker.opus.core.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.StaticTwoSkiesDial
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.TwoSkiesDial
import dev.sebastiano.clockblocker.opus.core.model.BodyRingMode
import dev.sebastiano.clockblocker.opus.core.designsystem.preview.SamplePlan
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.EightBitMode
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w360dp-h400dp-xhdpi")
class DialScreenshotTest : ScreenshotTest() {

    @Test
    fun preTrip() = snap("dial_pre_trip") { TwoSkiesDial(SamplePlan.preTripDial, Modifier.size(328.dp)) }

    @Test
    fun midAdaptation() = snap("dial_mid_adaptation") { TwoSkiesDial(SamplePlan.midAdaptationDial, Modifier.size(328.dp)) }

    @Test
    fun midAdaptationDark() =
        snap("dial_mid_adaptation_dark", darkTheme = true) { TwoSkiesDial(SamplePlan.midAdaptationDial, Modifier.size(328.dp)) }

    @Test
    fun adapted() = snap("dial_adapted") { TwoSkiesDial(SamplePlan.adaptedDial, Modifier.size(328.dp)) }

    @Test
    fun nightSafe() = snap("dial_night_safe", nightSafe = true) { TwoSkiesDial(SamplePlan.nightDial, Modifier.size(328.dp)) }

    @Test
    fun opus() = snap("dial_opus", opusMode = true) { TwoSkiesDial(SamplePlan.midAdaptationDial, Modifier.size(328.dp)) }

    @Test
    fun eightBit() = snap("dial_8bit") { EightBitMode(true) { TwoSkiesDial(SamplePlan.midAdaptationDial, Modifier.size(328.dp)) } }

    @Test
    fun twelveHour() =
        snap("dial_mid_adaptation_12h", use24Hour = false) { TwoSkiesDial(SamplePlan.midAdaptationDial, Modifier.size(328.dp)) }

    @Test
    fun fontScale150() =
        snap("dial_mid_adaptation_fontscale_1_5", fontScale = 1.5f) { TwoSkiesDial(SamplePlan.midAdaptationDial, Modifier.size(328.dp)) }

    /** The planner's biological night on the body ring (the per-widget "Precise" option, #52). */
    @Test
    fun precise() = snap("dial_precise") {
        TwoSkiesDial(SamplePlan.midAdaptationDial, Modifier.size(328.dp), bodyRing = BodyRingMode.Precise)
    }

    /** The hand scrubbed four hours ahead: the next block is in focus and a dot marks where now is. */
    @Test
    fun scrubbed() = snap("dial_scrubbed") {
        StaticTwoSkiesDial(SamplePlan.midAdaptationDial, Modifier.size(328.dp), scrubMinutes = 240f)
    }

    /** Mid-turn into the next day's offset: the body sky, its labels and the offset words move as one. */
    @Test
    fun dayRotationMid() = snapMidChange("dial_day_rotation_mid", advanceMillis = 240) { changed ->
        val today = SamplePlan.midAdaptationDial
        val state = if (changed) today else today.copy(bodyAheadMinutes = today.bodyAheadMinutes - 150f)
        TwoSkiesDial(state, Modifier.size(328.dp))
    }
}
