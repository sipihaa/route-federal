package ru.vladm.routefederal.ui.data

import java.time.LocalDate

data class DataUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val loadedDates: List<LocalDate> = emptyList(),
    val isLoading: Boolean = false,
    val progress: Float = 0f,
    val message: String? = null,
)
