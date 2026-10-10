package dev.sebastiano.clockblocker.opus.core.data.datastore

import dev.sebastiano.clockblocker.opus.core.model.BodyRingMode
import dev.sebastiano.clockblocker.opus.core.model.WidgetConfig
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class DataStoreWidgetConfigRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC)
    private val precise = WidgetConfig(bodyRing = BodyRingMode.Precise)

    private fun file() = tmp.newFile("widget_configs.json").also { it.delete() }

    private fun TestScope.repo(file: File, scope: CoroutineScope = backgroundScope) = DataStoreWidgetConfigRepository(
        JsonDataStores.create(file, WidgetConfigsDocument.serializer(), WidgetConfigsDocument(), scope, clock),
    )

    @Test
    fun emptyUntilAWidgetIsConfiguredThenPersists() = runTest {
        val file = file()
        val first = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val repo = repo(file, first)
        repo.configs.first().shouldBeEmpty()

        repo.update(7) { it.copy(bodyRing = BodyRingMode.Precise) }
        repo.configs.first() shouldBe mapOf(7 to precise)
        first.coroutineContext[Job]!!.cancelAndJoin()
        advanceUntilIdle()

        repo(file).configs.first() shouldBe mapOf(7 to precise)
    }

    @Test
    fun updateStartsFromTheDefaults() = runTest {
        val repo = repo(file())
        var seen: WidgetConfig? = null
        repo.update(3) { seen = it; it }
        seen shouldBe WidgetConfig()
    }

    @Test
    fun removeForgetsOnlyTheGivenWidgets() = runTest {
        val repo = repo(file())
        listOf(1, 2, 3).forEach { id -> repo.update(id) { precise } }

        repo.remove(listOf(1, 3))

        repo.configs.first() shouldBe mapOf(2 to precise)
    }

    @Test
    fun remapMovesOptionsToTheRestoredIds() = runTest {
        val repo = repo(file())
        repo.update(10) { precise }
        repo.update(11) { WidgetConfig() }

        repo.remap(oldIds = intArrayOf(10, 11), newIds = intArrayOf(20, 21))

        repo.configs.first() shouldBe mapOf(20 to precise, 21 to WidgetConfig())
    }

    @Test
    fun remapSwapsWithoutLosingEither() = runTest {
        val repo = repo(file())
        repo.update(1) { precise }
        repo.update(2) { WidgetConfig() }

        repo.remap(oldIds = intArrayOf(1, 2), newIds = intArrayOf(2, 1))

        repo.configs.first() shouldBe mapOf(2 to precise, 1 to WidgetConfig())
    }

    @Test
    fun retainOnlyDropsWidgetsThatAreGone() = runTest {
        val repo = repo(file())
        listOf(1, 2, 3).forEach { id -> repo.update(id) { precise } }

        repo.retainOnly(listOf(2, 9))

        repo.configs.first() shouldBe mapOf(2 to precise)
    }

    @Test
    fun aFileFromANewerVersionStillReadsTheOptionsItKnows() = runTest {
        val file = file()
        file.writeText("""{"schemaVersion":1,"configs":{"5":{"bodyRing":"Precise","theme":"Dark"}}}""")
        repo(file).configs.first() shouldBe mapOf(5 to precise)
    }
}
