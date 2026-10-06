package dev.sebastiano.clockblocker.opus.feature.trips.editor

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FlagEmojiTest {
    @Test
    fun `country codes become regional indicator flags`() {
        flagEmoji("PT") shouldBe "\uD83C\uDDF5\uD83C\uDDF9"
        flagEmoji("jp") shouldBe "\uD83C\uDDEF\uD83C\uDDF5"
    }

    @Test
    fun `anything that isn't two letters has no flag`() {
        flagEmoji("") shouldBe ""
        flagEmoji("USA") shouldBe ""
        flagEmoji("1A") shouldBe ""
    }
}
