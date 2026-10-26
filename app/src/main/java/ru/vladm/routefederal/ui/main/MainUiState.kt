package ru.vladm.routefederal.ui.main

import ru.vladm.routefederal.data.City
import ru.vladm.routefederal.data.Route
import java.time.LocalDate

const val PAGE_SELECTION = "Selection"
const val PAGE_ROUTES = "Routes"

data class MainUiState(
    val cities: List<City> = emptyList(),
    val departureCity: City? = null,
    val arrivalCity: City? = null,
    val date: LocalDate = LocalDate.now(),
    val currentPage: String = PAGE_SELECTION,
    val routes: List<Route> = emptyList(),
    val selectedRoute: Route? = null,
    val errorMessage: String? = null,
    val stationNames: Map<String, String> = emptyMap(),
    val stationCities: Map<String, City> = emptyMap(),
)
