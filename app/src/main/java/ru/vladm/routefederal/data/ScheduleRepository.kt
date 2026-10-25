package ru.vladm.routefederal.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.vladm.routefederal.data.api.RaspApi
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime

// Расписание: один JSON-файл со списком участков на каждую дату (папка dir внутри filesDir).
// Вкладка «Данные» скачивает через него даты, главный экран читает участки для поиска.
class ScheduleRepository(
    private val dir: File,
    private val api: RaspApi,
    private val apiKey: String,
    private val json: Json,
    stations: List<Station>,
) {
    private val stationIds = stations.map { it.id }.toSet()

    private val _loadedDates = MutableStateFlow(readLoadedDates())
    val loadedDates: StateFlow<List<LocalDate>> = _loadedDates.asStateFlow()

    // Скачивает участки за дату по всем связям (в обе стороны) и сохраняет в файл.
    // onProgress(сделано, всего) вызывается после каждого направления.
    suspend fun download(date: LocalDate, onProgress: (Int, Int) -> Unit) {
        val directions = Links.all.flatMap { listOf(it, Link(it.to, it.from, it.transportType)) }
        val rides = mutableListOf<Ride>()

        directions.forEachIndexed { index, link ->
            // API отдаёт не больше 100 рейсов за запрос, остальные — следующими страницами
            var offset = 0
            do {
                val page = api.search(apiKey, link.from, link.to, date.toString(), link.transportType, offset)
                for (segment in page.segments) {
                    // Для автобусов (пары городов) приходят и станции не из нашего набора
                    if (segment.from.code !in stationIds || segment.to.code !in stationIds) continue
                    rides.add(
                        Ride(
                            fromStationId = segment.from.code,
                            toStationId = segment.to.code,
                            departure = OffsetDateTime.parse(segment.departure),
                            arrival = OffsetDateTime.parse(segment.arrival),
                            serviceKey = "${segment.thread.uid}|${segment.startDate}",
                            number = segment.thread.number,
                            title = segment.thread.title,
                            transportType = segment.thread.transportType,
                        )
                    )
                }
                offset += page.segments.size
            } while (offset < page.pagination.total)
            onProgress(index + 1, directions.size)
        }

        withContext(Dispatchers.IO) {
            dir.mkdirs()
            fileFor(date).writeText(json.encodeToString(rides))
        }
        _loadedDates.value = readLoadedDates()
    }

    fun isLoaded(date: LocalDate): Boolean = date in _loadedDates.value

    // Участки за несколько дат одним списком. Один участок может оказаться в файлах
    // соседних дней (отправления после полуночи), поэтому повторы убираются.
    suspend fun loadRides(dates: List<LocalDate>): List<Ride> = withContext(Dispatchers.IO) {
        dates.flatMap { json.decodeFromString<List<Ride>>(fileFor(it).readText()) }.distinct()
    }

    private fun fileFor(date: LocalDate) = File(dir, "$date.json")

    private fun readLoadedDates(): List<LocalDate> =
        dir.listFiles().orEmpty().map { LocalDate.parse(it.nameWithoutExtension) }.sorted()
}
