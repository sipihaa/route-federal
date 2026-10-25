package ru.vladm.routefederal.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.vladm.routefederal.data.City
import ru.vladm.routefederal.data.StationRepository
import ru.vladm.routefederal.ui.theme.RouteFederalTheme
import java.time.LocalDate

// UI-тест главного экрана. MainScreen ничего не хранит сам: тест передаёт ему состояние
// и колбэки, а затем проверяет, что нарисовано и какие колбэки вызваны.
class MainScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val moscow = City("c213", "Москва", 55.7558, 37.6173)
    private val petushki = City("c10669", "Петушки", 55.9296, 39.4503)

    private val selectionState = MainUiState(
        cities = StationRepository().cities,
        departureCity = moscow,
        arrivalCity = petushki,
        date = LocalDate.of(2026, 10, 25),
        currentPage = PAGE_SELECTION,
    )

    // Счётчики вызовов колбэков
    private var findClicks = 0
    private var swapClicks = 0
    private var backClicks = 0

    private fun showScreen(state: () -> MainUiState) {
        composeRule.setContent {
            RouteFederalTheme {
                MainScreen(
                    state = state(),
                    onDepartureCitySelected = {},
                    onArrivalCitySelected = {},
                    onDateSelected = {},
                    onSwapClick = { swapClicks++ },
                    onFindRouteClick = { findClicks++ },
                    onBackClick = { backClicks++ },
                    onRouteSelected = {},
                    onErrorShown = {},
                )
            }
        }
    }

    @Test
    fun showsSelectedCitiesAndDate() {
        showScreen { selectionState }

        composeRule.onNodeWithText("Москва").assertIsDisplayed()
        composeRule.onNodeWithText("Петушки").assertIsDisplayed()
        composeRule.onNodeWithText("Дата: 25.10.2026").assertIsDisplayed()
    }

    @Test
    fun selectionPageButtonsCallTheirCallbacks() {
        showScreen { selectionState }

        composeRule.onNodeWithText("Найти маршрут").performClick()
        composeRule.onNodeWithText("⇅ Поменять местами").performClick()

        assertEquals(1, findClicks)
        assertEquals(1, swapClicks)
        assertEquals(0, backClicks)
    }

    @Test
    fun routesPageBackButtonCallsOnBackClick() {
        showScreen { selectionState.copy(currentPage = PAGE_ROUTES) }

        composeRule.onNodeWithText("← Назад").performClick()

        assertEquals(1, backClicks)
        assertEquals(0, findClicks)
    }

    @Test
    fun errorMessageIsShownReplacedAndHidden() {
        // Состояние хранится в тесте, чтобы менять его по ходу проверки
        var state by mutableStateOf(selectionState)
        showScreen { state }

        state = state.copy(errorMessage = "Выберите город прибытия")
        composeRule.onNodeWithText("Выберите город прибытия").assertIsDisplayed()

        state = state.copy(errorMessage = "Города отправления и прибытия не должны совпадать")
        composeRule.onNodeWithText("Города отправления и прибытия не должны совпадать").assertIsDisplayed()
        composeRule.onNodeWithText("Выберите город прибытия").assertDoesNotExist()

        state = state.copy(errorMessage = null)
        composeRule.onNodeWithText("Города отправления и прибытия не должны совпадать").assertDoesNotExist()
    }
}
