package dev.sebastiano.clockblocker.opus.core.data.demo

import dev.sebastiano.clockblocker.opus.core.data.places.PlaceTsv
import dev.sebastiano.clockblocker.opus.core.data.trip.TripValidator
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

class DemoDataTest {
    @Test
    fun `demo trips have the documented local times`() {
        val sfo = DemoData.sfoToLhr().legs.single()
        sfo.departureLocal shouldBe LocalDateTime.of(2026, 6, 15, 19, 30)
        sfo.arrivalLocal shouldBe LocalDateTime.of(2026, 6, 16, 13, 50)

        val (toSin, toSyd) = DemoData.lhrToSydneyViaSingapore().legs
        toSin.departureLocal shouldBe LocalDateTime.of(2026, 6, 25, 21, 35)
        toSin.arrivalLocal shouldBe LocalDateTime.of(2026, 6, 26, 18, 5)
        toSyd.departureLocal shouldBe LocalDateTime.of(2026, 6, 26, 20, 20)
        toSyd.arrivalLocal shouldBe LocalDateTime.of(2026, 6, 27, 6, 5)
    }

    @Test
    fun `demo trips are valid on any date, DST included`() {
        val validator = TripValidator()
        listOf(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 3, 28), LocalDate.of(2026, 10, 24), LocalDate.of(2027, 4, 3))
            .forEach { date -> DemoData.trips(date).forEach { validator.validate(it).issues.shouldBeEmpty() } }
    }

    @Test
    fun `demo data is deterministic`() {
        DemoData.trips() shouldBe DemoData.trips()
        DemoData.trips().map { it.id } shouldBe listOf(DemoData.SfoLhrId, DemoData.LhrSydId)
        DemoData.tryItTrip(LocalDate.of(2026, 9, 1)).departure.isAfter(
            LocalDate.of(2026, 9, 1).atStartOfDay(DemoData.SFO.zone).toInstant(),
        ) shouldBe true
    }

    @Test
    fun `demo places match the bundled dataset`() {
        val dataset = File("src/main/assets/places.tsv").bufferedReader().use(PlaceTsv::parse).associateBy { it.place.code }
        DemoData.places.forEach { p ->
            val row = dataset.getValue(p.code).place
            row.zoneId shouldBe p.zoneId
            row.city shouldBe p.city
            row.countryCode shouldBe p.countryCode
        }
    }
}
