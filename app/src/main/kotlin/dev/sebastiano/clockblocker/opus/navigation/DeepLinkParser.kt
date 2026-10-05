package dev.sebastiano.clockblocker.opus.navigation

import dev.sebastiano.clockblocker.opus.core.model.DeepLinks
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Where a deep link lands: the top-level [destination] and the full (synthetic) [backStack] for it. */
data class DeepLinkTarget(val destination: TopLevelDestination, val backStack: List<AppRoute>) {
    init {
        require(backStack.firstOrNull() == destination.root) { "A deep link back stack starts at its root" }
    }
}

/**
 * Maps the app's [DeepLinks] to back stacks. Pure (no `android.net.Uri`), so it is unit tested on the JVM.
 *
 * | URI | Destination | Back stack |
 * |---|---|---|
 * | `opusclockblock://trips` | Trips | Trips |
 * | `opusclockblock://trips/new` | Trips | Trips → TripEditor(new) |
 * | `opusclockblock://plan/current` | Now | Now |
 * | `opusclockblock://plan/{tripId}` | Trips | Trips → Plan(tripId) (synthetic parent) |
 *
 * Scheme and host are case-insensitive; queries, fragments and trailing slashes are ignored. Anything else
 * returns null.
 */
object DeepLinkParser {
    private const val HOST_TRIPS = "trips"
    private const val HOST_PLAN = "plan"
    private const val SEGMENT_NEW = "new"
    private const val SEGMENT_CURRENT = "current"

    fun parse(uri: String?): DeepLinkTarget? {
        if (uri.isNullOrBlank()) return null
        val parsed = try {
            URI(uri)
        } catch (_: URISyntaxException) {
            return null
        }
        if (!parsed.scheme.equals(DeepLinks.SCHEME, ignoreCase = true)) return null
        val host = (parsed.host ?: parsed.rawAuthority)?.lowercase() ?: return null
        val segments = parsed.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }.map { decode(it) ?: return null }
        return when (host) {
            HOST_TRIPS -> when {
                segments.isEmpty() -> DeepLinkTarget(TopLevelDestination.Trips, listOf(TripsRoute))
                segments == listOf(SEGMENT_NEW) ->
                    DeepLinkTarget(TopLevelDestination.Trips, listOf(TripsRoute, TripEditorRoute()))
                else -> null
            }

            HOST_PLAN -> when {
                segments.size != 1 -> null
                segments.single().equals(SEGMENT_CURRENT, ignoreCase = true) ->
                    DeepLinkTarget(TopLevelDestination.Now, listOf(NowRoute))
                else -> DeepLinkTarget(TopLevelDestination.Trips, listOf(TripsRoute, PlanRoute(segments.single())))
            }

            else -> null
        }
    }

    /** Percent-decodes a path segment ('+' stays literal in paths). */
    private fun decode(segment: String): String? = try {
        URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        null
    }
}
