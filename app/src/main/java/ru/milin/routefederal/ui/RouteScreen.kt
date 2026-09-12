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
    var stationPicker by remember { mutableStateOf<Boolean?>(null) }
    var mapSelection by remember { mutableStateOf<Station?>(null) }
    var detail by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importData(uri)
    }
    val stations = state.data?.timetable?.stations.orEmpty()
    val from = stations.find { it.id == state.fromId }
    val to = stations.find { it.id == state.toId }
    val journeys = state.result?.routes.orEmpty()
    LaunchedEffect(state.result) { detail = 0 }
    val shownJourney = journeys.getOrNull(detail)
    val selectedIds = shownJourney?.legs?.flatMap { it.stopIds } ?: listOfNotNull(state.fromId, state.toId)

    Scaffold { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets).testTag("main_content"),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Route Federal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Поезда и автобусы · маршруты по расписанию", style = MaterialTheme.typography.bodyMedium)
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
                            Text("Период данных: ${data.timetable.validFrom} — ${data.timetable.validUntil}")
                            Text("Станций: ${stations.size} · расписаний: ${data.timetable.trips.size}")
                            Text(data.timetable.coverageNote)
                            Text(data.sourceUrl, style = MaterialTheme.typography.bodySmall)
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
                        Text("Первое отправление — в выбранную дату. Ожидания на пересадках входят во время в пути; ожидание до первой посадки не входит.")
                        Text("Ищем прибытие в течение выбранного периода:")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (days in listOf(7, 14, 30)) FilterChip(state.horizonDays == days,
                                onClick = { model.chooseHorizon(days) }, label = { Text("$days дней") })
                        }
                        Text("Пересадки разрешены только в одном узле с известным правилом времени пересадки. Переходы между вокзалами не строятся.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    Text("Карта: Natural Earth, public domain. Обзорная география, без уличной навигации. Линии показывают связи станций, а не точный путь транспорта.", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                item {
                    OfflineMap(state.map, stations, selectedIds, onStationClick = { mapSelection = it }, showRoute = shownJourney != null,
                        modifier = Modifier.fillMaxWidth().height(260.dp))
                }
                item {
                    SectionCard {
                        OutlinedButton(onClick = { stationPicker = true }, enabled = stations.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("choose_from")) {
                            Text("Откуда: ${from?.name ?: "выберите станцию"}")
                        }
                        OutlinedButton(onClick = { stationPicker = false }, enabled = stations.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("choose_to")) {
                            Text("Куда: ${to?.name ?: "выберите станцию"}")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = model::swapStations, enabled = !state.loading, modifier = Modifier.testTag("swap")) { Text("Поменять местами") }
                            TextButton(onClick = {
                                DatePickerDialog(context, { _, year, month, day -> model.chooseDate(LocalDate.of(year, month + 1, day)) },
                                    state.date.year, state.date.monthValue - 1, state.date.dayOfMonth).show()
                            }, modifier = Modifier.testTag("choose_date")) { Text(state.date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))) }
                        }
                        Text("Ожидание до первой посадки не учитывается", style = MaterialTheme.typography.bodySmall)
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
                    Text("Снимок ${data.timetable.snapshotDate}. ${data.timetable.coverageNote}", style = MaterialTheme.typography.bodySmall)
                } }
                state.result?.let { result ->
                    item {
                        Text(if (result.status == SearchStatus.COMPLETED_IN_WINDOW) "Найдено вариантов: ${journeys.size}" else "Поиск не завершён",
                            style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("result_title"))
                        Text("По загруженному расписанию, в пределах ${state.horizonDays} дней", style = MaterialTheme.typography.bodySmall)
                        if (result.errors.isNotEmpty()) Text(result.errors.joinToString("\n"), color = MaterialTheme.colorScheme.error)
                        if (journeys.isEmpty() && result.status == SearchStatus.COMPLETED_IN_WINDOW) Text("Варианты в этой базе и периоде не найдены. Это не означает отсутствия сообщения.")
                    }
                    items(journeys.indices.toList()) { index ->
                        val journey = journeys[index]
                        OutlinedCard(onClick = { detail = index }, modifier = Modifier.fillMaxWidth().testTag("route_$index")) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (index == 0) "Лучший из найденных" else "Альтернатива $index", fontWeight = FontWeight.Bold)
                                Text("${durationText(journey.durationMinutes)} · пересадок: ${journey.transferCount}")
                                Text(journey.legs.joinToString(" → ") { it.routeName }, style = MaterialTheme.typography.bodySmall)
                                if (detail == index) Text("Выбран", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    shownJourney?.let { journey -> item {
                        SectionCard {
                            Text("Поездка по участкам", style = MaterialTheme.typography.titleMedium)
                            for ((index, leg) in journey.legs.withIndex()) {
                                if (index > 0) Text("Ожидание пересадки: ${durationText(journey.waitingMinutesBefore(index))}", fontWeight = FontWeight.Bold)
                                val departureStation = stations.find { it.id == leg.fromStationId }
                                val arrivalStation = stations.find { it.id == leg.toStationId }
                                Text("${if (leg.transport == TransportType.BUS) "Автобус" else "Поезд"}: ${leg.routeName}")
                                Text("${departureStation?.name ?: leg.fromStationId}\n${formatTime(leg.departure, departureStation?.timeZone)}")
                                Text("↓")
                                Text("${arrivalStation?.name ?: leg.toStationId}\n${formatTime(leg.arrival, arrivalStation?.timeZone)}")
                                HorizontalDivider()
                            }
                            Text("Время указано в часовом поясе соответствующей станции.", style = MaterialTheme.typography.bodySmall)
                        }
                    } }
                    item {
                        for (note in result.limitations) Text(note, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    stationPicker?.let { origin ->
        StationPicker(stations, onSelect = { model.chooseStation(it.id, origin); stationPicker = null }, onDismiss = { stationPicker = null })
    }
    mapSelection?.let { station ->
        AlertDialog(onDismissRequest = { mapSelection = null }, title = { Text(station.name) },
            text = { Text("Выбрать эту станцию для маршрута") },
            confirmButton = { TextButton(onClick = { model.chooseStation(station.id, true); mapSelection = null }) { Text("Отсюда") } },
            dismissButton = { TextButton(onClick = { model.chooseStation(station.id, false); mapSelection = null }) { Text("Сюда") } })
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
}

@Composable
private fun StationPicker(stations: List<Station>, onSelect: (Station) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val matches = remember(stations, query) { stations.filter { it.name.contains(query.trim(), ignoreCase = true) || it.region.contains(query.trim(), ignoreCase = true) }.take(100) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Выбор станции") },
        text = {
            Column {
                OutlinedTextField(query, onValueChange = { query = it }, label = { Text("Название или регион") }, singleLine = true, modifier = Modifier.testTag("station_query"))
                LazyColumn(Modifier.heightIn(max = 350.dp)) {
                    items(matches, key = { it.id }) { station ->
                        Column(Modifier.fillMaxWidth().clickable { onSelect(station) }.padding(vertical = 12.dp).testTag("station_${station.id}")) {
                            Text(station.name)
                            Text("Регион ${station.region} · ${station.id}", style = MaterialTheme.typography.bodySmall)
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
