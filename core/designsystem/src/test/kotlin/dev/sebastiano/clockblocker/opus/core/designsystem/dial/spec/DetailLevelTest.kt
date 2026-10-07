package dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec

import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.numericFloat
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DetailLevelTest {

    @Test
    fun `levels follow the dial's smaller side in dp`() {
        DetailLevel.forSize(48f) shouldBe DetailLevel.Glance
        DetailLevel.forSize(109.9f) shouldBe DetailLevel.Glance
        DetailLevel.forSize(110f) shouldBe DetailLevel.Simple
        DetailLevel.forSize(249.9f) shouldBe DetailLevel.Simple
        DetailLevel.forSize(250f) shouldBe DetailLevel.Full
        DetailLevel.forSize(420f) shouldBe DetailLevel.Full
    }

    @Test
    fun `a bigger dial never shows less detail`() = runTest {
        checkAll(Arb.numericFloat(1f, 1000f), Arb.numericFloat(0f, 500f)) { side, extra ->
            (DetailLevel.forSize(side + extra) >= DetailLevel.forSize(side)) shouldBe true
        }
    }
}
