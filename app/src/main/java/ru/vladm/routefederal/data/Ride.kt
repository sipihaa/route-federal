package ru.vladm.routefederal.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.OffsetDateTime

// Участок: прямой проезд одной поездки рейса между двумя нашими станциями.
// serviceKey = "uid|start_date" — однозначно определяет поездку (рейс в конкретный день),
// по нему участки одной поездки склеиваются в один проезд без пересадки.
@Serializable
data class Ride(
    val id: String,
    val fromStationId: String,
    val toStationId: String,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val departure: OffsetDateTime,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val arrival: OffsetDateTime,
    val serviceKey: String,
    val number: String,
    val title: String,
    val transportType: String,
)

// Время хранится в JSON строкой ISO 8601 с часовым поясом, как его отдаёт API
object OffsetDateTimeSerializer : KSerializer<OffsetDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: OffsetDateTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): OffsetDateTime = OffsetDateTime.parse(decoder.decodeString())
}
