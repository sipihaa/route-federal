package ru.milin.routefederal.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.milin.routefederal.data.*
import ru.milin.routefederal.routing.*
import java.time.LocalDate
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive

data class RouteUiState(
    val loading: Boolean = true,
    val searching: Boolean = false,
    val data: DataInfo? = null,
    val map: List<List<MapPoint>> = emptyList(),
    val fromId: String? = null,
    val toId: String? = null,
    val date: LocalDate = LocalDate.now(),
    val horizonDays: Int = 7,
    val result: RouteSearchResult? = null,
    val message: String? = null
)

class RouteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TimetableRepository(application)
    private val mutableState = MutableStateFlow(RouteUiState())
    val state = mutableState.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val map = withContext(Dispatchers.IO) { repository.loadMap() }
                mutableState.value = mutableState.value.copy(map = map)
                val data = withContext(Dispatchers.IO) { repository.load() }
                mutableState.value = mutableState.value.copy(loading = false, data = data)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.value = mutableState.value.copy(loading = false, message = error.message ?: "Не удалось прочитать данные.")
            }
        }
    }

    fun chooseStation(id: String, origin: Boolean) {
        cancelSearch()
        val old = mutableState.value
        mutableState.value = if (origin) old.copy(fromId = id, result = null, message = null)
        else old.copy(toId = id, result = null, message = null)
    }

    fun swapStations() {
        cancelSearch()
        val old = mutableState.value
        mutableState.value = old.copy(fromId = old.toId, toId = old.fromId, result = null, message = null)
    }

    fun chooseDate(date: LocalDate) {
        cancelSearch()
        mutableState.value = mutableState.value.copy(date = date, result = null, message = null)
    }

    fun chooseHorizon(days: Int) {
        cancelSearch()
        mutableState.value = mutableState.value.copy(horizonDays = days, result = null)
    }

    fun cancelSearch() {
        searchJob?.cancel()
        mutableState.value = mutableState.value.copy(searching = false)
    }

    fun search() {
        cancelSearch()
        val input = mutableState.value
        val timetable = input.data?.timetable
        if (timetable == null || input.fromId == null || input.toId == null) {
            mutableState.value = input.copy(message = "Выберите начальную и конечную станции.")
            return
        }
        if (input.fromId == input.toId) {
            mutableState.value = input.copy(message = "Начальная и конечная станции должны различаться.", result = null)
            return
        }
        searchJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(searching = true, result = null, message = null)
            try {
                val result = withContext(Dispatchers.Default) {
                    val context = coroutineContext
                    RouteFinder(timetable).search(input.fromId, input.toId, input.date, input.horizonDays) { !context.isActive }
                }
                mutableState.value = mutableState.value.copy(searching = false, result = result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(searching = false, message = error.message ?: "Ошибка поиска.")
            }
        }
    }

    fun importData(uri: Uri) {
        cancelSearch()
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, message = null)
            try {
                val data = withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: error("Не удалось открыть файл.")
                    input.use { repository.importFile(it) }
                }
                mutableState.value = mutableState.value.copy(loading = false, data = data, fromId = null, toId = null, result = null)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.value = mutableState.value.copy(loading = false, message = error.message ?: "Ошибка импорта. Прежняя база сохранена.")
            }
        }
    }
}
