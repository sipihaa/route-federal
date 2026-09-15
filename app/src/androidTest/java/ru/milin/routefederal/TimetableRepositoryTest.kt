package ru.milin.routefederal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import ru.milin.routefederal.data.TimetableRepository
import ru.milin.routefederal.routing.RouteFinder
import ru.milin.routefederal.routing.SearchStatus
import java.io.File
import java.time.LocalDate

class TimetableRepositoryTest {
    @Test fun readsRealSnapshotAndKeepsItWhenImportIsBroken() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.filesDir, "timetable.json")
        val previous = if (file.exists()) file.readBytes() else null
        try {
            val repository = TimetableRepository(context)
            val testContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
            val source = testContext.assets.open("rostov_timetable.json").use { it.readBytes() }
            repository.importFile(source.inputStream())
            val timetable = repository.load()!!.timetable
            val result = RouteFinder(timetable).search("skppk:9612913", "skppk:9612952", LocalDate.of(2026, 9, 12))
            assertEquals(SearchStatus.COMPLETED_IN_WINDOW, result.status)
            assertEquals(193L, result.routes.single().durationMinutes)
            assertEquals(0, result.routes.single().transferCount)
            val before = file.readBytes()
            try {
                repository.importFile("{broken".byteInputStream())
                fail("Broken JSON must be rejected")
            } catch (_: org.json.JSONException) {
                // Expected parse failure; the working file must survive it.
            }
            assertArrayEquals(before, file.readBytes())
            assertEquals(timetable, repository.load()!!.timetable)
            val expired = org.json.JSONObject(source.toString(Charsets.UTF_8))
            expired.getJSONObject("source").put("expiresAt", "2000-01-01T03:00:00+03:00")
            try {
                repository.importFile(expired.toString().byteInputStream())
                fail("An expired import must be rejected")
            } catch (_: IllegalArgumentException) {
                assertArrayEquals(before, file.readBytes())
            }
            file.writeText(expired.toString())
            try {
                repository.load()
                fail("An expired local cache must be removed")
            } catch (_: IllegalStateException) {
                assertFalse(file.exists())
            }
        } finally {
            if (previous == null) file.delete() else file.writeBytes(previous)
        }
    }
}
