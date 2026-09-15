package ru.milin.routefederal.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ru.milin.routefederal.routing.*
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.time.Instant
import java.time.OffsetDateTime

data class DataInfo(val timetable: Timetable, val sourceName: String, val sourceUrl: String)
data class MapRegion(val name: String, val center: MapPoint, val rings: List<List<MapPoint>>)
data class MapPoint(val longitude: Float, val latitude: Float)

class TimetableRepository(context: Context) {
    private val appContext = context.applicationContext
    private val importedFile = File(appContext.filesDir, "timetable.json")

    fun load(): DataInfo? {
        if (importedFile.exists()) {
            val data = parse(importedFile.readText())
            if (data.timetable.expiresAt?.let { !Instant.now().isBefore(it) } == true) {
                importedFile.delete()
                error("Срок кэша истёк. Импортируйте обновлённое расписание.")
            }
            return data
        }
        if (appContext.assets.list("")?.contains("timetable.json") != true) return null
        return appContext.assets.open("timetable.json").bufferedReader().use { parse(it.readText()) }
    }

    fun importFile(input: InputStream): DataInfo {
        val bytes = input.readBytesLimited(20 * 1024 * 1024)
        val text = bytes.toString(Charsets.UTF_8)
        val data = parse(text)
        require(data.timetable.expiresAt?.let { Instant.now().isBefore(it) } != false) {
            "Срок кэша этого файла истёк. Прежняя база сохранена."
        }
        val errors = TimetableValidator.validate(data.timetable)
        require(errors.isEmpty()) { errors.take(3).joinToString("\n") }
        require(data.timetable.trips.isNotEmpty()) { "В файле нет пригодных рейсов." }
        val temporary = File(appContext.filesDir, "timetable.new")
        try {
            temporary.outputStream().use { stream ->
                stream.write(bytes)
                stream.fd.sync()
            }
            check(temporary.renameTo(importedFile)) { "Не удалось сохранить расписание." }
        } finally {
            temporary.delete()
        }
        return data
    }

    fun loadMap(): List<List<MapPoint>> = readRings(JSONArray(
        appContext.assets.open("map_land.json").bufferedReader().use { it.readText() }))

    fun loadRegions(): List<MapRegion> {
        val array = JSONArray(appContext.assets.open("map_regions.json").bufferedReader().use { it.readText() })
        val result = mutableListOf<MapRegion>()
        for (i in 0 until array.length()) {
            val region = array.getJSONObject(i)
            result.add(MapRegion(region.getString("name"),
                MapPoint(region.getDouble("longitude").toFloat(), region.getDouble("latitude").toFloat()),
                readRings(region.getJSONArray("rings"))))
        }
        return result
    }

    private fun readRings(polygons: JSONArray): List<List<MapPoint>> {
        val result = mutableListOf<List<MapPoint>>()
        for (i in 0 until polygons.length()) {
            val points = polygons.getJSONArray(i)
            val ring = mutableListOf<MapPoint>()
            for (j in 0 until points.length()) {
                val point = points.getJSONArray(j)
                ring.add(MapPoint(point.getDouble(0).toFloat(), point.getDouble(1).toFloat()))
            }
            result.add(ring)
        }
        return result
    }

    private fun parse(text: String): DataInfo {
        val root = JSONObject(text)
        require(root.getInt("schemaVersion") == 1) { "Неподдерживаемая версия файла." }
        val source = root.getJSONObject("source")
        require(source.getString("name").isNotBlank() && source.getString("url").isNotBlank()) {
            "В файле не указан источник расписания."
        }
        val stations = mutableListOf<Station>()
        val stopsJson = root.getJSONArray("stops")
        for (i in 0 until stopsJson.length()) {
            val s = stopsJson.getJSONObject(i)
            require(!s.isNull("cityId") && !s.isNull("cityName") &&
                s.getString("cityId").isNotBlank() && s.getString("cityName").isNotBlank()) {
                "В файле нет сведений о городах. Подготовьте новую версию расписания."
            }
            stations.add(Station(
                s.getString("id"), s.getString("name"), s.getString("regionCode"),
                s.nullableDouble("latitude"), s.nullableDouble("longitude"),
                s.getString("timeZone"), s.nullableInt("minTransferMinutes"),
                if (s.isNull("cityId")) null else s.getString("cityId"),
                if (s.isNull("cityName")) null else s.getString("cityName")
            ))
        }
        val trips = mutableListOf<ScheduledTrip>()
        val tripsJson = root.getJSONArray("trips")
        for (i in 0 until tripsJson.length()) {
            val t = tripsJson.getJSONObject(i)
            val days = mutableSetOf<Int>()
            val daysJson = t.getJSONArray("daysOfWeek")
            for (j in 0 until daysJson.length()) days.add(daysJson.getInt(j))
            val calls = mutableListOf<StopCall>()
            val callsJson = t.getJSONArray("calls")
            for (j in 0 until callsJson.length()) {
                val c = callsJson.getJSONObject(j)
                calls.add(StopCall(c.getString("stationId"), c.nullableInt("arrivalMinutes"), c.nullableInt("departureMinutes")))
            }
            trips.add(ScheduledTrip(
                t.getString("id"), t.getString("routeId"), t.getString("routeName"),
                TransportType.valueOf(t.getString("transport")), days,
                t.getString("seasonStart"), t.getString("seasonEnd"), calls, t.getString("timeZone"),
                t.dates("includedDates"), t.dates("excludedDates")
            ))
        }
        val timetable = Timetable(stations, trips, LocalDate.parse(source.getString("snapshotDate")), source.getString("coverageNote"),
            LocalDate.parse(source.getString("validFrom")), LocalDate.parse(source.getString("validUntil")),
            if (source.isNull("expiresAt")) null else OffsetDateTime.parse(source.getString("expiresAt")).toInstant())
        val errors = TimetableValidator.validate(timetable)
        require(errors.isEmpty()) { errors.take(3).joinToString("\n") }
        return DataInfo(timetable, source.getString("name"), source.getString("url"))
    }
}

private fun JSONObject.nullableInt(name: String): Int? = if (isNull(name)) null else getInt(name)
private fun JSONObject.nullableDouble(name: String): Double? = if (isNull(name)) null else getDouble(name)
private fun JSONObject.dates(name: String): Set<LocalDate> {
    val values = optJSONArray(name) ?: return emptySet()
    val result = mutableSetOf<LocalDate>()
    for (i in 0 until values.length()) result.add(LocalDate.parse(values.getString(i)))
    return result
}

private fun InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var count = read(buffer)
    while (count != -1) {
        require(output.size() + count <= limit) { "Файл больше 20 МБ. Для большой базы потребуется другой формат хранения." }
        output.write(buffer, 0, count)
        count = read(buffer)
    }
    return output.toByteArray()
}
