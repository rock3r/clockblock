package dev.sebastiano.clockblocker.opus.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w400dp-h1400dp-xhdpi")
class TypographyScreenshotTest : ScreenshotTest() {

    @Test
    fun typeSpecimen() = snap("typography_specimen") { Specimen() }

    @Test
    fun typeSpecimenOpus() = snap("typography_specimen_opus", opusMode = true) { Specimen() }

    @Test
    fun typeSpecimenFontScale150() = snap("typography_specimen_fontscale_1_5", fontScale = 1.5f) { Specimen() }
}

@Composable
private fun Specimen() {
    val t = MaterialTheme.typography
    val o = ClockblockTheme.textStyles
    val c = MaterialTheme.colorScheme.onSurface
    Column(Modifier.width(368.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("14:20", style = o.timeDisplay, color = c)
        Text("09:20 body", style = o.bodyClockTitle, color = MaterialTheme.colorScheme.primary)
        Text("Clockblocked.", style = o.editorialDisplay, color = c)
        Text("Display large", style = t.displayLarge, color = c)
        Text("Display emphasized", style = t.displayMediumEmphasized, color = c)
        Text("Headline · Lisbon → Tokyo", style = t.headlineMedium, color = c)
        Text("Headline emphasized", style = t.headlineMediumEmphasized, color = c)
        Text("Title large · Day 2 · body 5 h behind", style = t.titleLarge, color = c)
        Text("Title medium emphasized", style = t.titleMediumEmphasized, color = c)
        Text(
            "Body large. Sunglasses on, even if it feels silly. Especially if it feels silly.",
            style = t.bodyLarge,
            color = c,
        )
        Text("Body medium emphasized · 0123456789", style = t.bodyMediumEmphasized, color = c)
        Text("LABEL LARGE · UNTIL 15:00", style = t.labelLarge, color = c)
        Text("Your body thinks it's 04:12. Be gentle with it.", style = o.editorialBody, color = c)
    }
}
