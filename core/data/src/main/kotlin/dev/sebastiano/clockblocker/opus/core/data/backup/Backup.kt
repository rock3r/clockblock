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
     * Only adds: backup trips and advice logs that aren't on the device (logs only onto matching trips and
     * profile), and the backup's profile when the device has none. Nothing on the device is changed or deleted.
     */
    Merge,
}

/**
 * Summary of an import, for a confirmation snackbar. On a [merged] import every count is what was added;
 * [profileImported] is true when the device had no profile and took the backup's.
 */
data class ImportResult(
    val tripsImported: Int,
    val tripsDeleted: Int,
    val logsImported: Int,
    val profileImported: Boolean = false,
    val merged: Boolean = false,
)

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
        val onDevice = trips.trips.first().mapTo(HashSet()) { it.id }
        var deleted = 0
        onDevice.filterNot { it in keep }.forEach { trips.delete(it); deleted++ }
        backup.profile?.let { profiles.save(it) }
        settings.update { backup.settings }
        var logs = 0
        backup.trips.forEach { trip ->
            val entries = backup.adviceLogs[trip.id].orEmpty()
            if (trip.id in onDevice) {
                entries.forEach { adviceLogs.log(trip.id, it.adviceId, it.outcome) }
            } else {
                // Check-ins a deleted trip with this id left behind belong to that trip, not this one.
                adviceLogs.replaceAll(trip.id, entries)
            }
            logs += entries.size
            trips.upsert(trip)
        }
        return ImportResult(tripsImported = backup.trips.size, tripsDeleted = deleted, logsImported = logs)
    }

    /**
     * Only adds: the device's profile, trips and check-ins win over the backup's.
     *
     * Advice ids are positional (trip, type, day, ordinal), so a check-in only means the same block on a plan
     * built from the same trip and profile. Check-ins are therefore added only for trips that end up identical
     * to the backup's, and only when the device ends up with the backup's profile. A trip the merge adds starts
     * from the backup's check-ins (or none): anything a deleted trip with the same id left behind is dropped.
     */
    private suspend fun merge(backup: Backup): ImportResult {
        val deviceProfile = profiles.profile.first()
        val adoptedProfile = backup.profile?.takeIf { deviceProfile == null }
        adoptedProfile?.let { profiles.save(it) }
        val sameProfile = (deviceProfile ?: backup.profile) == backup.profile
        val onDevice = trips.trips.first().associateBy { it.id }
        val added = backup.trips.filterNot { it.id in onDevice }
        var logs = 0
        added.forEach { trip ->
            val entries = if (sameProfile) backup.adviceLogs[trip.id].orEmpty() else emptyList()
            adviceLogs.replaceAll(trip.id, entries)
            logs += entries.size
            trips.upsert(trip)
        }
        if (sameProfile) {
            backup.trips.filter { onDevice[it.id] == it }.forEach { trip ->
                backup.adviceLogs[trip.id].orEmpty().forEach {
                    if (adviceLogs.logIfAbsent(trip.id, it.adviceId, it.outcome)) logs++
                }
            }
        }
        return ImportResult(
            tripsImported = added.size,
            tripsDeleted = 0,
            logsImported = logs,
            profileImported = adoptedProfile != null,
            merged = true,
        )
    }
}

