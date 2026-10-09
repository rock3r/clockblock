package dev.sebastiano.clockblocker.opus.feature.trips.editor

import io.kotest.matchers.shouldBe
import java.util.Locale
import org.junit.jupiter.api.Test

class PlaceResultTitleTest {
    @Test
    fun `city and country are joined after the flag`() {
        placeResultTitle(city = "Lisbon", countryCode = "PT", locale = Locale.ENGLISH) shouldBe "🇵🇹 Lisbon · Portugal"
        placeResultTitle(city = "Lisbon", countryCode = "PT", locale = Locale.GERMAN) shouldBe "🇵🇹 Lisbon · Portugal"
    }

    @Test
    fun `a city state is named once`() {
        placeResultTitle(city = "Singapore", countryCode = "SG", locale = Locale.ENGLISH) shouldBe "🇸🇬 Singapore"
        // The real locale data calls it "Hong Kong SAR China", not "Hong Kong".
        placeResultTitle(city = "Hong Kong", countryCode = "HK", locale = Locale.ENGLISH) shouldBe "🇭🇰 Hong Kong"
        placeResultTitle(city = "Macau", countryCode = "MO", locale = Locale.ENGLISH) shouldBe "🇲🇴 Macau"
    }

    @Test
    fun `a city state is named once when the country is localised`() {
        // German: the bundled city data says "Singapore", the device locale says "Singapur".
        placeResultTitle(city = "Singapore", countryCode = "SG", locale = Locale.GERMAN) shouldBe "🇸🇬 Singapore"
    }

    @Test
    fun `a city named like its country, outside the list, is still named once`() {
        placeResultTitle(city = "Djibouti", countryCode = "DJ", locale = Locale.ENGLISH) shouldBe "🇩🇯 Djibouti"
    }

    @Test
    fun `another airport inside a city state keeps the country`() {
        placeResultTitle(city = "Seletar", countryCode = "SG", locale = Locale.ENGLISH) shouldBe "🇸🇬 Seletar · Singapore"
    }

    @Test
    fun `missing parts leave no stray separators`() {
        placeResultTitle(city = "Lisbon", countryCode = "", locale = Locale.ENGLISH) shouldBe "Lisbon"
    }
}
