package dev.sebastiano.clockblocker.opus.core.model

/** Deep links handled by `MainActivity`; used by notifications and widgets. */
object DeepLinks {
    const val SCHEME = "clockblock"
    const val TRIPS = "$SCHEME://trips"
    const val NEW_TRIP = "$SCHEME://trips/new"
    const val CURRENT_PLAN = "$SCHEME://plan/current"
    fun plan(tripId: String) = "$SCHEME://plan/$tripId"
}
