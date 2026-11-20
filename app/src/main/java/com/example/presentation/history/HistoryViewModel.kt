package com.example.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.model.CompressionHistoryItem
import com.example.domain.repository.CompressionHistoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val items: List<CompressionHistoryItem> = emptyList(),
    val totalCompressedCount: Int = 0,
    val totalSpaceSavedBytes: Long = 0L
)

class HistoryViewModel(
    private val historyRepository: CompressionHistoryRepository
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = historyRepository.observeAllHistory()
        .map { items ->
            HistoryUiState(
                items = items,
                totalCompressedCount = items.size,
                totalSpaceSavedBytes = items.sumOf { it.spaceSavedBytes.coerceAtLeast(0L) }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HistoryUiState()
        )

    fun deleteRecord(item: CompressionHistoryItem) {
        viewModelScope.launch {
            historyRepository.deleteRecord(item)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyRepository.clearAllHistory()
        }
    }

    class Factory(
        private val historyRepository: CompressionHistoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(historyRepository) as T
        }
    }
}
