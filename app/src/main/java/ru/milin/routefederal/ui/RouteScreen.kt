package ru.milin.routefederal.ui

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.milin.routefederal.routing.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RouteApp(model: RouteViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    var showData by rememberSaveable { mutableStateOf(false) }
    var cityPicker by remember { mutableStateOf<Boolean?>(null) }
    var mapSelection by remember { mutableStateOf<City?>(null) }
    var detail by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importData(uri)
    }
    val stations = state.data?.timetable?.stations.orEmpty()
    val cities = state.data?.timetable?.cities().orEmpty()
    val from = cities.find { it.id == state.fromId }
    val to = cities.find { it.id == state.toId }
    val journeys = state.result?.routes.orEmpty()
    LaunchedEffect(state.result) { detail = 0 }
    val shownJourney = journeys.getOrNull(detail)
    val selectedIds = shownJourney?.legs?.flatMap { it.stopIds } ?: (from?.stations.orEmpty() + to?.stations.orEmpty()).map { it.id }

    Scaffold { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets).testTag("main_content"),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Route Federal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Поезда, электрички и автобусы", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!showData, onClick = { showData = false }, label = { Text("Маршрут") })
                    FilterChip(showData, onClick = { showData = true }, label = { Text("Данные") })
                }
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("error_message")) } }
            if (showData) {
                item {
                    SectionCard {
                        Text("Расписание на устройстве", style = MaterialTheme.typography.titleLarge)
                        val data = state.data
                        if (data == null) Text("Расписание ещё не загружено. Можно импортировать проверенный файл данных.")
                        else {
                            Text("Источник: ${data.sourceName}")
                            Text("Снимок: ${data.timetable.snapshotDate}")
                            data.timetable.expiresAt?.let { Text("Кэш действителен до ${formatTime(it, "Europe/Moscow")}") }
                            Text("Период данных: ${data.timetable.validFrom} — ${data.timetable.validUntil}")
                            Text("Станций: ${stations.size} · расписаний: ${data.timetable.trips.size}")
                            Text(data.timetable.coverageNote)
                            TextButton(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(data.sourceUrl))) }) { Text("Открыть источник расписания") }
                            val noCoordinates = stations.count { it.latitude == null || it.longitude == null }
                            if (noCoordinates > 0) Text("Без координат: $noCoordinates. Эти пункты доступны в списке, но не показаны на карте.")
                        }
                        Button(onClick = { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !state.loading) { Text("Импортировать расписание") }
                        Text("Импорт заменяет локальную базу только после проверки файла. При ошибке прежние данные сохраняются.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    SectionCard {
                        Text("Область поиска", style = MaterialTheme.typography.titleMedium)
                        Text("Первое отправление — в выбранную дату или позже, в пределах окна поиска. Ожидания на пересадках входят во время в пути; ожидание до первой посадки не входит.")
                        Text("Ищем отправления в пределах периода данных:")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (days in listOf(1, 3, 8)) FilterChip(state.horizonDays == days,
                                onClick = { model.chooseHorizon(days) }, label = { Text("$days дней") })
                        }
                        Text("Запас пересадки при отсутствии правила станции:")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (minutes in listOf(15, 30, 45, 60)) FilterChip(state.transferMinutes == minutes,
                                onClick = { model.chooseTransferMinutes(minutes) }, label = { Text("$minutes мин") }, modifier = Modifier.testTag("transfer_$minutes"))
                        }
                        Text("Это параметр расчёта. Пересадки только на одной станции; переходы между вокзалами не строятся.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    Text("Карта: Natural Earth, public domain. Обзорная география, без уличной навигации. Линии показывают связи станций, а не точный путь транспорта.", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                item {
                    OfflineMap(state.map, state.regions, cities, selectedIds, onCityClick = { mapSelection = it }, showRoute = shownJourney != null,
                        modifier = Modifier.fillMaxWidth().height(320.dp))
                }
                item {
                    SectionCard {
                        OutlinedButton(onClick = { cityPicker = true }, enabled = stations.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("choose_from")) {
                            Text("Откуда: ${from?.name ?: "выберите город"}")
                        }
                        OutlinedButton(onClick = { cityPicker = false }, enabled = stations.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("choose_to")) {
                            Text("Куда: ${to?.name ?: "выберите город"}")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = model::swapCities, enabled = !state.loading, modifier = Modifier.testTag("swap")) { Text("Поменять местами") }
                            TextButton(onClick = {
                                DatePickerDialog(context, { _, year, month, day -> model.chooseDate(LocalDate.of(year, month + 1, day)) },
                                    state.date.year, state.date.monthValue - 1, state.date.dayOfMonth).show()
                            }, modifier = Modifier.testTag("choose_date")) { Text(state.date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))) }
                        }
                        Text("Отправления с ${state.date.format(DateTimeFormatter.ofPattern("dd.MM"))} по ${minOf(state.date.plusDays(state.horizonDays - 1L), state.data?.timetable?.validUntil ?: state.date.plusDays(state.horizonDays - 1L)).format(DateTimeFormatter.ofPattern("dd.MM"))}. Ожидание до первой посадки не учитывается", style = MaterialTheme.typography.bodySmall)
                        if (state.searching) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            OutlinedButton(onClick = model::cancelSearch, modifier = Modifier.fillMaxWidth()) { Text("Отменить поиск") }
                        } else {
                            Button(onClick = model::search, enabled = state.data != null && !state.loading,
                                modifier = Modifier.fillMaxWidth().testTag("search")) { Text("Найти маршруты") }
                        }
                    }
                }
                if (state.data == null && !state.loading) item {
                    Text("Карта доступна офлайн. Для расчёта требуется реальное расписание: откройте раздел «Данные».")
                }
                state.data?.let { data -> item {
                    TextButton(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(data.sourceUrl))) }) {
                        Text(if (data.sourceName == "Яндекс Расписания") "Данные предоставлены сервисом Яндекс.Расписания" else "Источник: ${data.sourceName}")
                    }
                    Text("Снимок ${data.timetable.snapshotDate}. ${data.timetable.coverageNote}", style = MaterialTheme.typography.bodySmall)
                } }
                state.result?.let { result ->
                    item {
                        Text(if (result.status == SearchStatus.COMPLETED_IN_WINDOW) "Найдено вариантов: ${journeys.size}" else "Поиск не завершён",
                            style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("result_title"))
                        Text("По загруженному расписанию; окно отправлений — ${state.horizonDays} дней", style = MaterialTheme.typography.bodySmall)
                        if (result.errors.isNotEmpty()) Text(result.errors.joinToString("\n"), color = MaterialTheme.colorScheme.error)
                        if (journeys.isEmpty() && result.status == SearchStatus.COMPLETED_IN_WINDOW) Text("Варианты в этой базе и периоде не найдены. Это не означает отсутствия сообщения.")
                    }
                    items(journeys.indices.toList()) { index ->
                        val journey = journeys[index]
                        OutlinedCard(onClick = { detail = index }, modifier = Modifier.fillMaxWidth().testTag("route_$index")) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (index == 0) "Лучший из найденных" else "Альтернатива $index", fontWeight = FontWeight.Bold)
                                Text("${durationText(journey.durationMinutes)} · пересадок: ${journey.transferCount}")
                                Text("Отправление: ${formatTime(journey.departure, from?.stations?.firstOrNull()?.timeZone)}")
                                Text(journey.legs.joinToString(" → ") { it.routeName }, style = MaterialTheme.typography.bodySmall)
                                if (detail == index) Text("Выбран", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    shownJourney?.let { journey ->
                        item { Text("Поездка по участкам", style = MaterialTheme.typography.titleMedium) }
                        items(journey.legs.indices.toList()) { index ->
                            val leg = journey.legs[index]
                            SectionCard {
                                if (index > 0) Text("Ожидание пересадки: ${durationText(journey.waitingMinutesBefore(index))}", fontWeight = FontWeight.Bold)
                                val departureStation = stations.find { it.id == leg.fromStationId }
                                val arrivalStation = stations.find { it.id == leg.toStationId }
                                Text("${leg.transport.title}: ${leg.routeName}")
                                Text("${departureStation?.name ?: leg.fromStationId}\n${formatTime(leg.departure, departureStation?.timeZone)}")
                                Text("↓")
                                Text("${arrivalStation?.name ?: leg.toStationId}\n${formatTime(leg.arrival, arrivalStation?.timeZone)}")
                            }
                        }
                        item { Text("Время указано в часовом поясе соответствующей станции.", style = MaterialTheme.typography.bodySmall) }
                    }
                    item {
                        for (note in result.limitations) Text(note, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    cityPicker?.let { origin ->
        CityPicker(cities, onSelect = { model.chooseCity(it.id, origin); cityPicker = null }, onDismiss = { cityPicker = null })
    }
    mapSelection?.let { city ->
        AlertDialog(onDismissRequest = { mapSelection = null }, title = { Text(city.name) },
            text = { Text("В поиск войдут все загруженные станции города") },
            confirmButton = { TextButton(onClick = { model.chooseCity(city.id, true); mapSelection = null }) { Text("Отсюда") } },
            dismissButton = { TextButton(onClick = { model.chooseCity(city.id, false); mapSelection = null }) { Text("Сюда") } })
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
}

@Composable
private fun CityPicker(cities: List<City>, onSelect: (City) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val matches = remember(cities, query) { cities.filter { it.name.contains(query.trim(), ignoreCase = true) }.take(100) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Выбор города") },
        text = {
            Column {
                OutlinedTextField(query, onValueChange = { query = it }, label = { Text("Название города") }, singleLine = true, modifier = Modifier.testTag("city_query"))
                LazyColumn(Modifier.heightIn(max = 350.dp)) {
                    items(matches, key = { it.id }) { city ->
                        Column(Modifier.fillMaxWidth().clickable { onSelect(city) }.padding(vertical = 12.dp).testTag("city_${city.id}")) {
                            Text(city.name)
                            Text("Станций в поиске: ${city.stations.size}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (matches.isEmpty()) item { Text("Ничего не найдено в загруженных данных", Modifier.padding(12.dp)) }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } })
}

fun durationText(minutes: Long): String {
    val days = minutes / 1440
    val hours = minutes % 1440 / 60
    val rest = minutes % 60
    return if (days > 0) "$days д $hours ч $rest мин" else if (hours > 0) "$hours ч $rest мин" else "$rest мин"
}

private fun formatTime(instant: Instant, zone: String?): String = instant.atZone(ZoneId.of(zone ?: "UTC"))
    .format(DateTimeFormatter.ofPattern("dd.MM HH:mm (XXX)"))
