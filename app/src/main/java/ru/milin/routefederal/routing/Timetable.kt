package ru.milin.routefederal.routing

import java.time.LocalDate

enum class TransportType { BUS, TRAIN }

data class Station(
    val id: String,
    val name: String,
    val region: String,
    val latitude: Double?,
    val longitude: Double?,
    val timeZone: String,
    val minTransferMinutes: Int? = null
)

data class Timetable(
    val stations: List<Station>,
    val trips: List<ScheduledTrip>,
    val snapshotDate: LocalDate,
    val coverageNote: String,
    val validFrom: LocalDate? = null,
    val validUntil: LocalDate? = null
)

data class ScheduledTrip(
    val id: String,
    val routeId: String,
    val routeName: String,
    val transport: TransportType,
    val daysOfWeek: Set<Int>,
    val seasonStart: String,
    val seasonEnd: String,
    val calls: List<StopCall>,
    val timeZone: String,
    val includedDates: Set<LocalDate> = emptySet(),
    val excludedDates: Set<LocalDate> = emptySet()
)

/** Minutes from midnight in the trip's reference time zone, with known day offsets. */
data class StopCall(
    val stationId: String,
    val arrivalMinutes: Int?,
    val departureMinutes: Int?
)
