package dev.sebastiano.clockblocker.opus.feature.trips.editor

/**
 * Busy long-haul hubs offered under an empty airport field, so the most common picks are one tap away. Kept short
 * and region-balanced; codes the bundled dataset doesn't know are skipped.
 */
internal object PopularAirports {
    val Codes: List<String> = listOf("LHR", "JFK", "LAX", "SFO", "HND", "CDG", "SIN", "AMS", "SYD", "DXB", "FRA")
}
