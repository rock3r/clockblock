package dev.sebastiano.clockblocker.opus

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.android.MetroApplication

class ClockblockApplication : Application(), MetroApplication {
    val graph: AppGraph by lazy { createGraphFactory<AppGraph.Factory>().create(this) }
    override val appComponentProviders: MetroAppComponentProviders get() = graph

    override fun onCreate() {
        super.onCreate()
        // Single source of truth for reminders, the Now notification and widget refreshes (idempotent).
        graph.adviceAlarmScheduler.start(graph.applicationScope)
    }
}
