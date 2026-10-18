package ru.vladm.routefederal.ui.main

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.vladm.routefederal.data.City
import ru.vladm.routefederal.data.CityRepository
import ru.vladm.routefederal.data.Route
import java.time.LocalDate

class MainViewModel(
    private val cityRepository: CityRepository = CityRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState(cities=cityRepository.cities))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun onDepartureCitySelected(city: City) {
        _uiState.update { it.copy(
            departureCity = city,
            errorMessage = null
        ) }
    }

    fun onArrivalCitySelected(city: City) {
        _uiState.update { it.copy(
            arrivalCity = city,
            errorMessage = null
        ) }
    }

    fun onDateSelected(date: LocalDate) {
        _uiState.update { it.copy(
            date = date,
            errorMessage = null
        ) }
    }

    fun onSwapClick() {
        _uiState.update { it.copy(
            departureCity = it.arrivalCity,
            arrivalCity = it.departureCity
        ) }
    }

    fun onFindRouteClick() {
        if (uiState.value.departureCity == null) {
            _uiState.update { it.copy(errorMessage = "Выберите город отправления") }
            return
        }
        if (uiState.value.arrivalCity == null) {
            _uiState.update { it.copy(errorMessage = "Выберите город прибытия") }
            return
        }
        if (uiState.value.departureCity == uiState.value.arrivalCity) {
            _uiState.update { it.copy(errorMessage = "Города отправления и прибытия не должны совпадать") }
            return
        }
        // TODO: Проверка на то, скачано ли расписание на нужные даты (D-1, D, D+1)
        // if () {}

        // TODO: Вызов метода поиска маршрута
        _uiState.update { it.copy(currentPage = PAGE_ROUTES) }
    }

    fun onBackClick() {
        _uiState.update { it.copy(currentPage = PAGE_SELECTION) }
    }

    fun onRouteSelected(route: Route) {
        _uiState.update { it.copy(selectedRoute = route) }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
