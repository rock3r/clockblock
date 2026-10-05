package dev.sebastiano.clockblocker.opus.core.data.places

import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SearchTextTest {
    @ParameterizedTest(name = "fold({0}) = {1}")
    @CsvSource(
        delimiter = '|',
        value = [
            "Zürich|zurich",
            "São Paulo|sao paulo",
            "MÜNCHEN|munchen",
            "Kraków|krakow",
            "Łódź|lodz",
            "Straße|strasse",
            "Tromsø|tromso",
            "Reykjavík|reykjavik",
            "Dallas-Fort Worth|dallas fort worth",
            "Chicago O'Hare|chicago ohare",
            "  New   York  |new york",
            "İstanbul|istanbul",
            "Cancún|cancun",
            "Xi’an|xian",
        ],
    )
    fun `folds case, diacritics and punctuation`(input: String, expected: String) {
        SearchText.fold(input) shouldBe expected
    }

    @org.junit.jupiter.api.Test
    fun `words splits folded text`() {
        SearchText.words("Aéroport de Paris-Orly") shouldBe listOf("aeroport", "de", "paris", "orly")
        SearchText.words("") shouldBe emptyList()
    }
}
