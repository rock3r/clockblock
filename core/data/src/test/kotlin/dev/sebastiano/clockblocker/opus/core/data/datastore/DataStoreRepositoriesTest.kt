package dev.sebastiano.clockblocker.opus.core.data.datastore

import app.cash.turbine.test
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.Intensity
import dev.sebastiano.clockblocker.opus.core.model.ThemeMode
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
class DataStoreRepositoriesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC)

    private fun TestScope.storeScope() = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())

    private fun TestScope.trips(file: File, scope: CoroutineScope = backgroundScope) = DataStoreTripRepository(
        JsonDataStores.create(file, TripsDocument.serializer(), TripsDocument(), scope, clock),
    )

    private fun TestScope.profiles(file: File, scope: CoroutineScope = backgroundScope) = DataStoreProfileRepository(
        JsonDataStores.create(file, ProfileDocument.serializer(), ProfileDocument(), scope, clock),
    )

    private fun TestScope.settings(file: File) = DataStoreSettingsRepository(
        JsonDataStores.create(file, SettingsDocument.serializer(), SettingsDocument(), backgroundScope, clock),
    )

    private fun TestScope.logs(file: File) = DataStoreAdviceLogRepository(
        JsonDataStores.create(file, AdviceLogDocument.serializer(), AdviceLogDocument(), backgroundScope, clock),
    )

    private val sfoLhr = DemoData.sfoToLhr()
    private val lhrSyd = DemoData.lhrToSydneyViaSingapore()

    // region Trips

    @Test
    fun tripsAreEmptyThenSortedByDeparture() = runTest {
        val repo = trips(tmp.newFile("trips.json").also { it.delete() })
        repo.trips.test {
            awaitItem().shouldBeEmpty()
            repo.upsert(lhrSyd)
            awaitItem().map { it.id } shouldContainExactly listOf(lhrSyd.id)
            repo.upsert(sfoLhr) // Departs earlier: must come first even though added later.
            awaitItem().map { it.id } shouldContainExactly listOf(sfoLhr.id, lhrSyd.id)
        }
    }

    @Test
    fun upsertReplacesByIdAndDeleteRemoves() = runTest {
        val repo = trips(File(tmp.root, "trips.json"))
        repo.upsert(sfoLhr)
        repo.upsert(sfoLhr.copy(title = "Renamed"))
        repo.trips.first().single().title shouldBe "Renamed"

        repo.trip(sfoLhr.id).test {
            awaitItem()?.title shouldBe "Renamed"
            repo.delete(sfoLhr.id)
            awaitItem().shouldBeNull()
        }
        repo.delete("does-not-exist") // No-op, no crash.
        repo.trips.first().shouldBeEmpty()
    }

    @Test
    fun tripsSurviveARestart() = runTest {
        val file = File(tmp.root, "trips.json")
        val first = storeScope()
        trips(file, first).apply { upsert(sfoLhr); upsert(lhrSyd) }
        first.coroutineContext[Job]!!.cancelAndJoin()
        advanceUntilIdle()

        trips(file).trips.first() shouldBe listOf(sfoLhr, lhrSyd)
    }

    @Test
    fun bodyClockStartSurvivesARestart() = runTest {
        val file = File(tmp.root, "trips.json")
        val first = storeScope()
        val fromRome = sfoLhr.copy(bodyClockStartZoneId = "Europe/Rome")
        trips(file, first).upsert(fromRome)
        first.coroutineContext[Job]!!.cancelAndJoin()
        advanceUntilIdle()

        trips(file).trips.first() shouldBe listOf(fromRome)
    }

    @Test
    fun tripsSavedBeforeTheBodyClockStartChoiceLoad() = runTest {
        // A document from an older version: the trip has no bodyClockStartZoneId at all.
        val legacy = ClockblockJson.encodeToString(dev.sebastiano.clockblocker.opus.core.model.Trip.serializer(), sfoLhr)
        legacy shouldNotContain "bodyClockStartZoneId"
        val file = File(tmp.root, "trips.json").apply { writeText("""{"schemaVersion":1,"trips":[$legacy]}""") }
        trips(file).trips.first().single().bodyClockStartZoneId shouldBe null
    }

    @Test
    fun corruptFileIsBackedUpAndTheStoreStartsEmpty() = runTest {
        val file = File(tmp.root, "trips.json").apply { writeText("{\"trips\": [ this is not json") }
        val repo = trips(file)

        repo.trips.first().shouldBeEmpty()
        val backups = tmp.root.listFiles { f -> f.name.startsWith("trips.json.corrupt-") }!!.toList()
        backups shouldHaveSize 1
        backups.single().readText() shouldContain "this is not json"

        repo.upsert(sfoLhr) // Store is usable again.
        repo.trips.first() shouldBe listOf(sfoLhr)
    }

    @Test
    fun oneUnreadableTripIsQuarantinedNotFatal() = runTest {
        val good = ClockblockJson.encodeToString(dev.sebastiano.clockblocker.opus.core.model.Trip.serializer(), sfoLhr)
        val badZone = good.replace("Europe/London", "Mars/Olympus_Mons").replace(sfoLhr.id, "bad-zone")
        val noLegs = """{"id":"no-legs","title":"x","legs":[],"createdAt":"2026-01-01T00:00:00Z"}"""
        val file = File(tmp.root, "trips.json").apply {
            writeText("""{"schemaVersion":1,"trips":[$good,$badZone,$noLegs]}""")
        }
        val repo = trips(file)

        repo.trips.first() shouldBe listOf(sfoLhr)

        repo.upsert(lhrSyd) // Rewrites the file: quarantined entries must be preserved verbatim.
        repo.trips.first() shouldBe listOf(sfoLhr, lhrSyd)
        val text = file.readText()
        text shouldContain "Mars/Olympus_Mons"
        text shouldContain "no-legs"
    }

    @Test
    fun unknownFieldsFromANewerVersionAreIgnored() = runTest {
        val json = ClockblockJson.encodeToString(dev.sebastiano.clockblocker.opus.core.model.Trip.serializer(), sfoLhr)
            .replaceFirst("{", "{\"futureField\":{\"x\":1},")
        val file = File(tmp.root, "trips.json").apply { writeText("""{"schemaVersion":2,"trips":[$json],"extra":true}""") }
        trips(file).trips.first() shouldBe listOf(sfoLhr)
    }

    // endregion

    // region Profile

    @Test
    fun profileIsNullUntilSavedAndPersists() = runTest {
        val file = File(tmp.root, "profile.json")
        val scope = storeScope()
        val repo = profiles(file, scope)
        repo.profile.test {
            awaitItem().shouldBeNull()
            repo.save(DemoData.profile)
            awaitItem() shouldBe DemoData.profile
            repo.save(DemoData.profile.copy(intensity = Intensity.Max))
            awaitItem()?.intensity shouldBe Intensity.Max
        }
        scope.coroutineContext[Job]!!.cancelAndJoin()
        advanceUntilIdle()
        profiles(file).profile.first()?.intensity shouldBe Intensity.Max
    }

    // endregion

    // region Settings

    @Test
    fun settingsDefaultThenUpdate() = runTest {
        val repo = settings(File(tmp.root, "settings.json"))
        repo.settings.test {
            awaitItem() shouldBe AppSettings()
            repo.update { it.copy(themeMode = ThemeMode.Dark) }
            awaitItem().themeMode shouldBe ThemeMode.Dark
        }
    }

    @Test
    fun concurrentSettingsUpdatesAreAtomic() = runTest {
        val repo = settings(File(tmp.root, "settings.json"))
        val start = AppSettings().reminderLeadMinutes
        List(50) { launch { repo.update { it.copy(reminderLeadMinutes = it.reminderLeadMinutes + 1) } } }.joinAll()
        repo.settings.first().reminderLeadMinutes shouldBe start + 50
    }

    @Test
    fun unknownEnumValueFallsBackToDefault() = runTest {
        val file = File(tmp.root, "settings.json").apply {
            writeText("""{"schemaVersion":1,"settings":{"themeMode":"Sepia","reminderLeadMinutes":30}}""")
        }
        val s = settings(file).settings.first()
        s.themeMode shouldBe ThemeMode.System
        s.reminderLeadMinutes shouldBe 30
    }

    // endregion

    // region Advice logs

    @Test
    fun adviceLogsArePerTripAndLatestOutcomeWins() = runTest {
        val repo = logs(File(tmp.root, "advice_logs.json"))
        repo.logs("t1").test {
            awaitItem().shouldBeEmpty()
            repo.log("t1", "a1", AdviceOutcome.Done)
            awaitItem() shouldBe listOf(AdviceLog("a1", AdviceOutcome.Done))
            repo.log("t1", "a2", AdviceOutcome.Skipped)
            awaitItem() shouldBe listOf(AdviceLog("a1", AdviceOutcome.Done), AdviceLog("a2", AdviceOutcome.Skipped))
            repo.log("t1", "a1", AdviceOutcome.CantDo)
            awaitItem() shouldBe listOf(AdviceLog("a2", AdviceOutcome.Skipped), AdviceLog("a1", AdviceOutcome.CantDo))
            repo.log("t2", "a1", AdviceOutcome.Done) // Other trip: no emission for t1.
            expectNoEvents()
        }
        repo.logs("t2").first() shouldBe listOf(AdviceLog("a1", AdviceOutcome.Done))
    }

    @Test
    fun clearingForgetsOneOutcomeAndKeepsTheRest() = runTest {
        val repo = logs(File(tmp.root, "advice_logs_clear.json"))
        repo.log("t1", "a1", AdviceOutcome.Done)
        repo.log("t1", "a2", AdviceOutcome.Skipped)
        repo.log("t2", "a1", AdviceOutcome.CantDo)
        repo.clear("t1", "a1")
        repo.logs("t1").first() shouldBe listOf(AdviceLog("a2", AdviceOutcome.Skipped))
        repo.logs("t2").first() shouldBe listOf(AdviceLog("a1", AdviceOutcome.CantDo))
        // Nothing logged: no-op, no crash.
        repo.clear("t1", "missing")
        repo.clear("nope", "a1")
        repo.logs("t1").first() shouldBe listOf(AdviceLog("a2", AdviceOutcome.Skipped))
    }

    @Test
    fun logIfAbsentNeverOverwritesAnOutcome() = runTest {
        val repo = logs(File(tmp.root, "advice_logs_if_absent.json"))
        repo.log("t1", "a1", AdviceOutcome.Done)
        repo.logIfAbsent("t1", "a1", AdviceOutcome.CantDo) shouldBe false
        repo.logIfAbsent("t1", "a2", AdviceOutcome.Skipped) shouldBe true
        repo.logs("t1").first() shouldBe listOf(AdviceLog("a1", AdviceOutcome.Done), AdviceLog("a2", AdviceOutcome.Skipped))
    }

    @Test
    fun replaceAllSetsOneTripsLogsExactly() = runTest {
        val repo = logs(File(tmp.root, "advice_logs_replace.json"))
        repo.log("t1", "stale", AdviceOutcome.Done)
        repo.log("t2", "a1", AdviceOutcome.CantDo)
        repo.replaceAll("t1", listOf(AdviceLog("a1", AdviceOutcome.Skipped)))
        repo.logs("t1").first() shouldBe listOf(AdviceLog("a1", AdviceOutcome.Skipped))
        repo.logs("t2").first() shouldBe listOf(AdviceLog("a1", AdviceOutcome.CantDo))
        repo.replaceAll("t1", emptyList())
        repo.logs("t1").first().shouldBeEmpty()
    }

    // endregion
}
