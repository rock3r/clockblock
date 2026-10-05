package dev.sebastiano.clockblocker.opus

import android.app.Application
import dev.sebastiano.clockblocker.opus.core.circadian.DefaultJetLagPlanner
import dev.sebastiano.clockblocker.opus.core.circadian.JetLagPlanner
import dev.sebastiano.clockblocker.opus.core.data.AdviceLogRepository
import dev.sebastiano.clockblocker.opus.core.data.PlanRepository
import dev.sebastiano.clockblocker.opus.core.data.ProfileRepository
import dev.sebastiano.clockblocker.opus.core.data.SettingsRepository
import dev.sebastiano.clockblocker.opus.core.data.TripRepository
import dev.sebastiano.clockblocker.opus.core.notifications.NotificationsGraph
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlinx.coroutines.CoroutineScope

@DependencyGraph(AppScope::class)
interface AppGraph : MetroAppComponentProviders, ViewModelGraph, NotificationsGraph {

    /** Application-wide scope (provided by `:core:data`). */
    val applicationScope: CoroutineScope

    /** Exposed for e2e tests, which seed onboarding state before launching the UI. */
    val profileRepository: ProfileRepository

    /** Exposed for e2e tests (seeding trips, resetting app data in-process). */
    val tripRepository: TripRepository

    /** Exposed for e2e tests (resetting app data in-process). */
    val settingsRepository: SettingsRepository

    /** Exposed for e2e tests (asserting logged outcomes, resetting app data in-process). */
    val adviceLogRepository: AdviceLogRepository

    /** Exposed for e2e tests (picking a seeded trip whose plan has advice active right now). */
    val planRepository: PlanRepository

    /**
     * The engine lives in pure-JVM `:core:circadian`, which has no Metro plugin; `:core:data` depends only on
     * the [JetLagPlanner] interface, so the concrete binding is made here.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideJetLagPlanner(): JetLagPlanner = DefaultJetLagPlanner()

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): AppGraph
    }
}
