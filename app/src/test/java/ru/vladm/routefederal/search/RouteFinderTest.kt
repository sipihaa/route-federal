package ru.vladm.routefederal.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.vladm.routefederal.data.Leg
import ru.vladm.routefederal.data.Ride
import ru.vladm.routefederal.data.Route
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime

// Unit-тест офлайн-поиска маршрутов.
// Станции условные: A — город отправления, Z — город прибытия, B…F — промежуточные.
// У каждого варианта своя промежуточная станция, чтобы участки разных вариантов не стыковались.
// Каждый недопустимый вариант в данных специально быстрее одного из правильных:
// если алгоритм ошибётся, он попадёт в результат и изменит ответ.
class RouteFinderTest {

    private val date = LocalDate.of(2026, 10, 25)

    private fun time(day: Int, hhmm: String) = OffsetDateTime.parse("2026-10-${day}T$hhmm:00+03:00")

    private fun ride(trip: String, from: String, to: String, dep: String, arr: String, day: Int = 25) =
        Ride(
            fromStationId = from,
            toStationId = to,
            departure = time(day, dep),
            arrival = time(day, arr),
            serviceKey = "$trip|2026-10-$day",
            number = trip,
            title = "Рейс $trip",
            transportType = "train",
        )

    private val rides = listOf(
        // 1. T2: одна поездка тремя участками — A → B → Z и прямой A → Z (3 ч)
        ride("T2", "A", "B", "08:00", "09:00"),
        ride("T2", "B", "Z", "09:05", "11:00"),
        ride("T2", "A", "Z", "08:00", "11:00"),
        // 2. T1: прямой рейс (4 ч)
        ride("T1", "A", "Z", "10:00", "14:00"),
        // 3. Пересадка в C через 1 ч (9 ч)
        ride("C1", "A", "C", "06:00", "07:00"),
        ride("C2", "C", "Z", "08:00", "15:00"),
        // 4. Пересадка в F через 1 ч (10 ч) — четвёртый вариант, отсекается лимитом в 3
        ride("F1", "A", "F", "05:00", "06:00"),
        ride("F2", "F", "Z", "07:00", "15:00"),
        // ✗ Пересадка 20 минут в E (была бы 1-й: 2 ч 30)
        ride("S1", "A", "E", "07:00", "08:00"),
        ride("S2", "E", "Z", "08:20", "09:30"),
        // ✗ Ожидание 8 ч 20 мин (вытеснила бы 9-часовой вариант: 8 ч 50)
        ride("L1", "A", "D", "05:00", "05:10"),
        ride("L2", "D", "Z", "13:30", "13:50"),
        // ✗ Отправление накануне, 24.10 (был бы 1-м: 1 ч 30)
        Ride("A", "Z", time(24, "23:00"), time(25, "00:30"), "N1|2026-10-24", "N1", "Рейс N1", "train"),
    )

    private fun findRoutes() = RouteFinder().find(rides, setOf("A"), setOf("Z"), date)

    private fun leg(trip: String, from: String, to: String, dep: String, arr: String) =
        Leg(from, to, time(25, dep), time(25, arr), trip, "Рейс $trip", "train")

    @Test
    fun allRoutesDepartOnSelectedDate() {
        val routes = findRoutes()
        assertTrue(routes.isNotEmpty())
        routes.forEach { assertEquals(date, it.departure.toLocalDate()) }
    }

    @Test
    fun atMostThreeRoutesSortedByDuration() {
        val routes = findRoutes()
        assertTrue(routes.isNotEmpty())
        assertTrue(routes.size <= 3)
        routes.zipWithNext().forEach { (current, next) ->
            assertTrue(current.duration <= next.duration)
        }
    }

    @Test
    fun ridesOfSameTripAreMergedIntoOneLeg() {
        val routes = findRoutes()
        assertTrue(routes.isNotEmpty())
        routes.forEach { route ->
            route.legs.zipWithNext().forEach { (current, next) ->
                assertNotEquals(current.number to current.title, next.number to next.title)
            }
        }
    }

    @Test
    fun transferWaitIsBetween30MinutesAnd8Hours() {
        val routes = findRoutes()
        assertTrue(routes.isNotEmpty())
        routes.forEach { route ->
            route.legs.zipWithNext().forEach { (current, next) ->
                val wait = Duration.between(current.arrival, next.departure)
                assertTrue(wait >= Duration.ofMinutes(30) && wait <= Duration.ofHours(8))
            }
        }
    }

    @Test
    fun noDuplicateRoutes() {
        val routes = findRoutes()
        assertTrue(routes.isNotEmpty())
        assertEquals(routes.size, routes.distinct().size)
    }

    @Test
    fun returnsExpectedFastestRoutes() {
        val expected = listOf(
            Route(listOf(leg("T2", "A", "Z", "08:00", "11:00"))),
            Route(listOf(leg("T1", "A", "Z", "10:00", "14:00"))),
            Route(listOf(leg("C1", "A", "C", "06:00", "07:00"), leg("C2", "C", "Z", "08:00", "15:00"))),
        )
        assertEquals(expected, findRoutes())
    }
}
