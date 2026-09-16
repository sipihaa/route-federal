package ru.milin.routefederal.routing

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** Artificial cases are confined to tests; they are not passenger data. */
class RouteFinderTest {
    private val date = LocalDate.of(2026, 9, 11) // Friday
    private fun station(id: String, transfer: Int? = 10) = Station(id, id, "test", null, null, "Europe/Moscow", transfer)
    private fun trip(id: String, from: String, to: String, departure: Int, arrival: Int, route: String = id) = ScheduledTrip(
        id, route, route, TransportType.BUS, (1..7).toSet(), "01-01", "12-31",
        listOf(StopCall(from, null, departure), StopCall(to, arrival, null)), "Europe/Moscow"
    )
    private fun data(trips: List<ScheduledTrip>, stops: List<Station> = listOf(station("A"), station("B"), station("C"), station("D"))) =
        Timetable(stops, trips, date, "Test fixtures only")

    @Test fun firstDepartureMustMatchSelectedDate() {
        val first = LocalDate.of(2026, 9, 21)
        val later = first.plusDays(4)
        val timetable = data(listOf(
            trip("early", "A", "D", 600, 780).copy(daysOfWeek = emptySet(), includedDates = setOf(first)),
            trip("fast", "A", "D", 600, 660).copy(daysOfWeek = emptySet(), includedDates = setOf(later)),
            trip("outside", "A", "D", 600, 630).copy(daysOfWeek = emptySet(), includedDates = setOf(first.plusDays(8)))
        )).copy(validFrom = first, validUntil = first.plusDays(7))
        val finder = RouteFinder(timetable)
        val result = finder.search("A", "D", first, horizonDays = 8)
        assertEquals(listOf("early"), result.routes.map { it.legs.single().tripId })
        assertEquals(first, result.routes.first().departure.atZone(java.time.ZoneId.of("Europe/Moscow")).toLocalDate())
        assertEquals(180L, result.routes.first().durationMinutes)
        assertEquals("early", finder.search("A", "D", first, horizonDays = 1).routes.single().legs.single().tripId)
    }

    @Test fun selectedDepartureDateStillAllowsNextDayTransfer() {
        val timetable = data(listOf(
            trip("night", "A", "B", 1380, 1470).copy(daysOfWeek = emptySet(), includedDates = setOf(date)),
            trip("next", "B", "D", 90, 150).copy(daysOfWeek = emptySet(), includedDates = setOf(date.plusDays(1)))
        ))
        val route = RouteFinder(timetable).search("A", "D", date).routes.single()
        assertEquals(date, route.departure.atZone(java.time.ZoneId.of("Europe/Moscow")).toLocalDate())
        assertEquals(date.plusDays(1), route.arrival.atZone(java.time.ZoneId.of("Europe/Moscow")).toLocalDate())
        assertEquals(210L, route.durationMinutes)
    }

    @Test fun groupsEveryEndpointStationByCityButKeepsNearbyCitiesSeparate() {
        val stops = listOf(station("A").copy(cityId = "moscow", cityName = "Москва"),
            station("B").copy(cityId = "moscow", cityName = "Москва"),
            station("C").copy(cityId = "vladimir", cityName = "Владимир"),
            station("D").copy(cityId = "vladimir", cityName = "Владимир"),
            station("P").copy(cityId = "podolsk", cityName = "Подольск"))
        val timetable = data(listOf(trip("slow", "A", "C", 600, 780), trip("fast", "B", "D", 600, 660),
            trip("third", "A", "D", 600, 720), trip("nearby", "P", "D", 600, 630)), stops)
        assertEquals(3, timetable.cities().size)
        assertEquals(2, timetable.cities().first { it.id == "moscow" }.stations.size)
        val result = RouteFinder(timetable).searchCities("moscow", "vladimir", date)
        assertEquals(listOf("fast", "third", "slow"), result.routes.map { it.legs.single().tripId })
        assertEquals("nearby", RouteFinder(timetable).searchCities("podolsk", "vladimir", date).routes.single().legs.single().tripId)
    }

    @Test fun citySelectionDoesNotConnectTransferStations() {
        val stops = listOf(station("A").copy(cityId = "origin"), station("B").copy(cityId = "middle"),
            station("C").copy(cityId = "middle"), station("D").copy(cityId = "destination"))
        val timetable = data(listOf(trip("first", "A", "B", 600, 660), trip("second", "C", "D", 700, 800)), stops)
        assertTrue(RouteFinder(timetable).searchCities("origin", "destination", date).routes.isEmpty())
    }

