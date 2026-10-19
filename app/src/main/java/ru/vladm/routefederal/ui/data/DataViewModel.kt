package ru.vladm.routefederal.ui.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.vladm.routefederal.RouteFederalApp
import ru.vladm.routefederal.data.ScheduleRepository
import ru.vladm.routefederal.data.SettingsRepository
import ru.vladm.routefederal.ui.common.dateFormatter
import java.time.LocalDate

class DataViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataUiState())
    val uiState: StateFlow<DataUiState> = _uiState.asStateFlow()

    init {
        // Сохранённый ключ читаем из DataStore один раз и подставляем в поле ввода
        viewModelScope.launch {
            val key = settingsRepository.apiKey.first()
            _uiState.update { it.copy(savedApiKey = key, enteredApiKey = key.orEmpty()) }
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
        viewModelScope.launch {
            settingsRepository.saveApiKey(key)
            _uiState.update { it.copy(savedApiKey = key, message = "Ключ сохранён") }
        }
    }

    fun onDateSelected(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun onDownloadClick() {
        val key = uiState.value.savedApiKey
        if (key.isNullOrBlank()) {
            _uiState.update { it.copy(message = "Сначала сохраните API-ключ") }
            return
        }
        val date = uiState.value.selectedDate
        _uiState.update { it.copy(isLoading = true, progress = 0f) }

        viewModelScope.launch {
            val message = try {
                scheduleRepository.download(date, key) { done, total ->
                    _uiState.update { it.copy(progress = done.toFloat() / total) }
                }
                "Расписание на ${date.format(dateFormatter)} загружено"
            } catch (e: Exception) {
                "Не удалось загрузить расписание. Проверьте ключ и подключение к интернету"
            }
            _uiState.update { it.copy(isLoading = false, message = message) }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as RouteFederalApp).container
                DataViewModel(container.scheduleRepository, container.settingsRepository)
            }
        }
    }
}
