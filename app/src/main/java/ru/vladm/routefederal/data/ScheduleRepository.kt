package ru.vladm.routefederal.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.vladm.routefederal.data.api.RaspApi
import ru.vladm.routefederal.data.api.Segment
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime

// Содержимое файла расписания за одну дату
@Serializable
data class ScheduleFile(
    val date: String,
    val requestCount: Int,
    val rides: List<Ride>,
)

// Источник истины для расписания: файлы по датам в папке dir (внутри filesDir).
// Вкладка «Данные» скачивает через него даты, главный экран читает из него участки.
class ScheduleRepository(
    private val dir: File,
    private val api: RaspApi,
    private val json: Json,
    stations: List<Station>,
) {
    private val stationsById = stations.associateBy { it.id }

    private val _loadedDates = MutableStateFlow(readLoadedDates())
    val loadedDates: StateFlow<List<LocalDate>> = _loadedDates.asStateFlow()

    fun isLoaded(date: LocalDate): Boolean = date in _loadedDates.value

    // Скачивает все участки за дату и сохраняет в файл. Возвращает число запросов к API.
    // onProgress(сделано, всего) вызывается после каждого направления связи.
    suspend fun download(
        date: LocalDate,
        apiKey: String,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Int {
        val jobs = Links.all.flatMap { link -> listOf(link, link.copy(from = link.to, to = link.from)) }
        val rides = linkedMapOf<String, Ride>()
        var requestCount = 0

        jobs.forEachIndexed { index, link ->
            var offset = 0
            do {
                val page = api.search(
                    apiKey = apiKey,
                    from = link.from,
                    to = link.to,
                    date = date.toString(),
                    transportTypes = link.transportType,
                    offset = offset,
                )
                requestCount++
                page.segments.mapNotNull { toRide(it, link) }.forEach { rides[it.id] = it }
                offset += page.segments.size
            } while (page.segments.isNotEmpty() && offset < page.pagination.total)
            onProgress(index + 1, jobs.size)
        }

        val file = ScheduleFile(date.toString(), requestCount, rides.values.sortedBy { it.departure })
        withContext(Dispatchers.IO) { writeFile(date, json.encodeToString(ScheduleFile.serializer(), file)) }
        _loadedDates.value = readLoadedDates()
        return requestCount
    }

    // Участки за несколько дат одним списком. Соседние дни могут пересекаться,
    // поэтому повторы одного и того же участка убираются по id.
    suspend fun loadRides(dates: List<LocalDate>): List<Ride> = withContext(Dispatchers.IO) {
        dates.flatMap { date ->
            val file = fileFor(date)
            if (!file.exists()) emptyList()
            else json.decodeFromString(ScheduleFile.serializer(), file.readText()).rides
        }.distinctBy { it.id }
    }

    // Превращает сегмент ответа API в участок или отбрасывает его, если он нам не подходит
    internal fun toRide(segment: Segment, link: Link): Ride? {
        if (segment.hasTransfers) return null
        val from = segment.from?.code?.let { stationsById[it] } ?: return null
        val to = segment.to?.code?.let { stationsById[it] } ?: return null
        val thread = segment.thread ?: return null
        if (thread.transportType != link.transportType) return null
        // Для пар городов оставляем только станции именно этих городов
        if (link.from.startsWith("c") && from.cityId != link.from) return null
        if (link.to.startsWith("c") && to.cityId != link.to) return null
        val departure = segment.departure?.let { OffsetDateTime.parse(it) } ?: return null
        val arrival = segment.arrival?.let { OffsetDateTime.parse(it) } ?: return null
        val startDate = segment.startDate ?: return null
        if (!arrival.isAfter(departure)) return null

        val serviceKey = "${thread.uid}|$startDate"
        return Ride(
            id = "$serviceKey|${from.id}|${to.id}",
            fromStationId = from.id,
            toStationId = to.id,
            departure = departure,
            arrival = arrival,
            serviceKey = serviceKey,
            number = thread.number,
            title = thread.title,
            transportType = thread.transportType,
        )
    }

    private fun fileFor(date: LocalDate) = File(dir, "$date.json")

    // Запись через временный файл: если приложение упадёт посередине, старый файл не испортится
    private fun writeFile(date: LocalDate, text: String) {
        dir.mkdirs()
        val tmp = File(dir, "$date.json.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(fileFor(date))) {
            tmp.delete()
            error("Не удалось сохранить расписание")
        }
    }

    private fun readLoadedDates(): List<LocalDate> =
        dir.listFiles { file -> file.name.endsWith(".json") }
            .orEmpty()
            .mapNotNull { runCatching { LocalDate.parse(it.nameWithoutExtension) }.getOrNull() }
            .sorted()
}