    @Test fun ranksThreeDistinctAlternativesAndCountsWaiting() {
        val timetable = data(listOf(
            trip("slow", "A", "D", 480, 900),
            trip("ab", "A", "B", 600, 660), trip("bd", "B", "D", 690, 750),
            trip("ac", "A", "C", 720, 780), trip("cd", "C", "D", 800, 900),
            trip("medium", "A", "D", 900, 1100)
        ))
        val result = RouteFinder(timetable).search("A", "D", date)
        assertEquals(SearchStatus.COMPLETED_IN_WINDOW, result.status)
        assertEquals(listOf(150L, 180L, 200L), result.routes.map { it.durationMinutes })
        assertEquals(listOf("ab", "bd"), result.routes[0].legs.map { it.tripId })
        assertEquals(30L, result.routes[0].waitingMinutesBefore(1))
        assertEquals(1, result.routes[0].transferCount)
    }

    @Test fun laterDepartureWinsEvenWhenArrivalIsLater() {
        val result = RouteFinder(data(listOf(trip("early", "A", "D", 480, 600), trip("late", "A", "D", 900, 960)))).search("A", "D", date)
        assertEquals("late", result.routes.first().legs.single().tripId)
        assertEquals(60L, result.routes.first().durationMinutes)
    }

    @Test fun duplicateDeparturesDoNotOccupyAllAlternatives() {
        val result = RouteFinder(data(listOf(
            trip("x1", "A", "D", 480, 540, "x"), trip("x2", "A", "D", 600, 660, "x"),
            trip("x3", "A", "D", 720, 780, "x"), trip("y", "A", "D", 800, 900), trip("z", "A", "D", 900, 1010)
        ))).search("A", "D", date)
        assertEquals(listOf("x", "y", "z"), result.routes.map { it.legs.single().routeId })
        assertEquals("x1", result.routes.first().legs.single().tripId)
    }

    @Test fun appliesStationRuleAndConfigurableFallback() {
        val trips = listOf(trip("ab", "A", "B", 600, 660), trip("miss", "B", "D", 665, 700).copy(daysOfWeek = setOf(5)))
        assertTrue(RouteFinder(data(trips)).search("A", "D", date, horizonDays = 1).routes.isEmpty())
        val unknown = data(trips, listOf(station("A"), station("B", null), station("D")))
        val result = RouteFinder(unknown).search("A", "D", date)
        assertTrue(result.routes.isEmpty())
        assertTrue(result.limitations.any { it.contains("30 мин") })
        val shorter = RouteFinder(unknown).search("A", "D", date, horizonDays = 1, defaultTransferMinutes = 5)
        assertEquals(100L, shorter.routes.single().durationMinutes)
        assertEquals(5L, shorter.routes.single().waitingMinutesBefore(1))
        // An explicit station rule still takes precedence over the fallback.
        assertTrue(RouteFinder(data(trips)).search("A", "D", date, horizonDays = 1, defaultTransferMinutes = 0).routes.isEmpty())
    }

    @Test fun keepsOvernightArrivalButDoesNotBoardOutsideLoadedDates() {
        val limited = data(listOf(
            trip("overnight", "A", "B", 1430, 1500),
            trip("next", "B", "D", 1520, 1600).copy(calls = listOf(StopCall("B", null, 80), StopCall("D", 160, null)))
        )).copy(validFrom = date, validUntil = date)
        val arrival = RouteFinder(limited).search("A", "B", date)
        assertEquals(70L, arrival.routes.single().durationMinutes)
        assertTrue(arrival.routes.single().arrival >= arrival.windowEndExclusive!!)
        assertTrue(RouteFinder(limited).search("A", "D", date).routes.isEmpty())
    }

    @Test fun rejectsExpiredCache() {
        val expired = data(listOf(trip("one", "A", "D", 600, 660))).copy(expiresAt = java.time.Instant.EPOCH)
        assertEquals(SearchStatus.EXPIRED_DATA, RouteFinder(expired).search("A", "D", date).status)
    }

    @Test fun sameVehicleNeedsNoTransferRule() {
        val through = trip("through", "A", "D", 600, 800).copy(calls = listOf(
            StopCall("A", null, 600), StopCall("B", 660, 675), StopCall("D", 800, null)
        ))
        val result = RouteFinder(data(listOf(through), listOf(station("A"), station("B", null), station("D")))).search("A", "D", date)
        assertEquals(200L, result.routes.single().durationMinutes)
        assertEquals(0, result.routes.single().transferCount)
        assertEquals(listOf("A", "B", "D"), result.routes.single().legs.single().stopIds)
    }

    @Test fun doesNotTransferBetweenDifferentStationsWithSameName() {
        val stops = listOf(station("A"), station("B").copy(name = "Вокзал"), station("C").copy(name = "Вокзал"), station("D"))
        val result = RouteFinder(data(listOf(trip("ab", "A", "B", 600, 660), trip("cd", "C", "D", 690, 800)), stops)).search("A", "D", date)
        assertTrue(result.routes.isEmpty())
    }

