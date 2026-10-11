package dev.sebastiano.clockblocker.opus.feature.plan

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The plan toolbar gets out of the way while you read on, and comes back as soon as you scroll back. */
class ToolbarScrollTest {

    private val threshold = 48f

    @Test
    fun `reading on past the threshold hides the toolbar`() {
        val state = ToolbarScrollState(threshold)
        state.onScrolled(-30f)
        state.hidden shouldBe false
        state.onScrolled(-30f)
        state.hidden shouldBe true
    }

    @Test
    fun `scrolling back past the threshold brings it back`() {
        val state = ToolbarScrollState(threshold)
        state.onScrolled(-100f)
        state.onScrolled(40f)
        state.hidden shouldBe true
        state.onScrolled(20f)
        state.hidden shouldBe false
    }

    @Test
    fun `a change of direction starts counting again`() {
        val state = ToolbarScrollState(threshold)
        // Jitter under a resting finger never toggles it.
        repeat(10) {
            state.onScrolled(-40f)
            state.onScrolled(40f)
        }
        state.hidden shouldBe false
    }

    @Test
    fun `show brings it back and forgets the travel so far`() {
        val state = ToolbarScrollState(threshold)
        state.onScrolled(-100f)
        state.show()
        state.hidden shouldBe false
        state.onScrolled(-30f)
        state.hidden shouldBe false
    }
}
