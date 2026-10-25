package ru.vladm.routefederal.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.vladm.routefederal.data.City
import ru.vladm.routefederal.data.Leg
import ru.vladm.routefederal.data.Route
import ru.vladm.routefederal.ui.common.DateField
import ru.vladm.routefederal.ui.common.shortDateFormatter
import ru.vladm.routefederal.ui.common.timeFormatter
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime

// Связывает экран с ViewModel: берёт состояние и передаёт события
@Composable
fun MainRoute(viewModel: MainViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    MainScreen(
        state = state,
        onDepartureCitySelected = viewModel::onDepartureCitySelected,
        onArrivalCitySelected = viewModel::onArrivalCitySelected,
        onDateSelected = viewModel::onDateSelected,
        onSwapClick = viewModel::onSwapClick,
        onFindRouteClick = viewModel::onFindRouteClick,
        onBackClick = viewModel::onBackClick,
        onRouteSelected = viewModel::onRouteSelected,
        onErrorShown = viewModel::onErrorShown,
    )
}

// Сам экран ничего не хранит: всё получает параметрами и сообщает о действиях колбэками
@Composable
fun MainScreen(
    state: MainUiState,
    onDepartureCitySelected: (City) -> Unit,
    onArrivalCitySelected: (City) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onSwapClick: () -> Unit,
    onFindRouteClick: () -> Unit,
    onBackClick: () -> Unit,
    onRouteSelected: (Route) -> Unit,
    onErrorShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // Показываем ошибку, когда она появилась в состоянии. Если ошибка сменилась
    // или стала null раньше, эффект перезапускается и текущий Snackbar закрывается.
    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onErrorShown()
    }

    // Системная кнопка «назад» на странице маршрутов возвращает к выбору
    BackHandler(enabled = state.currentPage == PAGE_ROUTES) { onBackClick() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CityMap(
                cities = state.cities,
                departureCity = state.departureCity,
                arrivalCity = state.arrivalCity,
                routeCities = routeCities(state),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(2f)
                    .padding(16.dp)
            ) {
                when (state.currentPage) {
                    PAGE_ROUTES -> RoutesPage(
                        routes = state.routes,
                        selectedRoute = state.selectedRoute,
                        stationNames = state.stationNames,
                        onBackClick = onBackClick,
                        onRouteSelected = onRouteSelected,
                    )
                    else -> SelectionPage(
                        state = state,
                        onDepartureCitySelected = onDepartureCitySelected,
                        onArrivalCitySelected = onArrivalCitySelected,
                        onDateSelected = onDateSelected,
                        onSwapClick = onSwapClick,
                        onFindRouteClick = onFindRouteClick,
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// Города выбранного маршрута для карты: начало первого проезда и конец каждого проезда.
// Маршрут рисуется только на странице результатов.
private fun routeCities(state: MainUiState): List<City> {
    val route = state.selectedRoute
    if (state.currentPage != PAGE_ROUTES || route == null) return emptyList()
    val stationIds = listOf(route.legs.first().fromStationId) + route.legs.map { it.toStationId }
    return stationIds.mapNotNull { state.stationCities[it] }
}

@Composable
private fun SelectionPage(
    state: MainUiState,
    onDepartureCitySelected: (City) -> Unit,
    onArrivalCitySelected: (City) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onSwapClick: () -> Unit,
    onFindRouteClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CityDropdown(
            label = "Откуда",
            cities = state.cities,
            selected = state.departureCity,
            onSelected = onDepartureCitySelected,
        )
        TextButton(onClick = onSwapClick) {
            Text("⇅ Поменять местами")
        }
        CityDropdown(
            label = "Куда",
            cities = state.cities,
            selected = state.arrivalCity,
            onSelected = onArrivalCitySelected,
        )
        DateField(date = state.date, onDateSelected = onDateSelected)
        Button(
            onClick = onFindRouteClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Найти маршрут")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CityDropdown(
    label: String,
    cities: List<City>,
    selected: City?,
    onSelected: (City) -> Unit,
) {
    // Открыт ли список — чисто визуальная деталь, ViewModel о ней знать не нужно
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            cities.forEach { city ->
                DropdownMenuItem(
                    text = { Text(city.name) },
                    onClick = {
                        onSelected(city)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RoutesPage(
    routes: List<Route>,
    selectedRoute: Route?,
    stationNames: Map<String, String>,
    onBackClick: () -> Unit,
    onRouteSelected: (Route) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBackClick) {
            Text("← Назад")
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(routes) { index, route ->
                RouteCard(
                    index = index,
                    route = route,
                    expanded = route == selectedRoute,
                    stationNames = stationNames,
                    onClick = { onRouteSelected(route) },
                )
            }
        }
        // Обязательное условие API: указать источник данных
        Text(
            text = "Данные: Яндекс Расписания",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RouteCard(
    index: Int,
    route: Route,
    expanded: Boolean,
    stationNames: Map<String, String>,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (expanded) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${index + 1}. ${formatTime(route.departure)} → ${formatTime(route.arrival)}" +
                        dayShift(route),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(formatDuration(route.duration), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = if (route.transfers == 0) "Без пересадок" else "Пересадок: ${route.transfers}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (expanded) {
                route.legs.forEachIndexed { i, leg ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    LegDetails(leg, stationNames)
                    if (i < route.legs.lastIndex) {
                        val wait = Duration.between(leg.arrival, route.legs[i + 1].departure)
                        Text(
                            text = "Пересадка, ожидание ${formatDuration(wait)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegDetails(leg: Leg, stationNames: Map<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "${transportName(leg.transportType)} ${leg.number}".trim() + " · ${leg.title}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "${formatDateTime(leg.departure)}  ${stationNames[leg.fromStationId] ?: leg.fromStationId}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "${formatDateTime(leg.arrival)}  ${stationNames[leg.toStationId] ?: leg.toStationId}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun transportName(type: String) = when (type) {
    "train" -> "Поезд"
    "suburban" -> "Электричка"
    "bus" -> "Автобус"
    else -> type
}

private fun formatTime(time: OffsetDateTime): String = time.format(timeFormatter)

// В деталях указываем и дату: поездка может переходить через полночь
private fun formatDateTime(time: OffsetDateTime): String =
    "${time.format(timeFormatter)} (${time.format(shortDateFormatter)})"

// «+1», если прибытие на следующие сутки после отправления
private fun dayShift(route: Route): String {
    val days = route.arrival.toLocalDate().toEpochDay() - route.departure.toLocalDate().toEpochDay()
    return if (days > 0) " (+$days)" else ""
}

private fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    return if (hours > 0) "$hours ч $minutes мин" else "$minutes мин"
}