    @Test fun excludesLocalRidesBetweenStationsOfSameCity() {
        val stops = listOf(station("A").copy(cityId = "city-a"), station("B").copy(cityId = "city-b"),
            station("C").copy(cityId = "city-b"), station("D").copy(cityId = "city-d"))
        val finder = RouteFinder(data(listOf(trip("ab", "A", "B", 600, 660),
            trip("bc", "B", "C", 690, 700), trip("cd", "C", "D", 730, 800)), stops))
        assertEquals(SearchStatus.INVALID_DATA, finder.search("B", "C", date).status)
        assertTrue(finder.search("A", "D", date).routes.isEmpty())
    }

    @Test fun boardsAfterMidnightOnServiceOfPreviousDay() {
        val overnight = trip("night", "A", "D", 1380, 1620).copy(daysOfWeek = setOf(4), calls = listOf(
            StopCall("A", null, 1380), StopCall("B", 1490, 1500), StopCall("D", 1620, null)
        ))
        val result = RouteFinder(data(listOf(overnight))).search("B", "D", date)
        assertEquals(120L, result.routes.single().durationMinutes)
        assertEquals(date.minusDays(1), result.routes.single().legs.single().serviceDate)
    }

    @Test fun appliesCalendarExceptionsAndWinterSeasonAcrossYear() {
        val excluded = trip("excluded", "A", "D", 600, 660).copy(excludedDates = setOf(date))
        val extra = trip("extra", "A", "D", 700, 780).copy(daysOfWeek = setOf(1), includedDates = setOf(date))
        val winter = trip("winter", "A", "D", 800, 850).copy(seasonStart = "10-01", seasonEnd = "05-31")
        val finder = RouteFinder(data(listOf(excluded, extra, winter)))
        assertEquals(listOf("extra"), finder.search("A", "D", date, horizonDays = 1).routes.map { it.legs.single().tripId })
        assertTrue(finder.search("A", "D", LocalDate.of(2026, 12, 4)).routes.any { it.legs.single().tripId == "winter" })
    }

    @Test fun canPassAnEarlierIntermediateStationWithoutAlightingThere() {
        val first = trip("ab", "A", "B", 600, 680).copy(calls = listOf(
            StopCall("A", null, 600), StopCall("C", null, 640), StopCall("B", 680, null)
        ))
        val second = trip("bd", "B", "D", 700, 760).copy(calls = listOf(
            StopCall("B", null, 700), StopCall("C", 720, null), StopCall("D", 760, null)
        ))
        val result = RouteFinder(data(listOf(first, second))).search("A", "D", date)
        assertEquals(listOf("ab", "bd"), result.routes.first().legs.map { it.tripId })
    }

    @Test fun reportsLimitsCancellationAndInvalidInput() {
        val finder = RouteFinder(data(listOf(trip("one", "A", "D", 600, 660), trip("two", "A", "D", 700, 800))))
        val twoBranches = data(listOf(trip("ab", "A", "B", 600, 660), trip("ac", "A", "C", 620, 700)))
        assertEquals(SearchStatus.RESOURCE_LIMIT, RouteFinder(twoBranches).search("A", "D", date, maxStates = 1).status)
        assertEquals(SearchStatus.CANCELLED, finder.search("A", "D", date, isCancelled = { true }).status)
        assertEquals(SearchStatus.INVALID_DATA, finder.search("A", "A", date).status)
        assertEquals(SearchStatus.INVALID_DATA, finder.search("X", "D", date).status)
        assertEquals(SearchStatus.INVALID_DATA, finder.search("A", "D", date, horizonDays = 0).status)
    }

    @Test fun rejectsBrokenChronologyAndDuplicateIdentifiers() {
        val broken = trip("bad", "A", "D", 700, 600)
        assertTrue(TimetableValidator.validate(data(listOf(broken))).isNotEmpty())
        assertTrue(TimetableValidator.validate(data(listOf(broken, broken))).any { it.contains("идентификаторы рейсов") })
    }

    @Test fun distinguishesDateOutsideSnapshotFromNoRoute() {
        val limited = data(listOf(trip("one", "A", "D", 600, 660))).copy(validFrom = date, validUntil = date)
        val finder = RouteFinder(limited)
        assertEquals(SearchStatus.DATE_OUT_OF_RANGE, finder.search("A", "D", date.plusDays(1)).status)
        val result = finder.search("A", "D", date)
        assertEquals(SearchStatus.COMPLETED_IN_WINDOW, result.status)
        assertEquals(date.plusDays(1).atStartOfDay(java.time.ZoneId.of("Europe/Moscow")).toInstant(), result.windowEndExclusive)
    }

    @Test fun comparesAbsoluteTimeAcrossStationTimeZones() {
        val stops = listOf(station("A"), station("D").copy(timeZone = "Asia/Yekaterinburg"))
        // Call minutes are in the trip reference zone (Moscow), not each stop's wall clock.
        val result = RouteFinder(data(listOf(trip("east", "A", "D", 600, 900)), stops)).search("A", "D", date)
        assertEquals(300L, result.routes.single().durationMinutes)
        val localArrival = result.routes.single().arrival.atZone(java.time.ZoneId.of("Asia/Yekaterinburg"))
        assertEquals(17, localArrival.hour)
    }
}
