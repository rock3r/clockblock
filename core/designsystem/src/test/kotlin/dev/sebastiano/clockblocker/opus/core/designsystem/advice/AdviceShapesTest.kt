package dev.sebastiano.clockblocker.opus.core.designsystem.advice

import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test

class AdviceShapesTest {
    @Test
    fun `morph, mark and pixel morphs are cached per advice type`() {
        AdviceShapes.warmUp()
        for (type in AdviceType.entries) {
            val morph = AdviceShapes.morph(type)
            AdviceShapes.morph(type) shouldBeSameInstanceAs morph
            morph.start shouldBe AdviceShapes.start(type)
            morph.end shouldBe AdviceShapes.target(type)

            val mark = AdviceShapes.mark(type)
            AdviceShapes.mark(type) shouldBeSameInstanceAs mark
            mark.start shouldBe AdviceShapes.target(type)
            mark.end shouldBe AdviceShapes.target(type)

            val pixelActive = AdviceShapes.pixelFromActive(type)
            AdviceShapes.pixelFromActive(type) shouldBeSameInstanceAs pixelActive
            pixelActive.start shouldBe AdviceShapes.target(type)
            pixelActive.end shouldBe AdviceShapes.pixel(type)

            val pixelIdle = AdviceShapes.pixelFromIdle(type)
            AdviceShapes.pixelFromIdle(type) shouldBeSameInstanceAs pixelIdle
            pixelIdle.start shouldBe AdviceShapes.start(type)
            pixelIdle.end shouldBe AdviceShapes.pixel(type)
        }
    }
}
