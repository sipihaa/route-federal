package ru.vladm.routefederal.data

import java.time.Duration
import java.time.OffsetDateTime

data class Route(val legs: List<Leg>) {
    val departure: OffsetDateTime get() = legs.first().departure
    val arrival: OffsetDateTime get() = legs.last().arrival
    val duration: Duration get() = Duration.between(departure, arrival)
    val transfers: Int get() = legs.size - 1
}

data class Leg(
    val fromStationId: String,
    val toStationId: String,
    val departure: OffsetDateTime,
    val arrival: OffsetDateTime,
    val number: String,
    val title: String,
    val transportType: String,
)
