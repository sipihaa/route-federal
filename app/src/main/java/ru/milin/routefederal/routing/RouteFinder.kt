package ru.milin.routefederal.routing

import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.PriorityQueue

class RouteFinder(private val timetable: Timetable) {
    fun search(
        fromStationId: String,
        toStationId: String,
        localDate: LocalDate,
        horizonDays: Int = 7,
        maxStates: Int = 100_000,
        maxTimetableEvents: Int = 1_000_000,
        defaultTransferMinutes: Int = 30,
        isCancelled: () -> Boolean = { false }
    ): RouteSearchResult {
        return searchStations(setOf(fromStationId), setOf(toStationId), localDate, horizonDays,
            maxStates, maxTimetableEvents, defaultTransferMinutes, isCancelled)
    }

    fun searchCities(
        fromCityId: String, toCityId: String, localDate: LocalDate, horizonDays: Int = 8,
        defaultTransferMinutes: Int = 30, isCancelled: () -> Boolean = { false }
    ): RouteSearchResult {
        val cities = timetable.cities()
        val origins = cities.find { it.id == fromCityId }?.stations.orEmpty().map { it.id }.toSet()
        val destinations = cities.find { it.id == toCityId }?.stations.orEmpty().map { it.id }.toSet()
        return searchStations(origins, destinations, localDate, horizonDays, 100_000,
            1_000_000, defaultTransferMinutes, isCancelled)
    }

    private fun searchStations(
        origins: Set<String>, destinations: Set<String>, localDate: LocalDate, horizonDays: Int,
        maxStates: Int, maxTimetableEvents: Int, defaultTransferMinutes: Int,
        isCancelled: () -> Boolean
    ): RouteSearchResult {
        if (isCancelled()) return emptyResult(SearchStatus.CANCELLED)
        val errors = TimetableValidator.validate(timetable).toMutableList()
        val stations = timetable.stations.associateBy { it.id }
        if (origins.isEmpty() || destinations.isEmpty() ||
            (origins + destinations).any { it !in stations }) errors.add("Неизвестный пункт поиска.")
        if (origins.any { it in destinations }) errors.add("Выберите разные начальный и конечный пункты.")
        val originCities = origins.mapNotNull { stations[it]?.cityId }.toSet()
        if (destinations.any { stations[it]?.cityId in originCities }) {
            errors.add("Внутригородские поездки не входят в область поиска.")
        }
        if (horizonDays < 1) errors.add("Окно поиска должно быть не менее одного дня.")
        if (defaultTransferMinutes !in 0..240) errors.add("Запас пересадки должен быть от 0 до 240 минут.")
        if (maxStates < 1 || maxTimetableEvents < 1) errors.add("Лимиты поиска должны быть положительными.")
        if (timetable.trips.isEmpty()) errors.add("Расписание не содержит рейсов.")
        if (isCancelled()) return emptyResult(SearchStatus.CANCELLED)
        if (errors.isNotEmpty()) return emptyResult(SearchStatus.INVALID_DATA, errors)
        if (timetable.expiresAt != null && !Instant.now().isBefore(timetable.expiresAt)) {
            return emptyResult(SearchStatus.EXPIRED_DATA, listOf("Срок кэша истёк. Загрузите обновлённое расписание."))
        }
        if ((timetable.validFrom != null && localDate < timetable.validFrom) ||
            (timetable.validUntil != null && localDate > timetable.validUntil)) {
            return emptyResult(SearchStatus.DATE_OUT_OF_RANGE, listOf("Дата вне периода загруженных данных: ${timetable.validFrom} — ${timetable.validUntil}."))
        }

        return try {
            val originZone = ZoneId.of(stations.getValue(origins.first()).timeZone)
            val windowStart = localDate.atStartOfDay(originZone).toInstant()
            var endDate = localDate.plusDays(horizonDays.toLong())
            timetable.validUntil?.let { if (endDate > it.plusDays(1)) endDate = it.plusDays(1) }
            val windowEnd = endDate.atStartOfDay(originZone).toInstant()
            SearchRun(
                timetable, stations, origins, destinations, originZone,
                windowStart, windowEnd, maxStates, maxTimetableEvents, defaultTransferMinutes, isCancelled
            ).execute()
        } catch (error: DateTimeException) {
            emptyResult(SearchStatus.INVALID_DATA, listOf(error.message ?: "Некорректная дата или время."))
        } catch (_: ArithmeticException) {
            emptyResult(SearchStatus.INVALID_DATA, listOf("Дата или окно поиска выходит за допустимый диапазон."))
        }
    }

