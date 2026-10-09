package dev.sebastiano.clockblocker.opus.feature.trips.editor

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PlaceResultTitleTest {
    @Test
    fun `city and country are joined after the flag`() {
        placeResultTitle(flag = "🇵🇹", city = "Lisbon", country = "Portugal") shouldBe "🇵🇹 Lisbon · Portugal"
    }

    @Test
    fun `a city state is named once`() {
        placeResultTitle(flag = "🇸🇬", city = "Singapore", country = "Singapore") shouldBe "🇸🇬 Singapore"
        placeResultTitle(flag = "🇭🇰", city = "Hong Kong", country = "hong kong") shouldBe "🇭🇰 Hong Kong"
    }

    @Test
    fun `missing parts leave no stray separators`() {
        placeResultTitle(flag = "", city = "Lisbon", country = "Portugal") shouldBe "Lisbon · Portugal"
        placeResultTitle(flag = "🇵🇹", city = "Lisbon", country = "") shouldBe "🇵🇹 Lisbon"
        placeResultTitle(flag = "", city = "Lisbon", country = "") shouldBe "Lisbon"
    }
}
