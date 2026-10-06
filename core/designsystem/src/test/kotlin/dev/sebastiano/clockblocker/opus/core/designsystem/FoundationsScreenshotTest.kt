package dev.sebastiano.clockblocker.opus.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sebastiano.clockblocker.opus.core.designsystem.component.DotMatrixText
import dev.sebastiano.clockblocker.opus.core.designsystem.component.IataCode
import dev.sebastiano.clockblocker.opus.core.designsystem.component.RollingMetricText
import dev.sebastiano.clockblocker.opus.core.designsystem.component.RollingText
import dev.sebastiano.clockblocker.opus.core.designsystem.component.RollingTimeText
import dev.sebastiano.clockblocker.opus.core.designsystem.component.RouteArcBanner
import dev.sebastiano.clockblocker.opus.core.designsystem.component.RouteArcDefaults
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.TwoClocksDial
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.formatJetLagHours
import dev.sebastiano.clockblocker.opus.core.designsystem.preview.SamplePlan
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.DotMatrixStyle
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.LocalTime

/**
 * Design-system foundations (TODO-UX §1): dot-matrix airport codes, rolling readouts and the route arc banner.
 * Static goldens render with reduce motion (the static carrier); `_mid` goldens freeze a frame mid-transition.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h1000dp-xhdpi")
class FoundationsScreenshotTest : ScreenshotTest() {

    @Test fun iataCodes() = snap("component_iata_codes") { IataCodes() }
    @Test fun iataCodesDark() = snap("component_iata_codes_dark", darkTheme = true) { IataCodes() }
    @Test fun iataCodesFontScale() = snap("component_iata_codes_fontscale_1_5", fontScale = 1.5f) { IataCodes() }

    /** `· · ·` → `HND`, caught while the reveal is sweeping left to right. */
    @Test fun iataRevealMid() = snapMidChange("component_iata_reveal_mid", advanceMillis = 80) { changed ->
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IataCode(if (changed) "HND" else null)
            IataCode(if (changed) "AMS" else "SFO")
        }
    }

    @Test fun rolling() = snap("component_rolling_text") { RollingSpecimens(changed = true) }
    @Test fun rollingDark() = snap("component_rolling_text_dark", darkTheme = true) { RollingSpecimens(changed = true) }
    @Test fun rollingMid() = snapMidChange("component_rolling_text_mid", advanceMillis = 96) { RollingSpecimens(it) }

    @Test fun routeArc() = snap("component_route_arc") { RouteArcs() }
    @Test fun routeArcDark() = snap("component_route_arc_dark", darkTheme = true) { RouteArcs() }
    @Test fun routeArcNightSafe() = snap("component_route_arc_night_safe", nightSafe = true) { RouteArcs() }
    @Test fun routeArcFontScale() = snap("component_route_arc_fontscale_1_5", fontScale = 1.5f) { RouteArcs() }

    /** Destination just picked: the arc is springing up from the horizon and the plane gliding out. */
    @Test fun routeArcLiftingMid() = snapMidChange("component_route_arc_lifting_mid", advanceMillis = 112) { changed ->
        RouteArcBanner(
            origin = "SFO",
            destination = if (changed) "AMS" else null,
            modifier = Modifier.width(368.dp),
            progress = 0.45f,
            apex = RouteArcDefaults.apexForDuration(Duration.ofMinutes(640)),
            originCaption = "San Francisco",
            destinationCaption = if (changed) "Amsterdam" else null,
        )
    }

    /** The dial's jet-lag pill rolling to the next day's offset while the inner ring turns. */
    @Test fun dialWedgeRollingMid() = snapMidChange("dial_wedge_rolling_mid", advanceMillis = 144) { changed ->
        val today = SamplePlan.midAdaptationDial
        val state = if (changed) today else today.copy(bodyAheadMinutes = today.bodyAheadMinutes - 150f)
        TwoClocksDial(state, Modifier.size(328.dp))
    }
}

