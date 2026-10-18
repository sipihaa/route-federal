package ru.vladm.routefederal.ui.data

import java.time.LocalDate

data class DataUiState(
    val savedApiKey: String? = null,
    val enteredApiKey: String = "",
    val selectedDate: LocalDate = LocalDate.now(),
    val loadedDates: List<LocalDate> = emptyList(),
    val isLoading: Boolean = false,
    // Доля выполненной загрузки от 0 до 1 (имеет смысл, пока isLoading = true)
    val progress: Float = 0f,
    // Сообщение для Snackbar: ошибка или итог загрузки. null — показывать нечего.
    val message: String? = null,
)
