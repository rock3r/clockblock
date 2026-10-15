package dev.sebastiano.clockblocker.opus.core.designsystem.illustration

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.testing.invalidateAllViews
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Each chronotype's own entrance (the reveal's layered landscape) lands exactly on the scene at rest. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w400dp-h800dp-xxhdpi")
class ChronotypeSceneEntranceTest {
    @get:Rule
    val compose = createComposeRule()

    private var chronotype by mutableStateOf(Chronotype.DefiniteMorning)
    private var own by mutableStateOf(true)
    private var entrance by mutableFloatStateOf(1f)
    private var drawn by mutableStateOf(true)

    private fun show() = compose.setContent {
        ClockblockTheme(dynamicColor = false, reduceMotion = true) {
            val pose = chronotype.skyPose()
            ChronotypeScene(
                chronotype = chronotype.takeIf { own },
                hour = { pose.hour },
                position = { pose.position },
                moonFraction = { if (pose.evening) 1f else 0f },
                entrance = { entrance },
                sunRotation = { 0.3f },
                twinkle = { 0.6f },
                drawn = { drawn },
                cornerRadius = 32.dp,
                modifier = Modifier.size(180.dp).testTag(Scene),
            )
        }
    }

    /** The scene's pixels, with the chronotype's own entrance or the plain one, in the same place (gradients dither by position). */
    private fun pixels(withOwnEntrance: Boolean): IntArray {
        own = withOwnEntrance
        compose.waitForIdle()
        compose.invalidateAllViews()
        return compose.onNodeWithTag(Scene).captureToImage().toPixelMap().let { map ->
            IntArray(map.width * map.height) { map.buffer[map.bufferOffset + it % map.width + it / map.width * map.stride] }
        }
    }

    @Test
    fun `every chronotype's entrance ends exactly on the scene at rest`() {
        show()
        for (type in Chronotype.entries) {
            chronotype = type
            (pixels(withOwnEntrance = true) contentEquals pixels(withOwnEntrance = false)) shouldBe true
        }
    }

    @Test
    fun `halfway in, every chronotype's entrance is its own`() {
        show()
        entrance = 0.5f
        for (type in Chronotype.entries) {
            chronotype = type
            (pixels(withOwnEntrance = true) contentEquals pixels(withOwnEntrance = false)) shouldBe false
        }
    }

    @Test
    fun `when drawn is false the scene skips drawing and paints identically once drawn becomes true`() {
        drawn = false
        show()
        compose.waitForIdle()
        compose.invalidateAllViews()
        val hidden = compose.onNodeWithTag(Scene).captureToImage().toPixelMap().let { map ->
            IntArray(map.width * map.height) { map.buffer[map.bufferOffset + it % map.width + it / map.width * map.stride] }
        }
        hidden.toSet().size shouldBe 1

        drawn = true
        for (type in Chronotype.entries) {
            chronotype = type
            val revealed = pixels(withOwnEntrance = true)
            revealed.toSet().size shouldBe (revealed.toSet().size.coerceAtLeast(2))
            (revealed contentEquals pixels(withOwnEntrance = false)) shouldBe true
        }
    }

    private companion object {
        const val Scene = "scene"
    }
}
