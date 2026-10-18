package ru.vladm.routefederal.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.vladm.routefederal.data.City
import ru.vladm.routefederal.data.Route
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Связывает экран с ViewModel: берёт состояние и передаёт события
@Composable
fun MainRoute(viewModel: MainViewModel = viewModel()) {
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
            MapPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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

@Composable
private fun MapPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text("Здесь будет карта")
    }
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

private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showDialog = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Дата: ${date.format(dateFormatter)}")
    }

    if (showDialog) {
        // DatePicker работает с миллисекундами в UTC, поэтому переводим LocalDate туда и обратно
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onDateSelected(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Отмена") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun RoutesPage(
    routes: List<Route>,
    onBackClick: () -> Unit,
    onRouteSelected: (Route) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBackClick) {
            Text("← Назад")
        }
        if (routes.isEmpty()) {
            Text("Маршруты не найдены (поиск пока не реализован)")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(routes) { index, route ->
                    // Заглушка карточки: содержимое появится вместе с моделью маршрута
                    Text(
                        text = "Маршрут ${index + 1}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRouteSelected(route) }
                            .padding(12.dp),
                    )
                }
            }
        }
    }
}
