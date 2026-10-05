package dev.sebastiano.clockblocker.opus.core.data.di

import dev.sebastiano.clockblocker.opus.core.data.time.AppDispatchers
import dev.sebastiano.clockblocker.opus.core.data.time.ClockTicker
import dev.sebastiano.clockblocker.opus.core.data.time.DeviceZone
import dev.sebastiano.clockblocker.opus.core.data.time.Ticker
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.time.Clock

/**
 * App-wide infrastructure provided by `:core:data`.
 *
 * - [Clock]: UTC system clock. Use `clock.instant()`; never read the user's location from `clock.zone`
 *   (the device zone changes while travelling; derive zones from trips / [DeviceZone] at use).
 * - [DeviceZone]: the zone the device is in right now, read fresh on every call.
 * - [AppDispatchers]: IO / Default / Main.
 * - [CoroutineScope]: the application scope (SupervisorJob + Default) for work that must outlive a screen,
 *   such as plan computation shared between the UI, widgets and the notification.
 * - [Ticker]: ticks once a minute so time-dependent flows (current plan) re-evaluate as time passes.
 */
@ContributesTo(AppScope::class)
interface DataBindings {
    @Provides
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @SingleIn(AppScope::class)
    fun provideDispatchers(): AppDispatchers = AppDispatchers()

    @Provides
    @SingleIn(AppScope::class)
    fun provideApplicationScope(dispatchers: AppDispatchers): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatchers.default)

    @Provides
    fun provideTicker(clock: Clock): Ticker = ClockTicker(clock)

    @Provides
    fun provideDeviceZone(): DeviceZone = DeviceZone.System
}