    private fun emptyResult(status: SearchStatus, errors: List<String> = emptyList()): RouteSearchResult {
        return RouteSearchResult(emptyList(), status, null, null, 0, timetable.coverageNote, emptyList(), errors)
    }
}

private data class TimedCall(val stationId: String, val arrival: Instant?, val departure: Instant?)
private data class TripInstance(val trip: ScheduledTrip, val serviceDate: LocalDate, val calls: List<TimedCall>)
private data class Boarding(val instance: TripInstance, val callIndex: Int, val departure: Instant)
private data class PathState(val journey: Journey, val visitedStations: Set<String>)

private class SearchRun(
    private val timetable: Timetable,
    private val stations: Map<String, Station>,
    private val origins: Set<String>,
    private val destinations: Set<String>,
    private val originZone: ZoneId,
    private val windowStart: Instant,
    private val windowEnd: Instant,
    private val maxStates: Int,
    private val maxTimetableEvents: Int,
    private val defaultTransferMinutes: Int,
    private val isCancelled: () -> Boolean
) {
    private val boardings = mutableMapOf<String, MutableList<Boarding>>()
    private val queue = PriorityQueue<PathState> { first, second -> compareJourneys(first.journey, second.journey) }
    private val best = mutableMapOf<List<LegSignature>, Journey>()
    private var generatedStates = 0
    private var examinedStates = 0
    private var preparationSteps = 0
    private var stoppedWith: SearchStatus? = null

    fun execute(): RouteSearchResult {
        if (!prepareBoardings()) return result(stoppedWith ?: SearchStatus.RESOURCE_LIMIT)
        for (origin in origins) {
            for (boarding in boardings[origin].orEmpty()) {
                val selectedDate = windowStart.atZone(originZone).toLocalDate()
                if (boarding.departure.atZone(originZone).toLocalDate() != selectedDate) continue
                if (!addRides(null, boarding)) return result(stoppedWith ?: SearchStatus.RESOURCE_LIMIT)
            }
        }
        while (queue.isNotEmpty()) {
            if (checkCancellation()) return result(SearchStatus.CANCELLED)
            val currentBest = sortedBest()
            // Equal durations still need examination: transfers and departure break ties.
            if (currentBest.size >= 3 && duration(queue.element().journey) > duration(currentBest[2])) break
            val state = queue.remove()
            examinedStates++
            val lastLeg = state.journey.legs.last()
            val nextBoardings = boardings[lastLeg.toStationId].orEmpty()
            val minimum = stations.getValue(lastLeg.toStationId).minTransferMinutes ?: defaultTransferMinutes
            val readyAt = lastLeg.arrival.plusSeconds(minimum.toLong() * 60)
            for (boarding in nextBoardings) {
                if (checkCancellation()) return result(SearchStatus.CANCELLED)
                if (boarding.departure.isBefore(readyAt) || sameInstance(boarding, lastLeg)) continue
                // Later boardings cannot beat three already known complete routes.
                val third = sortedBest().getOrNull(2)
                if (third != null && Duration.between(state.journey.departure, boarding.departure).seconds > duration(third)) break
                if (!addRides(state, boarding)) return result(stoppedWith ?: SearchStatus.RESOURCE_LIMIT)
            }
        }
        return result(SearchStatus.COMPLETED_IN_WINDOW)
    }

    private fun prepareBoardings(): Boolean {
        for (trip in timetable.trips) {
            if (checkCancellation()) return false
            val zone = ZoneId.of(trip.timeZone)
            val seasonStart = MonthDay.parse("--${trip.seasonStart}")
            val seasonEnd = MonthDay.parse("--${trip.seasonEnd}")
            val lastCall = trip.calls.last()
            val lastMinutes = lastCall.departureMinutes ?: lastCall.arrivalMinutes!!
            // A boarding after midnight can belong to a service from an earlier day.
            var serviceDate = windowStart.atZone(zone).toLocalDate().minusDays(lastMinutes / 1440L + 1)
            val lastDate = windowEnd.minusNanos(1).atZone(zone).toLocalDate()
            while (!serviceDate.isAfter(lastDate)) {
                if (!preparationStep()) return false
                if (runsOn(trip, serviceDate, seasonStart, seasonEnd)) {
                    val calls = mutableListOf<TimedCall>()
                    for (call in trip.calls) {
                        if (!preparationStep()) return false
                        calls.add(TimedCall(
                            call.stationId,
                            eventTime(serviceDate, call.arrivalMinutes, zone),
                            eventTime(serviceDate, call.departureMinutes, zone)
                        ))
                    }
                    val instance = TripInstance(trip, serviceDate, calls)
                    for (index in 0 until calls.lastIndex) {
                        val departure = calls[index].departure ?: continue
                        if (!departure.isBefore(windowStart) && departure.isBefore(windowEnd)) {
                            boardings.getOrPut(calls[index].stationId) { mutableListOf() }
                                .add(Boarding(instance, index, departure))
                        }
                    }
                }
                serviceDate = serviceDate.plusDays(1)
            }
        }
        for (values in boardings.values) {
            if (checkCancellation()) return false
            values.sortBy { it.departure }
        }
        return !checkCancellation()
    }

    private fun addRides(previous: PathState?, boarding: Boarding): Boolean {
        val instance = boarding.instance
        val calls = instance.calls
        val boardingStation = calls[boarding.callIndex].stationId
        val visited = previous?.visitedStations ?: setOf(boardingStation)
        for (index in boarding.callIndex + 1..calls.lastIndex) {
            if (checkCancellation()) return false
            val call = calls[index]
            // Passing a previous station on board is allowed; a new alighting must not create a transfer loop.
            if (call.stationId in visited) continue
            val boardingCity = stations.getValue(boardingStation).cityId
            if (boardingCity != null && boardingCity == stations.getValue(call.stationId).cityId) continue
            val arrival = call.arrival ?: continue
            // A known final arrival may be later than the last available departure day.
            val third = sortedBest().getOrNull(2)
            val firstDeparture = previous?.journey?.departure ?: boarding.departure
            if (third != null && Duration.between(firstDeparture, arrival).seconds > duration(third)) continue
            val trip = instance.trip
            val leg = JourneyLeg(
                trip.id, trip.routeId, trip.routeName, trip.transport, boardingStation,
                call.stationId, boarding.departure, arrival, instance.serviceDate,
                calls.subList(boarding.callIndex, index + 1).map { it.stationId }
            )
            val legs = previous?.journey?.legs.orEmpty() + leg
            val journey = Journey(legs)
            if (call.stationId in destinations) {
                val signature = journey.signature()
                val old = best[signature]
                if (old == null || compareJourneys(journey, old) < 0) best[signature] = journey
                if (best.size > 3) {
                    val worst = best.values.maxWithOrNull(::compareJourneys)!!
                    best.remove(worst.signature())
                }
                break
            }
            if (generatedStates >= maxStates) {
                stoppedWith = SearchStatus.RESOURCE_LIMIT
                return false
            }
            queue.add(PathState(journey, visited + call.stationId))
            generatedStates++
        }
        return true
    }

    private fun preparationStep(): Boolean {
        if (checkCancellation()) return false
        if (preparationSteps >= maxTimetableEvents) {
            stoppedWith = SearchStatus.RESOURCE_LIMIT
            return false
        }
        preparationSteps++
        return true
    }

    private fun checkCancellation(): Boolean {
        if (isCancelled()) {
            stoppedWith = SearchStatus.CANCELLED
            return true
        }
        return false
    }

    private fun sameInstance(boarding: Boarding, leg: JourneyLeg): Boolean {
        return boarding.instance.trip.id == leg.tripId && boarding.instance.serviceDate == leg.serviceDate
    }

    private fun sortedBest(): List<Journey> = best.values.sortedWith(::compareJourneys).take(3)

    private fun result(status: SearchStatus): RouteSearchResult {
        val lastDeparture = windowEnd.atZone(originZone).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm (XXX)"))
        val limitations = mutableListOf(
            "Новые посадки рассматриваются до $lastDeparture. Прибытие уже начатого рейса может быть позже.",
            "При отсутствии правила станции запас пересадки — $defaultTransferMinutes мин. Это параметр расчёта, не норматив перевозчика.",
            "Рассматриваются маршруты без повторных посадок или высадок в уже посещённом пересадочном узле.",
            "Дата снимка ${timetable.snapshotDate} сама по себе не подтверждает актуальность расписания на дату поездки."
        )
        if (timetable.coverageNote.isNotBlank()) limitations.add(timetable.coverageNote)
        if (status == SearchStatus.RESOURCE_LIMIT) {
            limitations.add("Поиск остановлен из-за большого числа вариантов. Найденные маршруты могут быть не самыми быстрыми.")
        }
        if (status == SearchStatus.CANCELLED) limitations.add("Поиск отменён. Сравнение всех вариантов не завершено.")
        return RouteSearchResult(sortedBest(), status, windowStart, windowEnd, examinedStates, timetable.coverageNote, limitations)
    }
}

