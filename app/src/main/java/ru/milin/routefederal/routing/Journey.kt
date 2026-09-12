package ru.milin.routefederal.routing

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

data class JourneyLeg(
    val tripId: String,
    val routeId: String,
    val routeName: String,
    val transport: TransportType,
    val fromStationId: String,
    val toStationId: String,
    val departure: Instant,
    val arrival: Instant,
    val serviceDate: LocalDate,
    val stopIds: List<String>
)

data class LegSignature(
    val transport: TransportType,
    val routeId: String,
    val fromStationId: String,
    val toStationId: String
)

data class Journey(val legs: List<JourneyLeg>) {
    val departure: Instant get() = legs.first().departure
    val arrival: Instant get() = legs.last().arrival
    val durationMinutes: Long get() = Duration.between(departure, arrival).toMinutes()
    val transferCount: Int get() = legs.size - 1

    fun signature(): List<LegSignature> = legs.map {
        LegSignature(it.transport, it.routeId, it.fromStationId, it.toStationId)
    }

    fun waitingMinutesBefore(legIndex: Int): Long {
        require(legIndex in legs.indices)
        if (legIndex == 0) return 0
        return Duration.between(legs[legIndex - 1].arrival, legs[legIndex].departure).toMinutes()
    }
}

enum class SearchStatus { COMPLETED_IN_WINDOW, CANCELLED, RESOURCE_LIMIT, INVALID_DATA, DATE_OUT_OF_RANGE }

data class RouteSearchResult(
    val routes: List<Journey>,
    val status: SearchStatus,
    val windowStart: Instant?,
    val windowEndExclusive: Instant?,
    val examinedStates: Int,
    val coverageNote: String,
    val limitations: List<String>,
    val errors: List<String> = emptyList()
)
