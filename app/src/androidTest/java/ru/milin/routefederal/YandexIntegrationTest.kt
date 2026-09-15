package ru.milin.routefederal

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.json.JSONArray
import org.json.JSONObject
import ru.milin.routefederal.data.TimetableRepository
import ru.milin.routefederal.routing.*
import java.io.File
import java.time.LocalDate

/** Requires the temporary, locally downloaded sample; this data is never put in the main APK. */
class YandexIntegrationTest {
    private var previous: ByteArray? = null
    private lateinit var file: File
    private var importMillis = 0L

    @get:Rule(order = 0)
    val fixture = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val testContext = InstrumentationRegistry.getInstrumentation().context
            assumeTrue("Download and prepare the real snapshot first", testContext.assets.list("")!!.contains("yandex_timetable.json"))
            file = File(context.filesDir, "timetable.json")
            previous = if (file.exists()) file.readBytes() else null
            val started = System.nanoTime()
            testContext.assets.open("yandex_timetable.json").use { TimetableRepository(context).importFile(it) }
            importMillis = (System.nanoTime() - started) / 1_000_000
        }
        override fun after() {
            if (!::file.isInitialized) return
            val bytes = previous
            if (bytes == null) file.delete() else file.writeBytes(bytes)
        }
    }
    @get:Rule(order = 1)
    val screen = createAndroidComposeRule<MainActivity>()

    @Test fun realNetworkImportsSearchesAndShowsAnOfflineRoute() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val started = System.nanoTime()
        val timetable = TimetableRepository(context).load()!!.timetable
        val loadMillis = (System.nanoTime() - started) / 1_000_000
        assertEquals(18, timetable.stations.size)
        assertEquals(1787, timetable.trips.size)
        assertEquals(setOf(TransportType.BUS, TransportType.TRAIN, TransportType.SUBURBAN), timetable.trips.map { it.transport }.toSet())
        val measurements = JSONArray()
        val pairs = listOf(
            "s2000001" to "s2060340", "s9879173" to "s9612089", "s2000001" to "s9612089",
            "s9601539" to "s9612089", "s9612144" to "s9612089", "s9623080" to "s9623084"
        )
        for ((from, to) in pairs) {
            for (day in 21..28) {
                val searchStart = System.nanoTime()
                val result = RouteFinder(timetable).search(from, to, LocalDate.of(2026, 9, day), horizonDays = 8)
                val millis = (System.nanoTime() - searchStart) / 1_000_000
                assertEquals("$from -> $to on $day", SearchStatus.COMPLETED_IN_WINDOW, result.status)
                assertTrue("No route: $from -> $to on $day", result.routes.isNotEmpty())
                assertEquals(result.routes.size, result.routes.map { it.signature() }.toSet().size)
                for (journey in result.routes) {
                    for (i in 1 until journey.legs.size) {
                        assertEquals(journey.legs[i - 1].toStationId, journey.legs[i].fromStationId)
                        assertTrue(journey.waitingMinutesBefore(i) >= 30)
                    }
                }
                measurements.put(JSONObject().put("from", from).put("to", to).put("day", day).put("millis", millis)
                    .put("states", result.examinedStates).put("durationsMinutes", JSONArray(result.routes.map { it.durationMinutes }))
                    .put("transfers", JSONArray(result.routes.map { it.transferCount })))
            }
        }
        val cities = timetable.cities()
        assertEquals(6, cities.size)
        assertEquals(5, cities.first { it.id == "c213" }.stations.size)
        for (day in 21..28) {
            val started = System.nanoTime()
            val result = RouteFinder(timetable).searchCities("c213", "c23243", LocalDate.of(2026, 9, day))
            assertEquals(SearchStatus.COMPLETED_IN_WINDOW, result.status)
            assertEquals(3, result.routes.size)
            assertTrue(result.routes.all { route -> route.legs.first().fromStationId in cities.first { it.id == "c213" }.stations.map { it.id } })
            measurements.put(JSONObject().put("from", "c213").put("to", "c23243").put("date", "2026-09-$day")
                .put("millis", (System.nanoTime() - started) / 1_000_000).put("states", result.examinedStates)
                .put("durationsMinutes", JSONArray(result.routes.map { it.durationMinutes })))
        }
        assertTrue(TimetableRepository(context).loadRegions().any { it.name.contains("Владимир") })
        val audit = JSONObject().put("importMillis", importMillis).put("loadMillis", loadMillis).put("searches", measurements)
        File(context.filesDir, "yandex-android-validation.json").writeText(audit.toString(2))
        screen.waitUntil(15_000) { screen.onAllNodes(hasTestTag("choose_from") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        choose("choose_from", "c213", "Москва")
        choose("choose_to", "c192", "Владимир")
        screen.onNodeWithTag("choose_date").assertTextEquals("21.09.2026")
        screen.onNodeWithTag("search").performScrollTo().performClick()
        screen.waitUntil(15_000) { screen.onAllNodesWithTag("search").fetchSemanticsNodes().isNotEmpty() }
        screen.onNodeWithTag("main_content").performScrollToNode(hasTestTag("result_title"))
        screen.onNodeWithTag("result_title").assertTextEquals("Найдено вариантов: 3")
        screen.onNodeWithTag("route_0").performScrollTo().assertIsDisplayed()
    }

    private fun choose(button: String, id: String, query: String) {
        screen.onNodeWithTag(button).performScrollTo().performClick()
        screen.onNodeWithTag("city_query").performTextInput(query)
        screen.onNodeWithTag("city_$id").performScrollTo().performClick()
    }
}
