package ru.vladm.routefederal.search

import ru.vladm.routefederal.data.Leg
import ru.vladm.routefederal.data.Ride
import ru.vladm.routefederal.data.Route
import ru.vladm.routefederal.data.Station
import java.time.Duration
import java.time.LocalDate
import java.util.PriorityQueue

// Офлайн-поиск маршрутов по загруженным участкам. Чистый Kotlin, без Android.
//
// Граф: вершины — станции, рёбра — участки с конкретным временем отправления и прибытия.
// Поиск «лучший-первый» (вариант алгоритма Дейкстры для расписаний): из очереди с приоритетом
// всегда берётся частичный путь с наименьшим временем в пути от первого отправления.
// Время в пути при продлении пути только растёт, поэтому пути до цели извлекаются из очереди
// в порядке возрастания времени, и первые maxResults различных путей — лучшие.
class RouteFinder(
    stations: List<Station>,
    // Минимальное время на пересадку на той же станции
    private val minTransfer: Duration = Duration.ofMinutes(30),
    // Минимальное время на пересадку на другую станцию того же города (дорога между вокзалами)
    private val minCityTransfer: Duration = Duration.ofMinutes(90),
    // Максимальное ожидание на пересадке: варианты с ожиданием почти в сутки бесполезны
    private val maxTransferWait: Duration = Duration.ofHours(8),
    // Защита от слишком долгого поиска на больших данных
    private val maxExpanded: Int = 50_000,
) {

    // Станции каждого города: пересесть можно и на соседний вокзал/автовокзал
    private val stationsOfSameCity: Map<String, List<String>> = stations
        .groupBy { it.cityId }
        .values
        .flatMap { group -> group.map { station -> station.id to group.map { it.id } } }
        .toMap()

    // Частичный путь: участки по порядку и уже посещённые станции (чтобы не ходить по кругу)
    private class State(val rides: List<Ride>, val visited: Set<String>) {
        val last: Ride get() = rides.last()
        val duration: Duration = Duration.between(rides.first().departure, rides.last().arrival)
    }

    fun find(
        rides: List<Ride>,
        fromStationIds: Set<String>,
        toStationIds: Set<String>,
        date: LocalDate,
        maxResults: Int = 3,
    ): List<Route> {
        // Участки, отправляющиеся с каждой станции, по времени отправления
        val outgoing = rides.groupBy { it.fromStationId }

        val queue = PriorityQueue<State>(compareBy({ it.duration }, { it.rides.size }))

        // Начало пути: участки со станций города отправления, отправляющиеся в выбранный день
        fromStationIds.flatMap { outgoing[it].orEmpty() }
            .filter { it.departure.toLocalDate() == date && it.toStationId !in fromStationIds }
            .forEach { queue.add(State(listOf(it), setOf(it.fromStationId, it.toStationId))) }

        val results = linkedMapOf<String, Route>()
        var expanded = 0

        while (queue.isNotEmpty() && results.size < maxResults && expanded < maxExpanded) {
            val state = queue.poll()!!
            expanded++

            if (state.last.toStationId in toStationIds) {
                val route = toRoute(state.rides)
                // Одинаковые по составу варианты (тот же рейс теми же станциями) считаем одним:
                // первым из очереди пришёл самый быстрый из них
                results.putIfAbsent(signature(route), route)
                continue
            }

            val here = state.last.toStationId
            val nearby = stationsOfSameCity[here] ?: listOf(here)
            for (station in nearby) {
                for (next in outgoing[station].orEmpty()) {
                    if (next.toStationId in state.visited || next.toStationId in fromStationIds) continue
                    val sameTrip = next.serviceKey == state.last.serviceKey && station == here
                    // Продолжение той же поездки — без ожидания, пересадка — с запасом времени
                    val wait = when {
                        sameTrip -> Duration.ZERO
                        station == here -> minTransfer
                        else -> minCityTransfer
                    }
                    if (next.departure.isBefore(state.last.arrival.plus(wait))) continue
                    if (!sameTrip && next.departure.isAfter(state.last.arrival.plus(maxTransferWait))) continue
                    queue.add(State(state.rides + next, state.visited + next.fromStationId + next.toStationId))
                }
            }
        }
        return results.values.toList()
    }

    // Подряд идущие участки одной поездки склеиваются в один проезд
    private fun toRoute(rides: List<Ride>): Route {
        val legs = mutableListOf<Leg>()
        var group = mutableListOf(rides.first())
        for (ride in rides.drop(1)) {
            if (ride.serviceKey == group.last().serviceKey) {
                group.add(ride)
            } else {
                legs.add(toLeg(group))
                group = mutableListOf(ride)
            }
        }
        legs.add(toLeg(group))
        return Route(legs)
    }

    private fun toLeg(group: List<Ride>) = Leg(
        fromStationId = group.first().fromStationId,
        toStationId = group.last().toStationId,
        departure = group.first().departure,
        arrival = group.last().arrival,
        number = group.first().number,
        title = group.first().title,
        transportType = group.first().transportType,
    )

    // Рейс и станции каждого проезда, без даты: разные дни одного поезда — один вариант
    private fun signature(route: Route): String =
        route.legs.joinToString("|") { "${it.number}/${it.title}@${it.fromStationId}>${it.toStationId}" }
}
