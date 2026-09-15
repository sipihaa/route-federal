package ru.milin.routefederal.routing

import java.time.LocalDate
import java.time.Instant

enum class TransportType(val title: String) { BUS("Автобус"), TRAIN("Поезд"), SUBURBAN("Электричка") }

data class Station(
    val id: String,
    val name: String,
    val region: String,
    val latitude: Double?,
    val longitude: Double?,
    val timeZone: String,
    val minTransferMinutes: Int? = null,
    val cityId: String? = null,
    val cityName: String? = null
)

data class Timetable(
    val stations: List<Station>,
    val trips: List<ScheduledTrip>,
    val snapshotDate: LocalDate,
    val coverageNote: String,
    val validFrom: LocalDate? = null,
    val validUntil: LocalDate? = null,
    val expiresAt: Instant? = null
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

/** A city groups endpoints only. It never connects its stations for transfers. */
data class City(val id: String, val name: String, val stations: List<Station>)

fun Timetable.cities(): List<City> = stations.groupBy { it.cityId ?: it.id }.map { (id, stops) ->
    City(id, stops.first().cityName ?: stops.first().name, stops)
}.sortedBy { it.name }