private fun runsOn(trip: ScheduledTrip, date: LocalDate, start: MonthDay, end: MonthDay): Boolean {
    if (date in trip.excludedDates) return false
    if (date in trip.includedDates) return true
    if (date.dayOfWeek.value !in trip.daysOfWeek) return false
    val day = MonthDay.from(date)
    if (start <= end) return day >= start && day <= end
    return day >= start || day <= end
}

private fun eventTime(date: LocalDate, minutes: Int?, zone: ZoneId): Instant? {
    if (minutes == null) return null
    val localTime = date.atStartOfDay().plusMinutes(minutes.toLong())
    val offsets = zone.rules.getValidOffsets(localTime)
    if (offsets.size != 1) throw DateTimeException("Неоднозначное или отсутствующее местное время $localTime в $zone.")
    return localTime.toInstant(offsets.single())
}

private fun duration(journey: Journey): Long = Duration.between(journey.departure, journey.arrival).seconds

private fun compareJourneys(first: Journey, second: Journey): Int {
    var result = duration(first).compareTo(duration(second))
    if (result != 0) return result
    result = first.transferCount.compareTo(second.transferCount)
    if (result != 0) return result
    result = first.departure.compareTo(second.departure)
    if (result != 0) return result
    for (index in first.legs.indices) {
        val a = first.legs[index]
        val b = second.legs[index]
        val aKeys = listOf(a.transport.name, a.routeId, a.tripId, a.serviceDate.toString(), a.fromStationId, a.toStationId)
        val bKeys = listOf(b.transport.name, b.routeId, b.tripId, b.serviceDate.toString(), b.fromStationId, b.toStationId)
        for (keyIndex in aKeys.indices) {
            result = aKeys[keyIndex].compareTo(bKeys[keyIndex])
            if (result != 0) return result
        }
    }
    return 0
}
