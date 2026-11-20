package com.example.presentation.result

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.domain.model.CompressionResult
import com.example.domain.repository.SaveOutcome
import com.example.utils.FileSizeFormatter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ResultUiState(
    val result: CompressionResult? = null,
    val outputFileName: String = "",
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
    val keptOriginal: Boolean = false
)

sealed class ResultEvent {
    data class ShowMessage(val message: String) : ResultEvent()
    data object NavigateBackHome : ResultEvent()
}

class ResultViewModel(
    private val resultId: String,
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ResultEvent>()
    val events: SharedFlow<ResultEvent> = _events.asSharedFlow()

    init {
        val loaded = container.getActiveResult(resultId)
        _uiState.value = ResultUiState(
            result = loaded,
            outputFileName = loaded?.suggestedOutputName ?: "compressed.pdf",
            isSaved = loaded?.savedUriString != null
        )
    }

    fun updateOutputFileName(name: String) {
        _uiState.update { it.copy(outputFileName = name, errorMessage = null) }
    }

    fun getNormalizedSaveFileName(): String {
        return FileSizeFormatter.ensurePdfExtension(_uiState.value.outputFileName)
    }

    fun saveToSafUri(destinationUri: Uri) {
        val currentResult = _uiState.value.result ?: return
        if (_uiState.value.isSaving) return

        val fileName = getNormalizedSaveFileName()
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            when (val outcome = container.saveCompressedPdfUseCase(currentResult, destinationUri, fileName)) {
                is SaveOutcome.Success -> {
                    val updatedResult = currentResult.copy(
                        savedUriString = outcome.savedUriString,
                        savedDisplayName = outcome.savedFileName,
                        managedCopyPath = outcome.managedCopyPath,
                        historyRecordId = outcome.historyId
                    )
                    container.putActiveResult(updatedResult)
                    _uiState.update {
                        it.copy(
                            result = updatedResult,
                            outputFileName = outcome.savedFileName,
                            isSaving = false,
                            isSaved = true
                        )
                    }
                    _events.emit(ResultEvent.ShowMessage("Saved & verified: ${outcome.savedFileName}"))
                }
                is SaveOutcome.Error -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = outcome.error.userMessage
                        )
                    }
                }
            }
        }
    }

    fun keepOriginalInstead() {
        val tempPath = _uiState.value.result?.tempFilePath
        viewModelScope.launch {
            container.pdfCompressionRepository.cleanupTempFile(tempPath)
            _uiState.update { it.copy(keptOriginal = true) }
            _events.emit(ResultEvent.ShowMessage("Kept original PDF. Larger temporary output discarded."))
            _events.emit(ResultEvent.NavigateBackHome)
        }
    }

    fun onCompressAgain() {
        val tempPath = _uiState.value.result?.tempFilePath
        viewModelScope.launch {
            container.pdfCompressionRepository.cleanupTempFile(tempPath)
            _events.emit(ResultEvent.NavigateBackHome)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    class Factory(
        private val resultId: String,
        private val container: AppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ResultViewModel(resultId, container) as T
        }
    }
}
