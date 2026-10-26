package ru.vladm.routefederal.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.OffsetDateTime

@Serializable
data class Ride(
    val fromStationId: String,
    val toStationId: String,
    @Serializable(with = DateTimeSerializer::class) val departure: OffsetDateTime,
    @Serializable(with = DateTimeSerializer::class) val arrival: OffsetDateTime,
    val serviceKey: String,
    val number: String,
    val title: String,
    val transportType: String,
)

object DateTimeSerializer : KSerializer<OffsetDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: OffsetDateTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): OffsetDateTime = OffsetDateTime.parse(decoder.decodeString())
}
