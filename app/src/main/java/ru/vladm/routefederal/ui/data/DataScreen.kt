package ru.vladm.routefederal.ui.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.vladm.routefederal.ui.common.DateField
import ru.vladm.routefederal.ui.common.shortDateFormatter
import java.time.LocalDate

@Composable
fun DataRoute(viewModel: DataViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    DataScreen(
        state = state,
        onDateSelected = viewModel::onDateSelected,
        onDownloadClick = viewModel::onDownloadClick,
        onMessageShown = viewModel::onMessageShown,
    )
}

@Composable
fun DataScreen(
    state: DataUiState,
    onDateSelected: (LocalDate) -> Unit,
    onDownloadClick: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Расписание", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Для поиска на дату нужны расписания на эту дату и на следующий день.",
                style = MaterialTheme.typography.bodySmall,
            )
            DateField(
                date = state.selectedDate,
                onDateSelected = onDateSelected,
                enabled = !state.isLoading,
            )
            Button(
                onClick = onDownloadClick,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isLoading) "Загрузка…" else "Скачать расписание")
            }
            if (state.isLoading) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text("Загруженные даты", style = MaterialTheme.typography.titleMedium)
            if (state.loadedDates.isEmpty()) {
                Text("Пока ничего не загружено", style = MaterialTheme.typography.bodySmall)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.loadedDates) { date ->
                        SuggestionChip(
                            onClick = { onDateSelected(date) },
                            label = { Text(date.format(shortDateFormatter)) },
                        )
                    }
                }
            }

            Text(
                text = "Данные: Яндекс Расписания",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
