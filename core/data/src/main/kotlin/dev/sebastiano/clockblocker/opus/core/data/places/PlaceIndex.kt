package dev.sebastiano.clockblocker.opus.core.data.places

import dev.sebastiano.clockblocker.opus.core.model.Place

/**
 * A searchable place: the domain [place] plus ranking metadata from the dataset.
 *
 * @property size relative importance (higher = bigger/busier airport), used to order results.
 * @property aliases extra names that should find this place: metro names and codes ("LON", "NYC"),
 * former names or the real municipality of an airport that is listed under its metro ("Newark").
 */
data class PlaceRecord(
    val place: Place,
    val size: Int = 0,
    val aliases: List<String> = emptyList(),
)

/** How a record matched a query. Declaration order is ranking order. */
enum class PlaceMatch {
    /** The query is the IATA code ("lhr"). */
    ExactCode,

    /** The query is a metropolitan-area code shared by several airports ("lon", "nyc"). */
    MetroCode,

    /** The city starts with the query ("lon" → London, "new york" → New York). */
    CityPrefix,

    /** An alias starts with the query ("newark" → EWR, listed under New York). */
    AliasPrefix,

    /** Every query word starts a word of the name/city/aliases, or the code starts with the query. */
    WordPrefix,

    /** The query occurs anywhere in the name, city or aliases. */
    Contains,
}

/**
 * In-memory airport search index. Immutable and thread-safe; build once, query many times (~1 ms per query
 * over the full dataset).
 *
 * Ranking: [PlaceMatch] tier first. Inside the city-like tiers, results are grouped by city so a
 * multi-airport city stays together ("lon" → LHR, LGW, STN, LTN, LCY, SEN before London, Ontario), groups
 * ordered by their biggest airport; inside a group and in the code tiers, bigger airports come first.
 * Matching is case- and diacritic-insensitive ("zurich" finds Zürich).
 */
class PlaceIndex(records: List<PlaceRecord>) {

    private class Entry(val record: PlaceRecord) {
        val place: Place get() = record.place
        val code: String = record.place.code.lowercase()
        val city: String = SearchText.fold(record.place.city)
        val name: String = SearchText.fold(record.place.name)
        val metroCodes: Set<String> = record.aliases
            .filter { it.length == 3 && it.all(Char::isUpperCase) }
            .mapTo(HashSet()) { it.lowercase() }
        val aliasNames: List<String> = record.aliases
            .filterNot { it.length == 3 && it.all(Char::isUpperCase) }
            .map(SearchText::fold)
        val words: List<String> = buildList {
            addAll(name.split(' '))
            addAll(city.split(' '))
            aliasNames.forEach { addAll(it.split(' ')) }
        }.filter { it.isNotEmpty() }.distinct()
        val haystack: String = buildString {
            append(name).append(' ').append(city)
            aliasNames.forEach { append(' ').append(it) }
        }
        val group: String = city + '|' + record.place.countryCode
    }

    private val entries: List<Entry> = records.map(::Entry)
    private val byCode: Map<String, PlaceRecord> = buildMap {
        // Records are expected biggest-first; keep the first record for a duplicated code.
        records.forEach { if (it.place.code.isNotBlank()) putIfAbsent(it.place.code.uppercase(), it) }
    }

    /** Number of indexed places. */
    val size: Int get() = entries.size

    /** All records, in dataset order. */
    val records: List<PlaceRecord> get() = entries.map { it.record }

    /** Exact IATA lookup, case-insensitive. */
    fun byCode(code: String): Place? = byCode[code.trim().uppercase()]?.place

    /** Ranked places for [query]; empty for a blank query. */
    fun search(query: String, limit: Int = 20): List<Place> = searchRecords(query, limit).map { it.place }

    /** Like [search], with ranking metadata and the kind of match, for UI highlighting or debugging. */
    fun searchRecords(query: String, limit: Int = 20): List<PlaceRecord> = rank(query, limit).map { it.first.record }

    /** Like [search], returning how each result matched. */
    fun explain(query: String, limit: Int = 20): List<Pair<Place, PlaceMatch>> =
        rank(query, limit).map { (entry, match) -> entry.place to match }

    private fun rank(query: String, limit: Int): List<Pair<Entry, PlaceMatch>> {
        if (limit <= 0) return emptyList()
        val q = SearchText.fold(query)
        if (q.isEmpty()) return emptyList()
        val qWords = q.split(' ')

        val matches = entries.mapNotNull { e -> matchOf(e, q, qWords)?.let { e to it } }
        val groupBest = HashMap<Pair<PlaceMatch, String>, Int>()
        for ((e, m) in matches) {
            val key = m to e.group
            groupBest[key] = maxOf(groupBest[key] ?: Int.MIN_VALUE, e.record.size)
        }
        val comparator = compareBy<Pair<Entry, PlaceMatch>> { it.second.ordinal }
            .thenByDescending { (e, m) -> if (m.grouped) groupBest.getValue(m to e.group) else e.record.size }
            .thenBy { (e, m) -> if (m.grouped) e.group else "" }
            .thenByDescending { it.first.record.size }
            .thenBy { it.first.name }
            .thenBy { it.first.code }
        return matches.sortedWith(comparator).take(limit)
    }

    private val PlaceMatch.grouped: Boolean
        get() = this != PlaceMatch.ExactCode && this != PlaceMatch.MetroCode

    private fun matchOf(e: Entry, q: String, qWords: List<String>): PlaceMatch? = when {
        e.code.isNotEmpty() && e.code == q -> PlaceMatch.ExactCode
        q in e.metroCodes -> PlaceMatch.MetroCode
        e.city.startsWith(q) -> PlaceMatch.CityPrefix
        e.aliasNames.any { it.startsWith(q) } -> PlaceMatch.AliasPrefix
        qWords.all { w -> e.words.any { it.startsWith(w) } } -> PlaceMatch.WordPrefix
        qWords.size == 1 && e.code.isNotEmpty() && e.code.startsWith(q) -> PlaceMatch.WordPrefix
        e.haystack.contains(q) -> PlaceMatch.Contains
        else -> null
    }

    companion object {
        /** Index plain places (no ranking metadata), e.g. for fakes and previews. Earlier = bigger. */
        fun of(places: List<Place>): PlaceIndex =
            PlaceIndex(places.mapIndexed { i, p -> PlaceRecord(p, size = places.size - i) })
    }
}
