package dev.sebastiano.clockblocker.opus.core.data.backup

import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.Intensity
import dev.sebastiano.clockblocker.opus.core.model.ThemeMode
import dev.sebastiano.clockblocker.opus.core.testing.FakeAdviceLogRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeProfileRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeSettingsRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeTripRepository
import dev.sebastiano.clockblocker.opus.core.testing.MutableClock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class BackupCodecTest {
    private val codec = BackupCodec()
    private val full = Backup(
        exportedAt = Instant.parse("2026-06-01T12:00:00Z"),
        profile = DemoData.profile.copy(intensity = Intensity.Max, useMelatonin = true),
        settings = AppSettings(themeMode = ThemeMode.Dark, reminderLeadMinutes = 30),
        trips = DemoData.trips(),
        adviceLogs = mapOf(DemoData.SfoLhrId to listOf(AdviceLog("a1", AdviceOutcome.Done), AdviceLog("a2", AdviceOutcome.CantDo))),
    )

    @Test
    fun `round trips everything`() {
        codec.decode(codec.encode(full)) shouldBe full
    }

    @Test
    fun `output is versioned, readable JSON`() {
        val text = codec.encode(full)
        text shouldContain "\"version\": 1"
        text shouldContain "\"format\": \"clockblock\""
        text shouldContain "\"zoneId\": \"Europe/London\""
        text shouldContain "\"departureLocal\": \"2026-06-15T19:30\""
    }

    @Test
    fun `newer versions are refused`() {
        val newer = codec.encode(full).replace("\"version\": 1", "\"version\": 7")
        val e = shouldThrow<BackupException.UnsupportedVersion> { codec.decode(newer) }
        e.found shouldBe 7
        e.supported shouldBe BackupCodec.CurrentVersion
    }

    @Test
    fun `other files are rejected clearly`() {
        shouldThrow<BackupException.Malformed> { codec.decode("not json at all") }
        shouldThrow<BackupException.Malformed> { codec.decode("[1,2,3]") }
        shouldThrow<BackupException.WrongFormat> { codec.decode("""{"version":1,"format":"other-app"}""") }
        shouldThrow<BackupException.WrongFormat> { codec.decode("""{"version":1}""") }
        shouldThrow<BackupException.Malformed> { codec.decode("""{"format":{"nested":true}}""") }
        shouldThrow<BackupException.Malformed> { codec.decode("""{"format":"clockblock"}""") }
        shouldThrow<BackupException.Malformed> { codec.decode("""{"format":"clockblock","version":"one"}""") }
        shouldThrow<BackupException.Malformed> {
            codec.decode("""{"format":"clockblock","version":1,"exportedAt":"yesterday"}""")
        }
    }

    @Test
    fun `backups made before the rename still import`() {
        val legacy = codec.encode(full).replace("\"format\": \"clockblock\"", "\"format\": \"opus-clockblock\"")
        legacy shouldContain "\"format\": \"opus-clockblock\""
        codec.decode(legacy) shouldBe full
    }

    @Test
    fun `trips with unknown zones are rejected before import`() {
        val bad = codec.encode(full).replace("Europe/London", "Mars/Olympus_Mons")
        shouldThrow<BackupException.Malformed> { codec.decode(bad) }
    }

    @Test
    fun `unknown fields from newer minor versions are ignored`() {
        val text = codec.encode(full).replaceFirst("{", "{\n  \"shiny\": {\"new\": [1]},")
        codec.decode(text) shouldBe full
    }

    @Test
    fun `minimal backup uses defaults`() {
        val b = codec.decode("""{"format":"clockblock","version":1,"exportedAt":"2026-01-01T00:00:00Z"}""")
        b.profile shouldBe null
        b.trips shouldBe emptyList()
        b.settings shouldBe AppSettings()
    }

    @Test
    fun `file name carries the date`() {
        BackupCodec.fileName(Instant.parse("2026-06-01T12:00:00Z")) shouldBe "clockblock-backup-2026-06-01.json"
    }
}

class BackupManagerTest {
    private class Device(
        val profiles: FakeProfileRepository = FakeProfileRepository(),
        val settings: FakeSettingsRepository = FakeSettingsRepository(),
        val trips: FakeTripRepository = FakeTripRepository(),
        val logs: FakeAdviceLogRepository = FakeAdviceLogRepository(),
    ) {
        val manager = BackupManager(profiles, settings, trips, logs, BackupCodec(), MutableClock())
    }

    @Test
    fun `export then import reproduces the device`() = runTest {
        val source = Device(
            profiles = FakeProfileRepository(DemoData.profile),
            settings = FakeSettingsRepository(AppSettings(reminderLeadMinutes = 30)),
            trips = FakeTripRepository(DemoData.trips()),
        )
        source.logs.log(DemoData.SfoLhrId, "a1", AdviceOutcome.Done)
        source.logs.log("deleted-trip", "zz", AdviceOutcome.Done) // Orphans are not exported.

        val target = Device()
        val result = target.manager.import(source.manager.export())

        result shouldBe ImportResult(tripsImported = 2, tripsDeleted = 0, logsImported = 1)
        target.profiles.current shouldBe DemoData.profile
        target.settings.current.reminderLeadMinutes shouldBe 30
        target.trips.current shouldBe DemoData.trips()
        target.logs.current(DemoData.SfoLhrId) shouldBe listOf(AdviceLog("a1", AdviceOutcome.Done))
        target.manager.snapshot().adviceLogs.keys shouldBe setOf(DemoData.SfoLhrId)
    }

