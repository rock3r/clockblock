package dev.sebastiano.clockblocker.opus.core.data.datastore

import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import java.time.ZoneId

/**
 * On-disk documents. Each store is one small JSON file with a `schemaVersion`, so a future version can
 * migrate old files (via DataStore migrations) instead of guessing.
 */
internal const val CurrentSchemaVersion = 1

@Serializable
internal data class ProfileDocument(
    val schemaVersion: Int = CurrentSchemaVersion,
    val profile: UserProfile? = null,
)

@Serializable
internal data class SettingsDocument(
    val schemaVersion: Int = CurrentSchemaVersion,
    val settings: AppSettings = AppSettings(),
)

@Serializable
internal data class AdviceLogDocument(
    val schemaVersion: Int = CurrentSchemaVersion,
    /** Trip id -> one entry per advice id (latest outcome wins). */
    val logs: Map<String, List<AdviceLog>> = emptyMap(),
)

/**
 * All trips. Trips are decoded one by one: a single unreadable trip (bad zone id, broken invariants) is
 * moved to [quarantined] verbatim instead of wiping every other trip, and is written back untouched so
 * a later version (or a human) can still recover it.
 */
@Serializable(with = TripsDocumentSerializer::class)
internal data class TripsDocument(
    val schemaVersion: Int = CurrentSchemaVersion,
    val trips: List<Trip> = emptyList(),
    val quarantined: List<JsonElement> = emptyList(),
)

internal object TripsDocumentSerializer : KSerializer<TripsDocument> {
    @Serializable
    private data class Surrogate(
        val schemaVersion: Int = CurrentSchemaVersion,
        val trips: List<JsonElement> = emptyList(),
        val quarantined: List<JsonElement> = emptyList(),
    )

    override val descriptor: SerialDescriptor = Surrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: TripsDocument) {
        val json = (encoder as JsonEncoder).json
        val surrogate = Surrogate(
            schemaVersion = value.schemaVersion,
            trips = value.trips.map { json.encodeToJsonElement(Trip.serializer(), it) },
            quarantined = value.quarantined,
        )
        encoder.encodeSerializableValue(Surrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): TripsDocument {
        val json = (decoder as JsonDecoder).json
        val surrogate = decoder.decodeSerializableValue(Surrogate.serializer())
        val good = mutableListOf<Trip>()
        val bad = surrogate.quarantined.toMutableList()
        for (element in surrogate.trips) {
            val trip = runCatching { json.decodeFromJsonElement(Trip.serializer(), element).also(::checkZones) }
            trip.fold(onSuccess = good::add, onFailure = { bad += element })
        }
        return TripsDocument(surrogate.schemaVersion, good, bad)
    }

    /** Fails if any leg references a zone this device's tzdata doesn't know. */
    private fun checkZones(trip: Trip) {
        trip.legs.forEach {
            ZoneId.of(it.origin.zoneId)
            ZoneId.of(it.destination.zoneId)
        }
    }
}
