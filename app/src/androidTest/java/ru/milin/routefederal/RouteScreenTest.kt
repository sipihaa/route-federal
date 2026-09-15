package ru.milin.routefederal

import android.content.Context
import android.view.View
import android.widget.DatePicker
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.hamcrest.Matcher
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.io.File

/** Artificial data exists only in the test APK; the original local data is restored. */
class RouteScreenTest {
    private var original: ByteArray? = null
    private lateinit var localData: File

    @get:Rule(order = 0)
    val fixture = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            localData = File(context.filesDir, "timetable.json")
            original = if (localData.exists()) localData.readBytes() else null
            val testContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
            testContext.assets.open("ui_timetable.json").use { localData.writeBytes(it.readBytes()) }
        }

        override fun after() {
            val bytes = original
            if (bytes == null) localData.delete() else localData.writeBytes(bytes)
        }
    }

    @get:Rule(order = 1)
    val screen = createAndroidComposeRule<MainActivity>()

    @Test fun choosesDateAndStationsShowsTopThreeAndClearsOldResult() {
        screen.waitUntil(15_000) {
            screen.onAllNodes(hasTestTag("choose_from") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        chooseStation("choose_from", "A")
        chooseStation("choose_to", "D")
        screen.onNodeWithTag("choose_date").performScrollTo().performClick()
        onView(isAssignableFrom(DatePicker::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(DatePicker::class.java)
            override fun getDescription() = "Select 11 September 2026"
            override fun perform(uiController: UiController, view: View) {
                (view as DatePicker).updateDate(2026, 8, 11)
                uiController.loopMainThreadUntilIdle()
            }
        })
        onView(withId(android.R.id.button1)).perform(click())
        screen.onNodeWithTag("choose_date").assertTextEquals("11.09.2026")
        screen.onNodeWithTag("search").performScrollTo().performClick()
        screen.waitUntil(15_000) { screen.onAllNodesWithTag("search").fetchSemanticsNodes().isNotEmpty() }
        screen.onNodeWithTag("main_content").performScrollToNode(hasTestTag("result_title"))
        screen.onNodeWithTag("result_title").performScrollTo().assertTextEquals("Найдено вариантов: 3")
        screen.onNodeWithTag("route_0").performScrollTo().assertTextContains("2 ч 30 мин · пересадок: 1", substring = true)
        screen.onNodeWithTag("route_0").assertTextContains("Отправление: 15.09", substring = true)
        screen.onNodeWithTag("route_1").performScrollTo().assertTextContains("3 ч 0 мин · пересадок: 1", substring = true)
        screen.onNodeWithTag("route_2").performScrollTo().assertTextContains("3 ч 20 мин · пересадок: 0", substring = true)
        screen.onNodeWithTag("main_content").performScrollToNode(hasText("Ожидание пересадки: 30 мин"))
        screen.onNodeWithText("Ожидание пересадки: 30 мин").assertIsDisplayed()
        screen.onNodeWithTag("main_content").performScrollToNode(hasTestTag("swap"))
        screen.onNodeWithTag("swap").performClick()
        screen.runOnIdle {
            val model = androidx.lifecycle.ViewModelProvider(screen.activity)[ru.milin.routefederal.ui.RouteViewModel::class.java]
            org.junit.Assert.assertNull(model.state.value.result)
        }
        screen.onNodeWithTag("result_title").assertDoesNotExist()
        screen.onNodeWithTag("choose_from").assertTextContains("D", substring = true)
        screen.onNodeWithTag("choose_to").assertTextContains("A", substring = true)
    }

    private fun chooseStation(button: String, stationId: String) {
        screen.onNodeWithTag(button).performScrollTo().performClick()
        screen.onNodeWithTag("city_$stationId").performClick()
    }
}
