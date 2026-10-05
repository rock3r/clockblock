package dev.sebastiano.clockblocker.opus.core.data.places

import io.kotest.matchers.collections.shouldBeSortedDescendingBy
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import java.time.ZoneId

/**
 * Checks the committed `places.tsv` (plain JVM, reading the file from the source tree): integrity, zone
 * validity against java.time, size budget, and ranking on the real data.
 */
class PlacesDatasetTest {
    private companion object {
        val file = File("src/main/assets/places.tsv")
        val records: List<PlaceRecord> by lazy { file.bufferedReader().use(PlaceTsv::parse) }
        val index: PlaceIndex by lazy { PlaceIndex(records) }
    }

    private fun codes(query: String, limit: Int = 10) = index.search(query, limit).map { it.code }

    @Test
    fun `dataset is within budget and big enough`() {
        (file.length() < 600 * 1024) shouldBe true
        records.size shouldBeGreaterThan 3_500
    }

    @Test
    fun `every zone id is valid for java time`() {
        val valid = ZoneId.getAvailableZoneIds()
        records.map { it.place.zoneId }.filterNot { it in valid }.shouldBeEmpty()
    }

    @Test
    fun `rows are well formed and codes unique`() {
        val codes = records.map { it.place.code }
        codes.toSet().size shouldBe codes.size
        records.forEach { r ->
            r.place.code shouldMatch Regex("[A-Z]{3}")
            r.place.countryCode shouldMatch Regex("[A-Z]{2}")
            (r.place.latitude in -90.0..90.0 && r.place.longitude in -180.0..180.0) shouldBe true
            r.place.name.isNotBlank() shouldBe true
            r.place.city.isNotBlank() shouldBe true
            r.size shouldBeGreaterThan 0
        }
        records.shouldBeSortedDescendingBy { it.size }
    }

    @ParameterizedTest(name = "{0} is in {1}")
    @CsvSource(
        "LHR,Europe/London", "SYD,Australia/Sydney", "HNL,Pacific/Honolulu", "DEL,Asia/Kolkata",
        "PHX,America/Phoenix", "KTM,Asia/Kathmandu", "SFO,America/Los_Angeles", "SIN,Asia/Singapore",
        "ADL,Australia/Adelaide", "SCL,America/Santiago", "ZRH,Europe/Zurich", "AKL,Pacific/Auckland",
    )
    fun `well known airports have the right zone`(code: String, zone: String) {
        index.byCode(code)?.zoneId shouldBe zone
    }

    @Test
    fun `lon finds the London airports, Heathrow and Gatwick first`() {
        val top = codes("lon", 6)
        top.take(2) shouldContainExactly listOf("LHR", "LGW")
        top shouldContainAll listOf("STN", "LTN", "LCY")
        index.search("lon", 6).map { it.countryCode }.toSet() shouldBe setOf("GB")
    }

    @Test
    fun `new york finds JFK, EWR, LGA`() {
        codes("new york", 3) shouldContainExactly listOf("JFK", "EWR", "LGA")
        codes("nyc", 3) shouldContainExactly listOf("JFK", "EWR", "LGA")
    }

    @Test
    fun `zurich finds Zürich with or without the umlaut`() {
        codes("zurich").first() shouldBe "ZRH"
        codes("Zürich").first() shouldBe "ZRH"
    }

    @ParameterizedTest(name = "''{0}'' → {1}")
    @CsvSource(
        "sin,SIN", "SYD,SYD", "sydney,SYD", "heathrow,LHR", "paris,CDG", "sao paulo,GRU", "são paulo,GRU",
        "lisbon,LIS", "singapore,SIN", "dubai,DXB", "atlanta,ATL", "frankfurt,FRA", "honolulu,HNL",
        "kennedy,JFK", "o'hare,ORD", "ohare,ORD", "tokyo,HND", "chicago,ORD", "san francisco,SFO",
    )
    fun `top result for common queries`(query: String, expected: String) {
        codes(query).first() shouldBe expected
    }

    @Test
    fun `multi-airport cities list their airports together`() {
        codes("tokyo", 2) shouldContainExactlyInAnyOrder listOf("HND", "NRT")
        codes("paris", 2) shouldContainExactly listOf("CDG", "ORY")
        codes("newark").first() shouldBe "EWR"
    }

    @Test
    fun `parsing and indexing the full dataset is fast`() {
        val text = file.readText()
        val cold = measure { PlaceIndex(PlaceTsv.parse(text)) }
        val warm = (1..5).minOf { measure { PlaceIndex(PlaceTsv.parse(text)) } }
        println("places.tsv: ${records.size} rows, ${file.length() / 1024} KB, cold ${cold} ms, warm ${warm} ms")
        cold shouldBeLessThan 1_500
        warm shouldBeLessThan 150
        val query = (1..20).minOf { measure { index.search("lon") } }
        query shouldBeLessThan 20
    }

    private inline fun measure(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }
}
