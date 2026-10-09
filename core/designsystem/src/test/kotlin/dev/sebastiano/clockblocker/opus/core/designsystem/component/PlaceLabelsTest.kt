package dev.sebastiano.clockblocker.opus.core.designsystem.component

import io.kotest.matchers.shouldBe
import java.util.Locale
import org.junit.jupiter.api.Test

/** The two text lines of the shared airport result row: "🇬🇧 London" over "London Heathrow Airport · United Kingdom". */
class PlaceLabelsTest {
    @Test
    fun `the title is the flag and the city, never the country`() {
        placeResultTitle(city = "Lisbon", countryCode = "PT") shouldBe "🇵🇹 Lisbon"
        placeResultTitle(city = "Los Angeles", countryCode = "US") shouldBe "🇺🇸 Los Angeles"
    }

    @Test
    fun `the title has no stray space without a country`() {
        placeResultTitle(city = "Lisbon", countryCode = "") shouldBe "Lisbon"
    }

    @Test
    fun `the subtitle is the airport and the country`() {
        placeResultSubtitle("London Heathrow Airport", city = "London", countryCode = "GB", locale = Locale.ENGLISH) shouldBe
            "London Heathrow Airport · United Kingdom"
        placeResultSubtitle("Lisbon Airport", city = "Lisbon", countryCode = "PT", locale = Locale.GERMAN) shouldBe
            "Lisbon Airport · Portugal"
    }

    @Test
    fun `a city state is not named again as the country`() {
        placeResultSubtitle("Singapore Changi Airport", city = "Singapore", countryCode = "SG", locale = Locale.ENGLISH) shouldBe
            "Singapore Changi Airport"
        // The real locale data calls it "Hong Kong SAR China", not "Hong Kong".
        placeResultSubtitle("Hong Kong International Airport", city = "Hong Kong", countryCode = "HK", locale = Locale.ENGLISH) shouldBe
            "Hong Kong International Airport"
        placeResultSubtitle("Macau International Airport", city = "Macau", countryCode = "MO", locale = Locale.ENGLISH) shouldBe
            "Macau International Airport"
    }

    @Test
    fun `a city state is not named again when the country is localised`() {
        // German: the bundled city data says "Singapore", the device locale says "Singapur".
        placeResultSubtitle("Singapore Changi Airport", city = "Singapore", countryCode = "SG", locale = Locale.GERMAN) shouldBe
            "Singapore Changi Airport"
    }

    @Test
    fun `a city named like its country, outside the list, is not named again`() {
        placeResultSubtitle("Djibouti–Ambouli International Airport", city = "Djibouti", countryCode = "DJ", locale = Locale.ENGLISH) shouldBe
            "Djibouti–Ambouli International Airport"
    }

    @Test
    fun `another airport inside a city state keeps the country`() {
        placeResultSubtitle("Seletar Airport", city = "Seletar", countryCode = "SG", locale = Locale.ENGLISH) shouldBe
            "Seletar Airport · Singapore"
    }

    @Test
    fun `a plain city is not repeated as its own name`() {
        placeResultSubtitle("Lisbon", city = "Lisbon", countryCode = "PT", locale = Locale.ENGLISH) shouldBe "Portugal"
    }

    @Test
    fun `missing parts leave no stray separators`() {
        placeResultSubtitle("", city = "Lisbon", countryCode = "", locale = Locale.ENGLISH) shouldBe ""
        placeResultSubtitle("Lisbon Airport", city = "Lisbon", countryCode = "", locale = Locale.ENGLISH) shouldBe "Lisbon Airport"
    }
}
