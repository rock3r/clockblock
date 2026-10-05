package dev.sebastiano.clockblocker.opus.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.BloomArt
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.GreatCircleArt
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.LittleAndOftenArt
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.NightCapsuleArt
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.PillowMoonArt
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.PowerNapArt
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.RunningLowArt
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.ShadesOnArt
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.SuitcaseOClockArt
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.TwoClocksArt
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.WindowLightArt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val Arts: List<Pair<String, @Composable (Modifier) -> Unit>> = listOf(
    "two_clocks" to { m -> TwoClocksArt(m) },
    "two_clocks_adapted" to { m -> TwoClocksArt(m, progress = 1f) },
    "shades_on" to { m -> ShadesOnArt(m) },
    "window_light" to { m -> WindowLightArt(m) },
    "pillow_moon" to { m -> PillowMoonArt(m) },
    "night_capsule" to { m -> NightCapsuleArt(m) },
    "little_and_often" to { m -> LittleAndOftenArt(m) },
    "little_and_often_avoid" to { m -> LittleAndOftenArt(m, avoid = true) },
    "great_circle" to { m -> GreatCircleArt(m) },
    "power_nap" to { m -> PowerNapArt(m) },
    "running_low" to { m -> RunningLowArt(m) },
    "bloom" to { m -> BloomArt(m) },
    "suitcase_oclock" to { m -> SuitcaseOClockArt(m) },
)

/** One golden per illustration (light, at 2× board size) plus contact sheets per theme variant. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w420dp-h900dp-xhdpi")
class IllustrationScreenshotTest : ScreenshotTest() {

    private fun art(name: String) = snap("art_$name") { Arts.first { it.first == name }.second(Modifier.size(240.dp)) }

    @Test fun twoClocks() = art("two_clocks")
    @Test fun twoClocksAdapted() = art("two_clocks_adapted")
    @Test fun shadesOn() = art("shades_on")
    @Test fun windowLight() = art("window_light")
    @Test fun pillowMoon() = art("pillow_moon")
    @Test fun nightCapsule() = art("night_capsule")
    @Test fun littleAndOften() = art("little_and_often")
    @Test fun littleAndOftenAvoid() = art("little_and_often_avoid")
    @Test fun greatCircle() = art("great_circle")
    @Test fun powerNap() = art("power_nap")
    @Test fun runningLow() = art("running_low")
    @Test fun bloom() = art("bloom")
    @Test fun suitcaseOClock() = art("suitcase_oclock")

    @Test fun sheetLight() = snap("art_sheet_light") { ArtSheet() }
    @Test fun sheetDark() = snap("art_sheet_dark", darkTheme = true) { ArtSheet() }
    @Test fun sheetNightSafe() = snap("art_sheet_night_safe", nightSafe = true) { ArtSheet() }
    @Test fun sheetOpus() = snap("art_sheet_opus", opusMode = true) { ArtSheet() }
}

@Composable
private fun ArtSheet() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Arts.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (name, art) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        art(Modifier.size(120.dp))
                        Text(
                            name.replace('_', ' '),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(120.dp),
                        )
                    }
                }
            }
        }
    }
}
