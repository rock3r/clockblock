package dev.sebastiano.clockblocker.opus.core.data.places

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.data.time.AppDispatchers
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class AssetPlaceSearchTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun loadsTheBundledAssetAndSearchesIt() = runTest {
        val search = AssetPlaceSearch(application, AppDispatchers.single(StandardTestDispatcher(testScheduler)))

        search.search("zurich").first().code shouldBe "ZRH"
        search.search("new york", 3).map { it.code } shouldContainExactly listOf("JFK", "EWR", "LGA")
        search.byCode("lhr")?.zoneId shouldBe "Europe/London"
        search.byCode("???").shouldBeNull()
        search.index().size shouldBeGreaterThan 3_500

        val loadMs = search.loadDuration.shouldNotBeNull().toMillis()
        println("AssetPlaceSearch (Robolectric) loaded ${search.index().size} places in $loadMs ms")
    }

    @Test
    fun warmLoadFromAssetsIsUnderBudget() = runTest {
        // Initial loads pay class loading/JIT; the budget applies to the steady-state parse+index cost.
        // Shared CI runners have noisy CPU/GC scheduling, so allow a wider ceiling there (issue #34).
        val budgetMs = if (System.getenv("CI") != null) 500L else 150L
        repeat(2) {
            AssetPlaceSearch(application, AppDispatchers.single(StandardTestDispatcher(testScheduler))).warmUp()
        }
        val best = (1..5).minOf {
            val search = AssetPlaceSearch(application, AppDispatchers.single(StandardTestDispatcher(testScheduler)))
            search.warmUp()
            search.loadDuration!!.toMillis()
        }
        println("AssetPlaceSearch warm load: $best ms (budget $budgetMs ms)")
        best shouldBeLessThan budgetMs
    }

    @Test
    fun concurrentFirstSearchesLoadTheDatasetOnce() = runTest {
        val opens = AtomicInteger()
        val tsv = "# header\nLHR\tLondon Heathrow Airport\tLondon\tGB\tEurope/London\t51.471\t-0.460\t398\tLON\n"
        val search = AssetPlaceSearch(
            openDataset = { opens.incrementAndGet(); ByteArrayInputStream(tsv.encodeToByteArray()) },
            dispatchers = AppDispatchers.single(StandardTestDispatcher(testScheduler)),
        )

        val results = (1..10).map { async { search.search("lon") } }.awaitAll()

        opens.get() shouldBe 1
        results.forEach { r -> r.map { it.code } shouldContainExactly listOf("LHR") }
    }

    @Test
    fun nothingIsLoadedUntilFirstUse() = runTest {
        val opens = AtomicInteger()
        AssetPlaceSearch(
            openDataset = { opens.incrementAndGet(); ByteArrayInputStream(ByteArray(0)) },
            dispatchers = AppDispatchers.single(StandardTestDispatcher(testScheduler)),
        )
        opens.get() shouldBe 0
    }
}
