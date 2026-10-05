package dev.sebastiano.clockblocker.opus.core.data.time

import java.time.ZoneId

/**
 * The time zone the device is in *right now*. Read it at use, never cache it: travellers change zones.
 *
 * Exists because the app's [java.time.Clock] is deliberately UTC (see `DataBindings`), so `clock.zone` is never
 * the user's zone — reading it once made a fresh install default the home zone to "Z" (UTC).
 */
fun interface DeviceZone {
    fun current(): ZoneId

    companion object {
        /** The platform default zone, read fresh on every call. */
        val System: DeviceZone = DeviceZone { ZoneId.systemDefault() }
    }
}