@Composable
private fun Caption(text: String) =
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun IataCodes() {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.width(368.dp)) {
        Caption("iataDisplay: set, set, empty")
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            IataCode("SFO", color = scheme.onSurface)
            IataCode("AMS", color = scheme.primary)
            IataCode(null, color = scheme.onSurfaceVariant)
        }
        Caption("Baseline-aligned with text")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IataCode("HND", Modifier.alignByBaseline(), color = scheme.onSurface)
            Text("Tokyo Haneda", Modifier.alignByBaseline(), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        }
        Caption("iataLabel")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("LIS", "JFK", "SIN", null).forEach { IataCode(it, style = ClockblockTheme.textStyles.iataLabel, color = scheme.onSurface) }
        }
        Caption("Glyph sheet")
        val sheet = DotMatrixStyle(glyphHeight = 21.sp)
        listOf("ABCDEFGHIJKLM", "NOPQRSTUVWXYZ", "0123456789", "+\u2212: ?\u00B7").forEach {
            DotMatrixText(it, style = sheet, color = scheme.onSurface)
        }
    }
}

@Composable
private fun RollingSpecimens(changed: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.width(368.dp)) {
        Caption("Time 09:59 \u2192 10:00 (every digit, colon still)")
        RollingTimeText(if (changed) LocalTime.of(10, 0) else LocalTime.of(9, 59), style = ClockblockTheme.textStyles.timeDisplay, color = scheme.onSurface)
        Caption("Minute tick 14:20 \u2192 14:21 (one digit)")
        RollingTimeText(if (changed) LocalTime.of(14, 21) else LocalTime.of(14, 20), style = ClockblockTheme.textStyles.timeHeadline, color = scheme.onSurface)
        Caption("Jet lag +3\u00BD h \u2192 +2 h (unit still, rolls down)")
        RollingMetricText(
            if (changed) 2f else 3.5f,
            style = ClockblockTheme.textStyles.timeHeadline,
            color = scheme.primary,
            format = ::formatJetLagHours,
        )
        Caption("Days to adapt 9d \u2192 10d (a digit rolls in)")
        RollingMetricText(if (changed) 10f else 9f, style = ClockblockTheme.textStyles.timeTitle, color = scheme.onSurface, format = { "${it.toInt()}d" })
        Caption("Plain text")
        RollingText(if (changed) "62% adapted" else "58% adapted", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
    }
}

@Composable
private fun RouteArcs() {
    val scheme = MaterialTheme.colorScheme
    val tenHours = RouteArcDefaults.apexForDuration(Duration.ofMinutes(640))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(368.dp)) {
        Caption("Empty")
        RouteArcBanner(origin = null, destination = null)
        Caption("Origin only: flat horizon, plane resting")
        RouteArcBanner(origin = "SFO", destination = null, originCaption = "San Francisco")
        Caption("Route set, before departure")
        RouteArcBanner("SFO", "AMS", apex = tenHours, originCaption = "San Francisco", destinationCaption = "Amsterdam")
        Caption("In flight, 55%")
        RouteArcBanner("LIS", "HND", progress = 0.55f, apex = 1f, originCaption = "Lisbon", destinationCaption = "Tokyo Haneda")
        Caption("Short hop, landed")
        RouteArcBanner("LIS", "OPO", progress = 1f, apex = RouteArcDefaults.apexForDuration(Duration.ofMinutes(55)))
        Caption("Right to left")
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            RouteArcBanner("DXB", "CAI", progress = 0.3f, apex = 0.6f, originCaption = "Dubai", destinationCaption = "Cairo")
        }
        Caption("On a card")
        val container = scheme.surfaceContainerHigh
        Card(colors = CardDefaults.cardColors(containerColor = container)) {
            RouteArcBanner(
                "JFK", "SIN",
                modifier = Modifier.padding(16.dp),
                progress = 0.2f,
                apex = 1f,
                originCaption = "New York",
                destinationCaption = "Singapore",
                colors = RouteArcDefaults.colors(background = container),
            )
        }
    }
}
