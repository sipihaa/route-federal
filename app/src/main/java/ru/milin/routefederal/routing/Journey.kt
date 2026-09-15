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

    fun signature(): List<LegSignature> {
        val result = mutableListOf<LegSignature>()
        for (leg in legs) {
            val previous = result.lastOrNull()
            if (previous != null && previous.transport == leg.transport && previous.routeId == leg.routeId &&
                previous.toStationId == leg.fromStationId) {
                // Waiting for another departure of the same line does not create a new route alternative.
                result[result.lastIndex] = previous.copy(toStationId = leg.toStationId)
            } else {
                result.add(LegSignature(leg.transport, leg.routeId, leg.fromStationId, leg.toStationId))
            }
        }
        return result
    }

    fun waitingMinutesBefore(legIndex: Int): Long {
        require(legIndex in legs.indices)
        if (legIndex == 0) return 0
        return Duration.between(legs[legIndex - 1].arrival, legs[legIndex].departure).toMinutes()
    }
}

enum class SearchStatus { COMPLETED_IN_WINDOW, CANCELLED, RESOURCE_LIMIT, INVALID_DATA, DATE_OUT_OF_RANGE, EXPIRED_DATA }

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
