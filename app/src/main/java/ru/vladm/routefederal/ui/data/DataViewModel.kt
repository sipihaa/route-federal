package ru.vladm.routefederal.ui.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.vladm.routefederal.data.ScheduleRepository
import ru.vladm.routefederal.ui.common.dateFormatter
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class DataViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataUiState())
    val uiState: StateFlow<DataUiState> = _uiState.asStateFlow()

    init {
        // Загруженные даты — из репозитория, общего с главным экраном
        viewModelScope.launch {
            scheduleRepository.loadedDates.collect { dates ->
                _uiState.update { it.copy(loadedDates = dates) }
            }
        }
    }

    fun onDateSelected(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun onDownloadClick() {
        val date = uiState.value.selectedDate
        _uiState.update { it.copy(isLoading = true, progress = 0f) }

        viewModelScope.launch {
            val message = try {
                scheduleRepository.download(date) { done, total ->
                    _uiState.update { it.copy(progress = done.toFloat() / total) }
                }
                "Расписание на ${date.format(dateFormatter)} загружено"
            } catch (_: Exception) {
                "Не удалось загрузить расписание. Проверьте ключ и подключение к интернету"
            }
            _uiState.update { it.copy(isLoading = false, message = message) }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }
}
