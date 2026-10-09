package dev.sebastiano.clockblocker.opus.core.designsystem.component

import java.util.Locale

/**
 * "🇵🇹 Lisbon": the title line of an airport search result. The country lives on the second line
 * ([placeResultSubtitle]) so the city never truncates.
 */
fun placeResultTitle(city: String, countryCode: String): String = "${flagEmoji(countryCode)} $city".trim()

/**
 * "London Heathrow Airport · United Kingdom": the second line of an airport search result, with the country in
 * [locale]. A city-state isn't named again ("Singapore Changi Airport", not "… · Singapore"): decided from
 * [countryCode], because the bundled city names are English while the country label is localised ("Singapur") or
 * longer ("Hong Kong SAR China"). A plain city whose name is the city itself shows only the country.
 */
fun placeResultSubtitle(name: String, city: String, countryCode: String, locale: Locale = Locale.getDefault()): String {
    val cityName = city.trim()
    val placeName = name.trim().takeUnless { it.isBlank() || it.equals(cityName, ignoreCase = true) }
    val cityState = CityStateNames[countryCode.trim().uppercase()].orEmpty().any { it.equals(cityName, ignoreCase = true) } ||
        countryName(countryCode, Locale.ENGLISH).equals(cityName, ignoreCase = true)
    val country = countryName(countryCode, locale).trim().takeUnless { it.isBlank() || cityState }
    return listOfNotNull(placeName, country).joinToString(" · ")
}

/** Localised country name for an ISO 3166 alpha-2 code ("PT" → "Portugal"); the code if unknown. */
fun countryName(countryCode: String, locale: Locale = Locale.getDefault()): String {
    if (countryCode.isBlank()) return ""
    val name = Locale.Builder().setRegion(countryCode).build().getDisplayCountry(locale)
    return name.ifBlank { countryCode }
}

/** Regional-indicator flag for an ISO 3166 alpha-2 code ("PT" → 🇵🇹); empty when the code isn't two letters. */
fun flagEmoji(countryCode: String): String {
    val code = countryCode.trim().uppercase()
    if (code.length != 2 || code.any { it !in 'A'..'Z' }) return ""
    return code.map { String(Character.toChars(RegionalIndicatorA + (it - 'A'))) }.joinToString("")
}

private const val RegionalIndicatorA = 0x1F1E6

/**
 * City-states and the city names that *are* the region, whatever the locale data calls the region
 * ("Hong Kong SAR China"). Other cities in the same region (Seletar in Singapore) still get the country.
 */
private val CityStateNames = mapOf(
    "SG" to listOf("Singapore"),
    "HK" to listOf("Hong Kong"),
    "MO" to listOf("Macau", "Macao"),
    "MC" to listOf("Monaco", "Monte Carlo"),
    "VA" to listOf("Vatican City"),
    "GI" to listOf("Gibraltar"),
    "SM" to listOf("San Marino"),
)
