package dev.sebastiano.clockblocker.opus.core.data.backup

import dev.sebastiano.clockblocker.opus.core.data.AdviceLogRepository
import dev.sebastiano.clockblocker.opus.core.data.ProfileRepository
import dev.sebastiano.clockblocker.opus.core.data.SettingsRepository
import dev.sebastiano.clockblocker.opus.core.data.TripRepository
import dev.sebastiano.clockblocker.opus.core.data.datastore.ClockblockJson
import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.InstantIsoSerializer
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.Instant

/**
 * Everything the user owns, as one portable document. Plans are not included: they're derived and are
 * recomputed after import.
 */
@Serializable
data class Backup(
    /** Format version; see [BackupCodec.CurrentVersion]. */
    val version: Int = BackupCodec.CurrentVersion,
    /** Always "clockblock", so a random JSON file is rejected with a clear error. */
    val format: String = BackupCodec.FormatName,
    @Serializable(with = InstantIsoSerializer::class) val exportedAt: Instant,
    val profile: UserProfile? = null,
    val settings: AppSettings = AppSettings(),
    val trips: List<Trip> = emptyList(),
    /** Trip id -> logged advice outcomes. */
    val adviceLogs: Map<String, List<AdviceLog>> = emptyMap(),
)

/** Why a backup couldn't be read. */
sealed class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Not JSON, or not shaped like a backup. */
    class Malformed(cause: Throwable?) : BackupException("This file isn't a Clockblock backup", cause)

    /** JSON, but some other app's. */
    class WrongFormat(val found: String?) : BackupException("Not a Clockblock backup (format '$found')")

    /** Written by a newer app version; update the app to import it. */
    class UnsupportedVersion(val found: Int, val supported: Int) :
        BackupException("Backup version $found is newer than this app supports ($supported)")
}

/**
 * Encodes/decodes [Backup] as human-readable, versioned JSON. Pure (no IO); see [BackupManager] for
 * reading and writing the repositories.
 *
 * Compatibility: unknown fields are ignored (newer minor additions import fine), older versions are
 * migrated on decode, and versions newer than [CurrentVersion] are refused rather than half-imported.
 */
@Inject
class BackupCodec {
    private val json = Json(ClockblockJson) { prettyPrint = true }

    fun encode(backup: Backup): String = json.encodeToString(Backup.serializer(), backup)

    @Throws(BackupException::class)
    fun decode(text: String): Backup {
        val root = malformedOnFailure { json.parseToJsonElement(text).jsonObject }
        val format = malformedOnFailure { root["format"]?.jsonPrimitive?.content }
        if (format != FormatName && format !in LegacyFormatNames) throw BackupException.WrongFormat(format)
        val version = malformedOnFailure { root["version"]?.jsonPrimitive?.intOrNull }
            ?: throw BackupException.Malformed(null)
        if (version > CurrentVersion) throw BackupException.UnsupportedVersion(version, CurrentVersion)
        return malformedOnFailure {
            val backup = json.decodeFromJsonElement(Backup.serializer(), root)
            // Fail now, not after import, if a trip references a zone this device doesn't know.
            backup.trips.forEach { trip -> trip.legs.forEach { it.origin.zone; it.destination.zone } }
            backup.copy(version = CurrentVersion, format = FormatName)
        }
    }

    private inline fun <T> malformedOnFailure(block: () -> T): T = try {
        block()
    } catch (e: SerializationException) {
        throw BackupException.Malformed(e)
    } catch (e: IllegalArgumentException) {
        throw BackupException.Malformed(e)
    } catch (e: java.time.DateTimeException) {
        throw BackupException.Malformed(e)
    }

    companion object {
        const val CurrentVersion: Int = 1
        const val FormatName: String = "clockblock"

        /** Format names written by earlier builds (the app was called Opus Clockblock); still imported. */
        val LegacyFormatNames: Set<String> = setOf("opus-clockblock")

        /** Suggested file name for an export made at [at]. */
        fun fileName(at: Instant): String = "clockblock-backup-${at.toString().take(10)}.json"
    }
}

/** How an import treats data already on the device. */
enum class ImportMode {
    /** The device ends up with exactly the backup's trips (others are deleted), profile and settings. */
    Replace,

    /**
     * Only adds: backup trips and advice logs that aren't on the device, and the backup's profile when the
     * device has none. Nothing on the device is changed or deleted.
     */
    Merge,
}

/** Summary of an import, for a confirmation snackbar. */
data class ImportResult(val tripsImported: Int, val tripsDeleted: Int, val logsImported: Int)

/** Exports/imports everything through the repositories, so all observers update immediately. */
@Inject
class BackupManager(
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
    private val trips: TripRepository,
    private val adviceLogs: AdviceLogRepository,
    private val codec: BackupCodec,
    private val clock: Clock,
) {
    suspend fun snapshot(): Backup {
        val allTrips = trips.trips.first()
        return Backup(
            exportedAt = clock.instant(),
            profile = profiles.profile.first(),
            settings = settings.settings.first(),
            trips = allTrips,
            adviceLogs = allTrips.associate { it.id to adviceLogs.logs(it.id).first() }.filterValues { it.isNotEmpty() },
        )
    }

    suspend fun export(): String = codec.encode(snapshot())

    /** Decodes [text] fully before touching any data, so a bad file never leaves a half-import. */
    @Throws(BackupException::class)
    suspend fun import(text: String, mode: ImportMode = ImportMode.Replace): ImportResult = restore(codec.decode(text), mode)

    suspend fun restore(backup: Backup, mode: ImportMode = ImportMode.Replace): ImportResult = when (mode) {
        ImportMode.Replace -> replace(backup)
        ImportMode.Merge -> merge(backup)
    }

    private suspend fun replace(backup: Backup): ImportResult {
        val keep = backup.trips.mapTo(HashSet()) { it.id }
        var deleted = 0
        trips.trips.first().filterNot { it.id in keep }.forEach { trips.delete(it.id); deleted++ }
        backup.profile?.let { profiles.save(it) }
        settings.update { backup.settings }
        backup.trips.forEach { trips.upsert(it) }
        var logs = 0
        backup.adviceLogs.forEach { (tripId, entries) ->
            entries.forEach { adviceLogs.log(tripId, it.adviceId, it.outcome); logs++ }
        }
        return ImportResult(tripsImported = backup.trips.size, tripsDeleted = deleted, logsImported = logs)
    }

    /** Only adds: the device's profile, trips and check-ins win over the backup's. */
    private suspend fun merge(backup: Backup): ImportResult {
        if (backup.profile != null && profiles.profile.first() == null) profiles.save(backup.profile)
        val onDevice = trips.trips.first().mapTo(HashSet()) { it.id }
        val added = backup.trips.filterNot { it.id in onDevice }
        added.forEach { trips.upsert(it) }
        var logs = 0
        backup.adviceLogs.forEach { (tripId, entries) ->
            val logged = adviceLogs.logs(tripId).first().mapTo(HashSet()) { it.adviceId }
            entries.filterNot { it.adviceId in logged }.forEach { adviceLogs.log(tripId, it.adviceId, it.outcome); logs++ }
        }
        return ImportResult(tripsImported = added.size, tripsDeleted = 0, logsImported = logs)
    }
}

