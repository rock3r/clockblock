package dev.sebastiano.clockblocker.opus.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GeoPoint
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeLeg
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeStop
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.LandDots
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.RouteGlobe
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.RouteGlobeDefaults
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** The route globe: small and full size, light and dark, the active leg, the back of the globe, a polar route. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w411dp-h891dp-xhdpi")
class GlobeScreenshotTest : ScreenshotTest() {

    @Before
    fun loadLand() {
        // The globe loads its land dots off the main thread; load them up front so the capture has them.
        LandDots.load(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun small() = snap("globe_small") { Small() }

    @Test
    fun smallDark() = snap("globe_small_dark", darkTheme = true) { Small() }

    @Test
    fun fullSizeWithLabels() = snap("globe_full_labels") { Full(Connection, active = 1) }

    @Test
    fun fullSizeDark() = snap("globe_full_labels_dark", darkTheme = true) { Full(Connection, active = 0) }

    /** Round the world, framed on the third leg: the earlier legs show through on the back of the globe. */
    @Test
    fun longItinerary() = snap("globe_round_the_world") { Full(RoundTheWorld, active = 2) }

    /** Over the pole and across the date line, with the traveller's position marked. */
    @Test
    fun polarRoute() = snap("globe_polar") {
        Full(Polar, active = null, marker = GeoPoint(80.0, -150.0))
    }

    @Composable
    private fun Small() {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RouteGlobe(legs = Single, instant = Noon, contentDescription = "", modifier = Modifier.size(RouteGlobeDefaults.SmallSize))
            RouteGlobe(legs = Connection, instant = Evening, contentDescription = "", activeLeg = 1, modifier = Modifier.size(RouteGlobeDefaults.SmallSize))
        }
    }

    @Composable
    private fun Full(legs: List<GlobeLeg>, active: Int?, marker: GeoPoint? = null) {
        Column(Modifier.padding(16.dp)) {
            RouteGlobe(
                legs = legs,
                instant = Noon,
                contentDescription = "",
                activeLeg = active,
                showLabels = true,
                marker = marker,
                modifier = Modifier.size(379.dp),
            )
        }
    }

    private companion object {
        val Noon: Instant = Instant.parse("2026-06-21T12:00:00Z")
        val Evening: Instant = Instant.parse("2026-10-07T19:00:00Z")
        val LIS = GlobeStop(GeoPoint(38.78, -9.14), "LIS")
        val DOH = GlobeStop(GeoPoint(25.27, 51.61), "DOH")
        val HND = GlobeStop(GeoPoint(35.55, 139.79), "HND")
        val LHR = GlobeStop(GeoPoint(51.47, -0.46), "LHR")
        val JNB = GlobeStop(GeoPoint(-26.13, 28.24), "JNB")
        val SYD = GlobeStop(GeoPoint(-33.95, 151.18), "SYD")
        val HNL = GlobeStop(GeoPoint(21.32, -157.93), "HNL")
        val SCL = GlobeStop(GeoPoint(-33.39, -70.79), "SCL")
        val JFK = GlobeStop(GeoPoint(40.64, -73.78), "JFK")
        val HKG = GlobeStop(GeoPoint(22.31, 113.92), "HKG")
        val Single = listOf(GlobeLeg(LIS, HND))
        val Connection = listOf(GlobeLeg(LIS, DOH), GlobeLeg(DOH, HND))
        val RoundTheWorld = listOf(GlobeLeg(LHR, JNB), GlobeLeg(JNB, SYD), GlobeLeg(SYD, HNL), GlobeLeg(HNL, SCL), GlobeLeg(SCL, LHR))
        val Polar = listOf(GlobeLeg(JFK, HKG))
    }
}
