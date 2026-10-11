package dev.sebastiano.clockblocker.opus.feature.trips.editor

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

/** The ticket's "+1 day" next to the arrival: calendar days between the two local dates on the ticket. */
class ArrivalDayOffsetTest {

    private val day = LocalDate.of(2026, 10, 16)

    private fun leg(arrival: LocalDate?, departure: LocalDate? = day) = LegDraft(
        id = "leg",
        departureDate = departure,
        departureTime = LocalTime.of(10, 0),
        arrivalDate = arrival,
        arrivalTime = LocalTime.of(4, 50),
    )

    @Test
    fun `a same-day arrival has no offset`() {
        leg(day).arrivalDayOffset shouldBe 0
    }

    @Test
    fun `an overnight flight lands a day later`() {
        leg(day.plusDays(1)).arrivalDayOffset shouldBe 1
    }

    @Test
    fun `long hauls across the date line can land two days later, or the day before`() {
        leg(day.plusDays(2)).arrivalDayOffset shouldBe 2
        leg(day.minusDays(1)).arrivalDayOffset shouldBe -1
    }

    @Test
    fun `no offset until both dates are known`() {
        leg(null).arrivalDayOffset.shouldBeNull()
        leg(day, departure = null).arrivalDayOffset.shouldBeNull()
    }
}
