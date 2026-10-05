package dev.sebastiano.clockblocker.opus.core.data.di

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.circadian.JetLagPlanner
import dev.sebastiano.clockblocker.opus.core.data.AdviceLogRepository
import dev.sebastiano.clockblocker.opus.core.data.PlaceSearch
import dev.sebastiano.clockblocker.opus.core.data.PlanRepository
import dev.sebastiano.clockblocker.opus.core.data.ProfileRepository
import dev.sebastiano.clockblocker.opus.core.data.SettingsRepository
import dev.sebastiano.clockblocker.opus.core.data.TripRepository
import dev.sebastiano.clockblocker.opus.core.data.backup.BackupManager
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.data.export.IcsExporter
import dev.sebastiano.clockblocker.opus.core.data.time.Ticker
import dev.sebastiano.clockblocker.opus.core.data.trip.ReturnTripFactory
import dev.sebastiano.clockblocker.opus.core.data.trip.TripTitleSuggester
import dev.sebastiano.clockblocker.opus.core.data.trip.TripValidator
import dev.sebastiano.clockblocker.opus.core.testing.FakeJetLagPlanner
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock

/** A graph shaped like the app's, proving every `:core:data` binding resolves through Metro aggregation. */
@DependencyGraph(AppScope::class)
interface DataTestGraph {
    val trips: TripRepository
    val profiles: ProfileRepository
    val settings: SettingsRepository
    val adviceLogs: AdviceLogRepository
    val plans: PlanRepository
    val places: PlaceSearch
    val clock: Clock
    val ticker: Ticker
    val validator: TripValidator
    val titles: TripTitleSuggester
    val returnTrips: ReturnTripFactory
    val backup: BackupManager
    val ics: IcsExporter

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application, @Provides planner: JetLagPlanner): DataTestGraph
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataBindingsTest {
    @Test
    fun everyBindingResolvesAndWorksEndToEnd(): Unit = runBlocking {
        val graph = createGraphFactory<DataTestGraph.Factory>()
            .create(ApplicationProvider.getApplicationContext(), FakeJetLagPlanner())

        graph.trips shouldBeSameInstanceAs graph.trips // @SingleIn(AppScope)
        graph.profiles.save(DemoData.profile)
        graph.trips.upsert(DemoData.sfoToLhr())

        graph.plans.plan(DemoData.SfoLhrId).filterNotNull().first().tripId shouldBe DemoData.SfoLhrId
        graph.places.search("zurich").first().code shouldBe "ZRH"
        graph.backup.snapshot().trips.single().id shouldBe DemoData.SfoLhrId
    }
}
