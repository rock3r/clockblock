package dev.sebastiano.clockblocker.opus.feature.trips.editor

import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The airport name under each code on the ticket says what the city line doesn't. */
class AirportLabelTest {

    @Test
    fun `the name loses the city and the word airport`() {
        DemoData.LHR.airportLabel shouldBe "Heathrow"
        DemoData.LGW.airportLabel shouldBe "Gatwick" // So LHR and LGW no longer both read just "London".
        DemoData.LIS.airportLabel shouldBe "Humberto Delgado"
        DemoData.HND.airportLabel shouldBe "Haneda"
        DemoData.JFK.airportLabel shouldBe "John F. Kennedy"
    }

    @Test
    fun `no name when it would only repeat the city`() {
        DemoData.DXB.airportLabel.shouldBeNull()
        DemoData.LAX.airportLabel.shouldBeNull()
        DemoData.ZRH.airportLabel.shouldBeNull() // "Zürich" airport, "Zurich" city.
        DemoData.LIS.copy(code = "").airportLabel.shouldBeNull() // A plain city.
    }
}
