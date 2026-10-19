package ru.vladm.routefederal.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.vladm.routefederal.RouteFederalApp
import ru.vladm.routefederal.data.City
import ru.vladm.routefederal.data.CityRepository
import ru.vladm.routefederal.data.Route
import ru.vladm.routefederal.data.ScheduleRepository
import ru.vladm.routefederal.data.StationRepository
import ru.vladm.routefederal.search.RouteFinder
import ru.vladm.routefederal.ui.common.dateFormatter
import java.time.LocalDate

class MainViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val cityRepository: CityRepository = CityRepository(),
    private val stationRepository: StationRepository = StationRepository(),
    private val routeFinder: RouteFinder = RouteFinder(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState(
        cities = cityRepository.cities,
        stationNames = stationRepository.stations.associate { it.id to it.name },
    ))
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
        // Для поиска на дату D нужны участки за D и D+1 (продолжения поездок после полуночи)
        val date = uiState.value.date
        val nextDate = date.plusDays(1)
        if (!scheduleRepository.isLoaded(date) || !scheduleRepository.isLoaded(nextDate)) {
            val dates = "${date.format(dateFormatter)} и ${nextDate.format(dateFormatter)}"
            _uiState.update { it.copy(errorMessage = "Загрузите расписание на $dates на вкладке «Данные»") }
            return
        }

        val fromStationIds = stationIdsOf(uiState.value.departureCity!!)
        val toStationIds = stationIdsOf(uiState.value.arrivalCity!!)
        viewModelScope.launch {
            val rides = scheduleRepository.loadRides(listOf(date, nextDate))
            // Поиск — вычисления, поэтому выполняется не в главном потоке
            val routes = withContext(Dispatchers.Default) {
                routeFinder.find(rides, fromStationIds, toStationIds, date)
            }
            _uiState.update {
                if (routes.isEmpty()) {
                    it.copy(errorMessage = "Маршруты на ${date.format(dateFormatter)} не найдены")
                } else {
                    it.copy(
                        routes = routes,
                        selectedRoute = routes.first(),
                        currentPage = PAGE_ROUTES,
                    )
                }
            }
        }
    }

    private fun stationIdsOf(city: City): Set<String> =
        stationRepository.stations.filter { it.cityId == city.id }.map { it.id }.toSet()

    fun onBackClick() {
        _uiState.update { it.copy(currentPage = PAGE_SELECTION) }
    }

    fun onRouteSelected(route: Route) {
        _uiState.update { it.copy(selectedRoute = route) }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        // ViewModel с параметрами создаётся через фабрику: зависимости берутся из AppContainer
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as RouteFederalApp).container
                MainViewModel(
                    scheduleRepository = container.scheduleRepository,
                    cityRepository = container.cityRepository,
                    stationRepository = container.stationRepository,
                )
            }
        }
    }
}
