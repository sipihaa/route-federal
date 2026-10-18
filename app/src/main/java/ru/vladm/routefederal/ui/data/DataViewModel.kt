package ru.vladm.routefederal.ui.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import ru.vladm.routefederal.RouteFederalApp
import ru.vladm.routefederal.data.ScheduleRepository
import ru.vladm.routefederal.data.SettingsRepository
import ru.vladm.routefederal.ui.common.dateFormatter
import java.io.IOException
import java.time.LocalDate

class DataViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataUiState())
    val uiState: StateFlow<DataUiState> = _uiState.asStateFlow()

    init {
        // Сохранённый ключ приходит из DataStore потоком: сразу и после каждого сохранения
        viewModelScope.launch {
            settingsRepository.apiKey.collect { key ->
                _uiState.update {
                    it.copy(
                        savedApiKey = key,
                        // При первом получении подставляем сохранённый ключ в поле ввода
                        enteredApiKey = if (it.savedApiKey == null && it.enteredApiKey.isEmpty()) key.orEmpty() else it.enteredApiKey,
                    )
                }
            }
        }
        // Загруженные даты — из репозитория, общего с главным экраном
        viewModelScope.launch {
            scheduleRepository.loadedDates.collect { dates ->
                _uiState.update { it.copy(loadedDates = dates) }
            }
        }
    }

    fun onApiKeyChanged(text: String) {
        _uiState.update { it.copy(enteredApiKey = text) }
    }

    fun onSaveApiKeyClick() {
        val key = uiState.value.enteredApiKey.trim()
        if (key.isEmpty()) {
            _uiState.update { it.copy(message = "Введите API-ключ") }
            return
        }
        viewModelScope.launch {
            settingsRepository.saveApiKey(key)
            _uiState.update { it.copy(message = "Ключ сохранён") }
        }
    }

    fun onDateSelected(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun onDownloadClick() {
        val state = uiState.value
        if (state.isLoading) return
        val key = state.savedApiKey
        if (key.isNullOrBlank()) {
            _uiState.update { it.copy(message = "Сначала сохраните API-ключ") }
            return
        }
        val date = state.selectedDate
        _uiState.update { it.copy(isLoading = true, progress = 0f, message = null) }

        viewModelScope.launch {
            val message = try {
                val requests = scheduleRepository.download(date, key) { done, total ->
                    _uiState.update { it.copy(progress = done.toFloat() / total) }
                }
                "Расписание на ${date.format(dateFormatter)} загружено ($requests запросов к API)"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorText(e)
            }
            _uiState.update { it.copy(isLoading = false, message = message) }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private fun errorText(e: Exception): String = when {
        e is HttpException && e.code() == 429 -> "Превышен лимит запросов к API"
        // API присылает причину ошибки в теле ответа: {"error": {"text": "Неверный ключ ..."}}
        e is HttpException -> apiErrorText(e)?.let { "Ошибка API: $it" } ?: "Ошибка API: HTTP ${e.code()}"
        e is IOException -> "Нет соединения с сервером"
        else -> "Не удалось загрузить расписание: ${e.message}"
    }

    private fun apiErrorText(e: HttpException): String? = runCatching {
        val body = e.response()?.errorBody()?.string() ?: return null
        Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("text")?.jsonPrimitive?.content
    }.getOrNull()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as RouteFederalApp).container
                DataViewModel(container.scheduleRepository, container.settingsRepository)
            }
        }
    }
}