    @Test
    fun `replace removes trips missing from the backup, merge keeps them`() = runTest {
        val backup = Device(trips = FakeTripRepository(listOf(DemoData.sfoToLhr()))).manager.export()
        val extra = DemoData.lhrToSydneyViaSingapore()

        val replaced = Device(trips = FakeTripRepository(listOf(extra)))
        replaced.manager.import(backup, ImportMode.Replace).tripsDeleted shouldBe 1
        replaced.trips.trips.first().map { it.id } shouldContainExactly listOf(DemoData.SfoLhrId)

        val merged = Device(trips = FakeTripRepository(listOf(extra)), settings = FakeSettingsRepository(AppSettings(dynamicColor = false)))
        merged.manager.import(backup, ImportMode.Merge).tripsDeleted shouldBe 0
        merged.trips.current.map { it.id } shouldContainExactly listOf(DemoData.SfoLhrId, DemoData.LhrSydId)
        merged.settings.current.dynamicColor shouldBe false // Merge doesn't touch settings.
    }

    @Test
    fun `merge keeps the device profile when there is one`() = runTest {
        val backup = Device(profiles = FakeProfileRepository(DemoData.profile.copy(intensity = Intensity.Max))).manager.export()
        val device = Device(profiles = FakeProfileRepository(DemoData.profile))

        device.manager.import(backup, ImportMode.Merge)

        device.profiles.current shouldBe DemoData.profile
    }

    @Test
    fun `merge takes the backup profile when the device has none`() = runTest {
        val profile = DemoData.profile.copy(intensity = Intensity.Max)
        val backup = Device(profiles = FakeProfileRepository(profile)).manager.export()
        val device = Device()

        device.manager.import(backup, ImportMode.Merge)

        device.profiles.current shouldBe profile
    }

    @Test
    fun `merge keeps the device version of a trip in both places and only adds new ones`() = runTest {
        val backupTrip = DemoData.sfoToLhr().copy(title = "From the backup")
        val backup = Device(trips = FakeTripRepository(listOf(backupTrip, DemoData.lhrToSydneyViaSingapore()))).manager.export()
        val deviceTrip = DemoData.sfoToLhr().copy(title = "On this phone")
        val device = Device(trips = FakeTripRepository(listOf(deviceTrip)))

        val result = device.manager.import(backup, ImportMode.Merge)

        result.tripsImported shouldBe 1
        device.trips.current shouldContainExactly listOf(deviceTrip, DemoData.lhrToSydneyViaSingapore())
    }

    @Test
    fun `merge keeps check-ins already on the device and adds the missing ones`() = runTest {
        val source = Device(trips = FakeTripRepository(listOf(DemoData.sfoToLhr())))
        source.logs.log(DemoData.SfoLhrId, "a1", AdviceOutcome.CantDo)
        source.logs.log(DemoData.SfoLhrId, "a2", AdviceOutcome.Done)
        val device = Device(trips = FakeTripRepository(listOf(DemoData.sfoToLhr())))
        device.logs.log(DemoData.SfoLhrId, "a1", AdviceOutcome.Done)

        val result = device.manager.import(source.manager.export(), ImportMode.Merge)

        result.logsImported shouldBe 1
        device.logs.outcomeOf(DemoData.SfoLhrId, "a1") shouldBe AdviceOutcome.Done
        device.logs.outcomeOf(DemoData.SfoLhrId, "a2") shouldBe AdviceOutcome.Done
    }

    @Test
    fun `replace still takes the backup's profile, trips and check-ins`() = runTest {
        val backupProfile = DemoData.profile.copy(intensity = Intensity.Max)
        val backupTrip = DemoData.sfoToLhr().copy(title = "From the backup")
        val source = Device(profiles = FakeProfileRepository(backupProfile), trips = FakeTripRepository(listOf(backupTrip)))
        source.logs.log(DemoData.SfoLhrId, "a1", AdviceOutcome.CantDo)
        val device = Device(profiles = FakeProfileRepository(DemoData.profile), trips = FakeTripRepository(listOf(DemoData.sfoToLhr())))
        device.logs.log(DemoData.SfoLhrId, "a1", AdviceOutcome.Done)

        device.manager.import(source.manager.export(), ImportMode.Replace)

        device.profiles.current shouldBe backupProfile
        device.trips.current shouldContainExactly listOf(backupTrip)
        device.logs.outcomeOf(DemoData.SfoLhrId, "a1") shouldBe AdviceOutcome.CantDo
    }

    @Test
    fun `a bad file changes nothing`() = runTest {
        val device = Device(trips = FakeTripRepository(DemoData.trips()))
        shouldThrow<BackupException> { device.manager.import("{ nope") }
        device.trips.current shouldBe DemoData.trips()
    }
}
