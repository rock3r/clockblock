package dev.sebastiano.clockblocker.opus.core.data.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset

/**
 * The JSON dialect for everything the app persists or exports.
 *
 * - `ignoreUnknownKeys`: files written by a newer app version still load in an older one.
 * - `coerceInputValues`: an unknown enum value (from a newer version) falls back to the property default
 *   instead of failing the whole document.
 * - `encodeDefaults`: files are self-describing, so changing a default later doesn't silently change data.
 */
val OpusJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
    explicitNulls = false
}

/**
 * DataStore [Serializer] backed by kotlinx-serialization JSON. Any decode failure is surfaced as a
 * [CorruptionException], which DataStore routes to the store's corruption handler.
 *
 * Writes are atomic: DataStore writes to a temp file and renames it over the original.
 */
internal class JsonSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
    private val json: Json = OpusJson,
) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T {
        val text = input.readBytes().decodeToString()
        if (text.isBlank()) return defaultValue
        return try {
            json.decodeFromString(serializer, text)
        } catch (e: SerializationException) {
            throw CorruptionException("Unreadable JSON document", e)
        } catch (e: IllegalArgumentException) {
            // Model invariants (e.g. `require` in Trip's init) surface as IllegalArgumentException.
            throw CorruptionException("JSON document violates model invariants", e)
        }
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(json.encodeToString(serializer, t).encodeToByteArray())
    }
}

/**
 * Factory for the app's JSON DataStores.
 *
 * On corruption the unreadable file is first copied to `<name>.corrupt-<timestamp>` next to the original
 * (so nothing the user entered is destroyed and it can be recovered or attached to a bug report), and the
 * store then restarts from [default]. The app keeps working instead of crash-looping.
 */
internal object JsonDataStores {
    fun <T> create(
        file: File,
        serializer: KSerializer<T>,
        default: T,
        scope: CoroutineScope,
        clock: Clock = Clock.systemUTC(),
        json: Json = OpusJson,
    ): DataStore<T> = DataStoreFactory.create(
        serializer = JsonSerializer(serializer, default, json),
        corruptionHandler = ReplaceFileCorruptionHandler { _ ->
            backUpCorruptFile(file, clock)
            default
        },
        scope = scope,
        produceFile = { file },
    )

    private val stampFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.ROOT).withZone(ZoneOffset.UTC)

    /** Copies [file] aside; returns the backup, or null if there was nothing to back up. */
    fun backUpCorruptFile(file: File, clock: Clock): File? {
        if (!file.exists()) return null
        val backup = File(file.parentFile, "${file.name}.corrupt-${stampFormat.format(clock.instant())}")
        return runCatching { file.copyTo(backup, overwrite = true) }.getOrNull()
    }
}
