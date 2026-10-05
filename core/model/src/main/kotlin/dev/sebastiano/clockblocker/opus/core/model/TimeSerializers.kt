package dev.sebastiano.clockblocker.opus.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** ISO-8601 string serializers for java.time types, so persisted JSON stays human-readable and diffable. */
private abstract class IsoSerializer<T : Any>(
    name: String,
    private val parse: (String) -> T,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): T = parse(decoder.decodeString())
}

object InstantIsoSerializer : KSerializer<Instant> by object :
    IsoSerializer<Instant>("dev.sebastiano.clockblocker.opus.Instant", Instant::parse) {}

object LocalDateTimeIsoSerializer : KSerializer<LocalDateTime> by object :
    IsoSerializer<LocalDateTime>("dev.sebastiano.clockblocker.opus.LocalDateTime", LocalDateTime::parse) {}

object LocalDateIsoSerializer : KSerializer<LocalDate> by object :
    IsoSerializer<LocalDate>("dev.sebastiano.clockblocker.opus.LocalDate", LocalDate::parse) {}

object LocalTimeIsoSerializer : KSerializer<LocalTime> by object :
    IsoSerializer<LocalTime>("dev.sebastiano.clockblocker.opus.LocalTime", LocalTime::parse) {}

object DurationIsoSerializer : KSerializer<Duration> by object :
    IsoSerializer<Duration>("dev.sebastiano.clockblocker.opus.Duration", Duration::parse) {}
