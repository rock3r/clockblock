package dev.sebastiano.clockblocker.opus.core.designsystem.dial.concepts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DESIGN EXPLORATION (issue #46): renders the three dial concepts as contact sheets. Not a product golden; the
 * PNGs live under src/test/screenshots/concepts/ so the exploration can be rebuilt and compared later.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w1000dp-h1600dp-xhdpi")
class DialConceptsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun sheet(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        android.provider.Settings.System.putString(
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver,
            android.provider.Settings.System.TIME_12_24,
            "24",
        )
        compose.setContent { Box(Modifier.wrapContentSize().testTag("sheet")) { content() } }
        compose.onNodeWithTag("sheet").captureRoboImage("src/test/screenshots/concepts/$name.png")
    }

    @Test
    fun twoSkies() = sheet("concept_1_two_skies") { ConceptSheet(Concept.Skies) }

    @Test
    fun twoHands() = sheet("concept_2_two_hands") { ConceptSheet(Concept.Hands) }

    @Test
    fun twoStrips() = sheet("concept_3_two_strips") { ConceptSheet(Concept.Strips) }

    @Test
    @Config(qualifiers = "w1880dp-h1400dp-hdpi")
    fun comparison() = sheet("concepts_comparison") { ComparisonSheet() }
}
