package dev.sebastiano.clockblocker.opus.core.model

/** Deep links handled by `MainActivity`; used by notifications and widgets. */
object DeepLinks {
    const val SCHEME = "clockblock"

    /**
     * Scheme used before the app was renamed from Opus Clockblock. Still accepted on the way in, so links that
     * were already handed out (placed widgets, posted notifications, saved links) keep opening the app; never
     * emitted.
     */
    const val LEGACY_SCHEME = "opusclockblock"

    const val TRIPS = "$SCHEME://trips"
    const val NEW_TRIP = "$SCHEME://trips/new"
    const val CURRENT_PLAN = "$SCHEME://plan/current"
    fun plan(tripId: String) = "$SCHEME://plan/$tripId"
}
