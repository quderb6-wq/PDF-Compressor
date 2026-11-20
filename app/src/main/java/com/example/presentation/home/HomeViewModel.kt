package com.example.presentation.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.domain.model.CompressionConfig
import com.example.domain.model.CompressionHistoryItem
import com.example.domain.model.CompressionLevel
import com.example.domain.model.CompressionPreset
import com.example.domain.model.CompressionProgress
import com.example.domain.model.PdfDocumentInfo
import com.example.domain.model.ResolutionOption
import com.example.domain.repository.CompressionOutcome
import com.example.domain.repository.InspectionOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val selectedPdf: PdfDocumentInfo? = null,
    val isInspecting: Boolean = false,
    val compressionConfig: CompressionConfig = CompressionConfig.forPreset(CompressionPreset.RECOMMENDED),
    val isCompressing: Boolean = false,
    val compressionProgress: CompressionProgress? = null,
    val errorMessage: String? = null,
    val recentHistory: List<CompressionHistoryItem> = emptyList()
)

sealed class HomeEvent {
    data class NavigateToResult(val resultId: String) : HomeEvent()
    data class ShowMessage(val message: String) : HomeEvent()
}

class HomeViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val _localState = MutableStateFlow(HomeUiState())
    private val _events = MutableSharedFlow<HomeEvent>()
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    private var activeCompressionJob: Job? = null

    val uiState: StateFlow<HomeUiState> = combine(
        _localState,
        container.historyRepository.observeRecentHistory(limit = 3)
    ) { state, recent ->
        state.copy(recentHistory = recent)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    init {
        viewModelScope.launch {
            val defaultPreset = container.settingsRepository.defaultPreset.first()
            _localState.update {
                it.copy(compressionConfig = CompressionConfig.forPreset(defaultPreset))
            }
        }
    }

    fun onPdfSelected(uri: Uri) {
        if (_localState.value.isCompressing) return
        val previousThumb = _localState.value.selectedPdf?.thumbnailPath

        viewModelScope.launch {
            _localState.update {
                it.copy(
                    isInspecting = true,
                    errorMessage = null
                )
            }
            when (val outcome = container.inspectPdfUseCase(uri)) {
                is InspectionOutcome.Success -> {
                    if (previousThumb != null && previousThumb != outcome.info.thumbnailPath) {
                        container.pdfCompressionRepository.cleanupTempFile(previousThumb)
                    }
                    _localState.update {
                        it.copy(
                            selectedPdf = outcome.info,
                            isInspecting = false,
                            errorMessage = null
                        )
                    }
                }
                is InspectionOutcome.Error -> {
                    _localState.update {
                        it.copy(
                            isInspecting = false,
                            errorMessage = outcome.error.userMessage
                        )
                    }
                }
            }
        }
    }

    fun clearSelectedPdf() {
        if (_localState.value.isCompressing) return
        val thumb = _localState.value.selectedPdf?.thumbnailPath
        _localState.update { it.copy(selectedPdf = null, errorMessage = null) }
        viewModelScope.launch {
            container.pdfCompressionRepository.cleanupTempFile(thumb)
        }
    }

    fun selectPreset(preset: CompressionPreset) {
        _localState.update { state ->
            val newConfig = if (preset == CompressionPreset.CUSTOM && state.compressionConfig.preset == CompressionPreset.CUSTOM) {
                state.compressionConfig
            } else {
                CompressionConfig.forPreset(preset)
            }
            state.copy(compressionConfig = newConfig, errorMessage = null)
        }
    }

    fun updateCustomQuality(quality: Int) {
        _localState.update { state ->
            state.copy(
                compressionConfig = state.compressionConfig.copy(
                    preset = CompressionPreset.CUSTOM,
                    imageQuality = quality.coerceIn(15, 95)
                )
            )
        }
    }

    fun updateCustomResolution(option: ResolutionOption) {
        _localState.update { state ->
            state.copy(
                compressionConfig = state.compressionConfig.copy(
                    preset = CompressionPreset.CUSTOM,
                    resolutionOption = option
                )
            )
        }
    }

    fun updateCustomLevel(level: CompressionLevel) {
        _localState.update { state ->
            state.copy(
                compressionConfig = state.compressionConfig.copy(
                    preset = CompressionPreset.CUSTOM,
                    compressionLevel = level
                )
            )
        }
    }

    fun updateOptimizeColor(enabled: Boolean) {
        _localState.update { state ->
            state.copy(
                compressionConfig = state.compressionConfig.copy(
                    preset = CompressionPreset.CUSTOM,
                    optimizeColorImages = enabled
                )
            )
        }
    }

    fun updateGrayscale(enabled: Boolean) {
        _localState.update { state ->
            state.copy(
                compressionConfig = state.compressionConfig.copy(
                    preset = CompressionPreset.CUSTOM,
                    grayscaleImages = enabled
                )
            )
        }
    }

    fun dismissError() {
        _localState.update { it.copy(errorMessage = null) }
    }

    fun startCompression() {
        val currentState = _localState.value
        if (currentState.isCompressing || currentState.isInspecting) return

        val doc = currentState.selectedPdf
        if (doc == null) {
            _localState.update {
                it.copy(errorMessage = "Please select a PDF document first.")
            }
            return
        }

        activeCompressionJob?.cancel()
        activeCompressionJob = viewModelScope.launch {
            _localState.update {
                it.copy(
                    isCompressing = true,
                    compressionProgress = CompressionProgress(),
                    errorMessage = null
                )
            }

            try {
                when (
                    val outcome = container.compressPdfUseCase(
                        documentInfo = doc,
                        config = currentState.compressionConfig,
                        onProgress = { progress ->
                            _localState.update { state ->
                                state.copy(compressionProgress = progress)
                            }
                        }
                    )
                ) {
                    is CompressionOutcome.Success -> {
                        container.putActiveResult(outcome.result)
                        _localState.update {
                            it.copy(
                                isCompressing = false,
                                compressionProgress = null
                            )
                        }
                        _events.emit(HomeEvent.NavigateToResult(outcome.result.id))
                    }
                    is CompressionOutcome.Error -> {
                        _localState.update {
                            it.copy(
                                isCompressing = false,
                                compressionProgress = null,
                                errorMessage = outcome.error.userMessage
                            )
                        }
                    }
                }
            } catch (_: CancellationException) {
                _localState.update {
                    it.copy(
                        isCompressing = false,
                        compressionProgress = null
                    )
                }
                _events.emit(HomeEvent.ShowMessage("Compression cancelled."))
            }
        }
    }

    fun cancelCompression() {
        activeCompressionJob?.cancel()
        activeCompressionJob = null
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(container) as T
        }
    }
}
