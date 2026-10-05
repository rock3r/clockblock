package dev.sebastiano.clockblocker.opus.core.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.TwoClocksDial
import dev.sebastiano.clockblocker.opus.core.designsystem.preview.SamplePlan
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h400dp-xhdpi")
class DialScreenshotTest : ScreenshotTest() {

    @Test
    fun preTrip() = snap("dial_pre_trip") { TwoClocksDial(SamplePlan.preTripDial, Modifier.size(328.dp)) }

    @Test
    fun midAdaptation() = snap("dial_mid_adaptation") { TwoClocksDial(SamplePlan.midAdaptationDial, Modifier.size(328.dp)) }

    @Test
    fun midAdaptationDark() =
        snap("dial_mid_adaptation_dark", darkTheme = true) { TwoClocksDial(SamplePlan.midAdaptationDial, Modifier.size(328.dp)) }

    @Test
    fun adapted() = snap("dial_adapted") { TwoClocksDial(SamplePlan.adaptedDial, Modifier.size(328.dp)) }

    @Test
    fun nightSafe() = snap("dial_night_safe", nightSafe = true) { TwoClocksDial(SamplePlan.nightDial, Modifier.size(328.dp)) }

    @Test
    fun twelveHour() =
        snap("dial_mid_adaptation_12h", use24Hour = false) { TwoClocksDial(SamplePlan.midAdaptationDial, Modifier.size(328.dp)) }

    @Test
    fun fontScale150() =
        snap("dial_mid_adaptation_fontscale_1_5", fontScale = 1.5f) { TwoClocksDial(SamplePlan.midAdaptationDial, Modifier.size(328.dp)) }
}
