package ru.milin.routefederal.routing

import java.time.DateTimeException
import java.time.MonthDay
import java.time.ZoneId

object TimetableValidator {
    fun validate(timetable: Timetable): List<String> {
        val errors = mutableListOf<String>()
        if ((timetable.validFrom == null) != (timetable.validUntil == null)) {
            errors.add("Период данных должен иметь обе границы.")
        }
        if (timetable.validFrom != null && timetable.validUntil != null && timetable.validUntil < timetable.validFrom) {
            errors.add("Конец периода данных раньше начала.")
        }
        val stations = timetable.stations.associateBy { it.id }
        if (stations.size != timetable.stations.size) errors.add("Повторяются идентификаторы станций.")
        if (timetable.trips.map { it.id }.toSet().size != timetable.trips.size) {
            errors.add("Повторяются идентификаторы рейсов.")
        }
        for (station in timetable.stations) {
            if (station.id.isBlank() || station.name.isBlank()) {
                errors.add("У станции отсутствует идентификатор или название.")
            }
            if (!validZone(station.timeZone)) errors.add("Неизвестный часовой пояс станции ${station.id}.")
            if (station.minTransferMinutes != null && station.minTransferMinutes < 0) {
                errors.add("Отрицательное время пересадки станции ${station.id}.")
            }
            if ((station.latitude == null) != (station.longitude == null)) {
                errors.add("Для станции ${station.id} требуется пара координат.")
            }
            if (station.latitude != null && (!station.latitude.isFinite() || station.latitude !in -90.0..90.0)) {
                errors.add("Некорректная широта станции ${station.id}.")
            }
            if (station.longitude != null && (!station.longitude.isFinite() || station.longitude !in -180.0..180.0)) {
                errors.add("Некорректная долгота станции ${station.id}.")
            }
        }
        for (trip in timetable.trips) {
            validateTrip(trip, stations, errors)
        }
        return errors
    }

    private fun validateTrip(trip: ScheduledTrip, stations: Map<String, Station>, errors: MutableList<String>) {
        val label = "Рейс ${trip.id}"
        if (trip.id.isBlank() || trip.routeId.isBlank() || trip.routeName.isBlank()) {
            errors.add("$label: отсутствует идентификатор или название маршрута.")
        }
        if (!validZone(trip.timeZone)) errors.add("$label: неизвестный часовой пояс.")
        if (trip.daysOfWeek.any { it !in 1..7 }) errors.add("$label: дни недели должны быть от 1 до 7.")
        if (!validMonthDay(trip.seasonStart) || !validMonthDay(trip.seasonEnd)) {
            errors.add("$label: сезон должен содержать даты в формате ММ-ДД.")
        }
        if (trip.includedDates.intersect(trip.excludedDates).isNotEmpty()) {
            errors.add("$label: одна дата одновременно добавлена и исключена.")
        }
        if (trip.calls.size < 2) {
            errors.add("$label: требуется не менее двух остановок.")
            return
        }
        val firstDeparture = trip.calls.first().departureMinutes
        if (firstDeparture == null || firstDeparture !in 0..1439) {
            errors.add("$label: первое отправление должно быть известно и принадлежать дню начала рейса.")
        }
        if (trip.calls.last().arrivalMinutes == null) errors.add("$label: неизвестно последнее прибытие.")
        var previousMinutes = -1
        for (call in trip.calls) {
            val station = stations[call.stationId]
            if (station == null) {
                errors.add("$label: неизвестная станция ${call.stationId}.")
            }
            if (call.arrivalMinutes == null && call.departureMinutes == null) {
                errors.add("$label: нет времени остановки ${call.stationId}.")
            }
            for (minutes in listOfNotNull(call.arrivalMinutes, call.departureMinutes)) {
                if (minutes < 0 || minutes < previousMinutes) {
                    errors.add("$label: нарушен порядок времени у ${call.stationId}; сутки нельзя угадывать.")
                }
                previousMinutes = minutes
            }
        }
    }

    private fun validZone(value: String): Boolean {
        return try {
            ZoneId.of(value)
            true
        } catch (_: DateTimeException) {
            false
        }
    }

    private fun validMonthDay(value: String): Boolean {
        if (!value.matches(Regex("\\d{2}-\\d{2}"))) return false
        return try {
            MonthDay.parse("--$value")
            true
        } catch (_: DateTimeException) {
            false
        }
    }
}
