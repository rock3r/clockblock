package dev.sebastiano.clockblocker.opus.core.data.places

import dev.sebastiano.clockblocker.opus.core.model.Place
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Pure ranking rules on a small hand-made dataset. */
class PlaceIndexTest {
    private fun rec(code: String, name: String, city: String, size: Int, country: String = "GB", vararg aliases: String) =
        PlaceRecord(Place(code, name, city, country, "Europe/London", 0.0, 0.0), size, aliases.toList())

    private val index = PlaceIndex(
        listOf(
            rec("LHR", "London Heathrow Airport", "London", 398, "GB", "LON"),
            rec("LGW", "London Gatwick Airport", "London", 383, "GB", "LON"),
            rec("STN", "London Stansted Airport", "London", 369, "GB", "LON"),
            rec("LCY", "London City Airport", "London", 300, "GB", "LON"),
            rec("YXU", "London International Airport", "London", 200, "CA"),
            rec("LDB", "Governador José Richa Airport", "Londrina", 210, "BR"),
            rec("LGB", "Long Beach Airport", "Los Angeles", 310, "US", "Long Beach", "QLA"),
            rec("JFK", "John F. Kennedy International Airport", "New York", 395, "US", "NYC"),
            rec("EWR", "Newark Liberty International Airport", "New York", 388, "US", "Newark", "NYC"),
            rec("LGA", "LaGuardia Airport", "New York", 380, "US", "NYC"),
            rec("ZRH", "Zürich Airport", "Zurich", 372, "CH"),
            rec("GRU", "Guarulhos International Airport", "São Paulo", 390, "BR", "SAO"),
            rec("LON", "Fictional Lonely Field", "Elsewhere", 50, "XX"),
        ),
    )

    private fun codes(query: String, limit: Int = 20) = index.search(query, limit).map { it.code }

    @Test
    fun `exact IATA code wins over everything`() {
        codes("lhr").first() shouldBe "LHR"
        codes("LGA").first() shouldBe "LGA"
        // A real airport coded like the query beats the metro code and city matches.
        codes("lon").first() shouldBe "LON"
    }

    @Test
    fun `metro code finds all airports of the metro, biggest first`() {
        codes("nyc") shouldContainExactly listOf("JFK", "EWR", "LGA")
    }

    @Test
    fun `city prefix groups a multi-airport city together, biggest airport first`() {
        codes("lond") shouldContainExactly listOf("LHR", "LGW", "STN", "LCY", "LDB", "YXU")
    }

    @Test
    fun `metro code results come before city prefix results`() {
        codes("lon").take(5) shouldContainExactly listOf("LON", "LHR", "LGW", "STN", "LCY")
    }

    @Test
    fun `multi word city query`() {
        codes("new york") shouldContainExactly listOf("JFK", "EWR", "LGA")
        codes("New-York") shouldContainExactly listOf("JFK", "EWR", "LGA")
    }

    @Test
    fun `alias prefix finds an airport listed under its metro`() {
        index.explain("newark").first() shouldBe (index.byCode("EWR")!! to PlaceMatch.AliasPrefix)
        codes("long beach").first() shouldBe "LGB"
    }

    @Test
    fun `diacritic and case insensitive both ways`() {
        codes("zurich") shouldContainExactly listOf("ZRH")
        codes("ZÜRICH") shouldContainExactly listOf("ZRH")
        codes("sao paulo") shouldContainExactly listOf("GRU")
        codes("são") shouldContainExactly listOf("GRU")
        codes("jose richa") shouldContainExactly listOf("LDB")
    }

    @Test
    fun `word prefix matches any word of the name`() {
        index.explain("heathrow").single() shouldBe (index.byCode("LHR")!! to PlaceMatch.WordPrefix)
        codes("kennedy") shouldContainExactly listOf("JFK")
        codes("gat lon") shouldContainExactly listOf("LGW")
    }

    @Test
    fun `contains is the last resort`() {
        index.explain("athrow").single() shouldBe (index.byCode("LHR")!! to PlaceMatch.Contains)
    }

    @Test
    fun `code prefix is a word prefix match`() {
        // Grouped by city like other name matches: London (383) > New York (380) > Los Angeles (310).
        index.explain("lg").map { it.first.code to it.second } shouldBe listOf(
            "LGW" to PlaceMatch.WordPrefix,
            "LGA" to PlaceMatch.WordPrefix,
            "LGB" to PlaceMatch.WordPrefix,
        )
    }

    @Test
    fun `blank queries and zero limits return nothing`() {
        index.search("").shouldBeEmpty()
        index.search("   ").shouldBeEmpty()
        index.search("--").shouldBeEmpty()
        index.search("london", limit = 0).shouldBeEmpty()
        index.search("qqqqq").shouldBeEmpty()
    }

    @Test
    fun `limit truncates after ranking`() {
        codes("lond", limit = 2) shouldContainExactly listOf("LHR", "LGW")
    }

    @Test
    fun `byCode is exact and case insensitive`() {
        index.byCode("jfk")?.name shouldBe "John F. Kennedy International Airport"
        index.byCode(" ewr ")?.code shouldBe "EWR"
        index.byCode("XXX").shouldBeNull()
        index.byCode("").shouldBeNull()
    }

    @Test
    fun `of() ranks plain places by list order`() {
        val small = PlaceIndex.of(listOf(index.byCode("LGW")!!, index.byCode("LHR")!!))
        small.search("london").map { it.code } shouldContainExactly listOf("LGW", "LHR")
        small.size shouldBe 2
    }

    @Test
    fun `search never throws and respects the limit for arbitrary input`() = runTest {
        checkAll(500, Arb.string(0..12)) { query ->
            index.search(query, limit = 3).size shouldBe minOf(3, index.search(query, 100).size)
        }
        index.search("l", limit = 100) shouldHaveSize index.records.count { r ->
            val p = r.place
            listOf(p.code, p.name, p.city).any { SearchText.fold(it).contains("l") } ||
                r.aliases.any { SearchText.fold(it).contains("l") }
        }
    }
}
