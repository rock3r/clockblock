package dev.sebastiano.clockblocker.opus.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.AdviceGlyph
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.label
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.shortInstruction
import dev.sebastiano.clockblocker.opus.core.designsystem.component.BodyClockSky
import dev.sebastiano.clockblocker.opus.core.designsystem.component.ConfettiCanvas
import dev.sebastiano.clockblocker.opus.core.designsystem.component.DualTimeText
import dev.sebastiano.clockblocker.opus.core.designsystem.component.ShapeLoadingIndicator
import dev.sebastiano.clockblocker.opus.core.designsystem.component.WavyAdaptationIndicator
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.WindowLightArt
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.NowCardShape
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

private val Tokyo = ZoneId.of("Asia/Tokyo")
private val Lisbon = ZoneId.of("Europe/Lisbon")
private val Now = Instant.parse("2026-10-12T05:20:00Z") // 14:20 Tokyo, 06:20 Lisbon

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h900dp-xhdpi")
class ComponentScreenshotTest : ScreenshotTest() {

    @Test fun skies() = snap("component_body_clock_sky") { Skies() }
    @Test fun skiesNightSafe() = snap("component_body_clock_sky_night_safe", nightSafe = true) { Skies() }
    @Test fun wavy() = snap("component_wavy_adaptation") { Wavy() }
    @Test fun dualTime() = snap("component_dual_time") { DualTimes() }
    @Test fun dualTime12h() = snap("component_dual_time_12h", use24Hour = false) { DualTimes() }
    @Test fun confetti() = snap("component_confetti") { ConfettiCanvas({ 0.32f }, Modifier.size(360.dp, 320.dp)) }
    @Test fun loading() = snap("component_loading") { ShapeLoadingIndicator() }
}

/** The design system applied to a representative screen, in every theme variant and at font scale 1.5. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h1100dp-xhdpi")
class ThemeScreenshotTest : ScreenshotTest() {
    @Test fun light() = snap("theme_light") { SampleScreen() }
    @Test fun dark() = snap("theme_dark", darkTheme = true) { SampleScreen() }
    @Test fun nightSafe() = snap("theme_night_safe", nightSafe = true) { SampleScreen() }
    @Test fun opus() = snap("theme_opus", opusMode = true) { SampleScreen() }
    @Test fun fontScale150() = snap("theme_light_fontscale_1_5", fontScale = 1.5f) { SampleScreen() }
}

@Composable
private fun Skies() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("04:10", "06:50", "13:00", "19:20", "21:30").forEach { t ->
            BodyClockSky(
                LocalTime.parse(t),
                Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(24.dp)),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Lisbon \u2192 Tokyo", style = MaterialTheme.typography.titleLarge, color = LocalContentColor.current)
                    Text("Body $t", style = OpusTheme.textStyles.bodyClockLabel, color = LocalContentColor.current)
                }
            }
        }
    }
}

@Composable
private fun Wavy() {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.width(360.dp)) {
        listOf(Triple(0.05f, 1f, "Day 0"), Triple(0.4f, 0.65f, "Day 2"), Triple(0.75f, 0.3f, "Day 3"), Triple(1f, 0f, "Adapted")).forEach { (p, m, label) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                WavyAdaptationIndicator(progress = p, misalignment = m, modifier = Modifier.fillMaxWidth(), remaining = "2 days")
            }
        }
    }
}

@Composable
private fun DualTimes() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DualTimeText(Now, Tokyo, Lisbon, style = OpusTheme.textStyles.timeDisplay)
        DualTimeText(Now, Tokyo, Lisbon, inline = true)
        // Lisbon 23:00 on the 10th → Tokyo 07:00 on the 11th: a "+1" day suffix.
        DualTimeText(Instant.parse("2026-10-10T22:00:00Z"), Lisbon, Tokyo, inline = true)
        DualTimeText(Instant.parse("2026-10-10T22:00:00Z"), Tokyo, Lisbon, inline = true)
        DualTimeText(Now, Tokyo, null)
    }
}

@Composable
private fun SampleScreen() {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.width(368.dp)) {
        BodyClockSky(LocalTime.of(5, 20), Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(28.dp))) {
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Text("Lisbon \u2192 Tokyo", style = MaterialTheme.typography.headlineMediumEmphasized, color = LocalContentColor.current)
                Text("Day 2 \u00B7 body \u22128 h", style = OpusTheme.textStyles.bodyClockLabel, color = LocalContentColor.current)
            }
        }
        // The Now card.
        val advice = OpusTheme.adviceColors[AdviceType.SeeBrightLight]
        Card(shape = NowCardShape, colors = CardDefaults.cardColors(containerColor = advice.container, contentColor = advice.onContainer)) {
            Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                AdviceGlyph(AdviceType.SeeBrightLight, active = true, size = 56.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Now", style = MaterialTheme.typography.labelLarge)
                    Text(AdviceType.SeeBrightLight.label(), style = MaterialTheme.typography.titleLargeEmphasized)
                    Text(AdviceType.SeeBrightLight.shortInstruction(), style = MaterialTheme.typography.bodyMedium)
                    DualTimeText(Now, Tokyo, Lisbon, inline = true, color = advice.onContainer, secondaryColor = advice.onContainer.copy(alpha = 0.75f))
                }
                WindowLightArt(Modifier.size(72.dp), animated = false)
            }
        }
        // Up next.
        listOf(AdviceType.AvoidCaffeine, AdviceType.Nap, AdviceType.Sleep).forEach { type ->
            Card(colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer)) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    AdviceGlyph(type, active = false, size = 40.dp)
                    Text(type.label(), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
                    Text("17:30", style = OpusTheme.textStyles.timeLabel, color = scheme.onSurfaceVariant)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("62% adapted", style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
            WavyAdaptationIndicator(0.62f, 0.4f, Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {}) { Text("Add trip") }
            FilledTonalButton(onClick = {}) { Text("Edit") }
            OutlinedButton(onClick = {}) { Text("Why?") }
        }
        Box(Modifier.fillMaxWidth().background(scheme.surfaceContainerHigh, RoundedCornerShape(28.dp)).padding(20.dp)) {
            Column {
                Text("Clockblocked.", style = OpusTheme.textStyles.editorialHeadline, color = scheme.onSurface)
                Text("Your body is on Tokyo time.", style = OpusTheme.textStyles.editorialBody, color = scheme.onSurfaceVariant)
            }
        }
    }
}
