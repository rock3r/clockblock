package dev.sebastiano.clockblocker.opus.core.designsystem.theme

import androidx.compose.animation.core.TweenSpec
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class StepMotionTest {
    private val motion = ClockblockMotion(MotionScheme.expressive(), reduceMotion = false)

    private fun delayOf(index: Int, lag: Int = 0): Int =
        motion.stepFadeIn<Float>(index, lag).shouldBeInstanceOf<TweenSpec<Float>>().delay

    @Test
    fun `the lag holds a chrome group back after the unlagged one, keeping its stagger`() {
        val lag = ClockblockMotion.StepTopBarLagMillis
        delayOf(0, lag) shouldBe delayOf(0) + lag
        delayOf(2, lag) - delayOf(0, lag) shouldBe 2 * ClockblockMotion.NavigationStaggerMillis
    }

    @Test
    fun `the top bar lag is a distinct but short beat`() {
        (ClockblockMotion.StepTopBarLagMillis in 150..200) shouldBe true
    }
}
