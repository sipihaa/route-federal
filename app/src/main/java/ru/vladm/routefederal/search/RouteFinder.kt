package ru.vladm.routefederal.search

import ru.vladm.routefederal.data.Leg
import ru.vladm.routefederal.data.Ride
import ru.vladm.routefederal.data.Route
import java.time.Duration
import java.time.LocalDate
import java.util.PriorityQueue

// Офлайн-поиск маршрутов по загруженным участкам. Чистый Kotlin, без Android.
//
// Поиск «лучший-первый» (вариант алгоритма Дейкстры для расписаний): из очереди с приоритетом
// всегда берётся частичный путь с наименьшим временем в пути. Время в пути при продлении
// пути только растёт, поэтому первые найденные пути до цели — самые быстрые.
class RouteFinder {

    private class Path(val rides: List<Ride>) {
        val last: Ride get() = rides.last()
        val duration: Duration = Duration.between(rides.first().departure, rides.last().arrival)
    }

    fun find(
        rides: List<Ride>,
        fromStationIds: Set<String>,
        toStationIds: Set<String>,
        date: LocalDate,
    ): List<Route> {
        val ridesFrom = rides.groupBy { it.fromStationId }
        val queue = PriorityQueue<Path>(compareBy { it.duration })

        // Начало: участки со станций города отправления, отправляющиеся в выбранный день
        rides.filter { it.fromStationId in fromStationIds && it.departure.toLocalDate() == date }
            .forEach { queue.add(Path(listOf(it))) }

        val results = mutableListOf<Route>()
        var steps = 0
        // steps ограничивает перебор, если маршрута нет вовсе
        while (queue.isNotEmpty() && results.size < MAX_RESULTS && steps < MAX_STEPS) {
            val path = queue.poll()!!
            steps++

            if (path.last.toStationId in toStationIds) {
                val route = toRoute(path.rides)
                // Один и тот же вариант может получиться разными путями (например, прямым
                // участком Москва → Нижний и цепочкой Москва → Владимир → Нижний того же поезда)
                if (route !in results) results.add(route)
                continue
            }

            val visited = path.rides.map { it.fromStationId }
            for (next in ridesFrom[path.last.toStationId].orEmpty()) {
                if (next.toStationId in visited) continue
                // Та же поездка — едем дальше без ожидания, иначе это пересадка
                val sameTrip = next.serviceKey == path.last.serviceKey
                val wait = Duration.between(path.last.arrival, next.departure)
                if (sameTrip && wait.isNegative) continue
                if (!sameTrip && (wait < MIN_TRANSFER || wait > MAX_TRANSFER)) continue
                queue.add(Path(path.rides + next))
            }
        }
        return results
    }

    // Подряд идущие участки одной поездки склеиваются в один проезд (Leg)
    private fun toRoute(rides: List<Ride>): Route {
        val groups = mutableListOf(mutableListOf(rides.first()))
        for (ride in rides.drop(1)) {
            if (ride.serviceKey == groups.last().last().serviceKey) groups.last().add(ride)
            else groups.add(mutableListOf(ride))
        }
        return Route(groups.map { group ->
            Leg(
                fromStationId = group.first().fromStationId,
                toStationId = group.last().toStationId,
                departure = group.first().departure,
                arrival = group.last().arrival,
                number = group.first().number,
                title = group.first().title,
                transportType = group.first().transportType,
            )
        })
    }

    private companion object {
        const val MAX_RESULTS = 3
        const val MAX_STEPS = 50_000
        // Минимальное время на пересадку и максимальное ожидание
        val MIN_TRANSFER: Duration = Duration.ofMinutes(30)
        val MAX_TRANSFER: Duration = Duration.ofHours(8)
    }
}
