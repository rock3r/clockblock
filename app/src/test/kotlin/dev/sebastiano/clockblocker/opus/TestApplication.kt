package dev.sebastiano.clockblocker.opus

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.android.MetroApplication

/**
 * Robolectric application for shell UI tests. Metro's `AppComponentFactory` (merged into the app manifest)
 * needs a [MetroApplication] to construct the manifest's injected receivers, so this exposes the real graph —
 * but, unlike [OpusApplication], starts nothing (no alarm scheduling or plan surface refreshes).
 */
class TestApplication : Application(), MetroApplication {
    private val graph: AppGraph by lazy { createGraphFactory<AppGraph.Factory>().create(this) }
    override val appComponentProviders: MetroAppComponentProviders get() = graph
}
