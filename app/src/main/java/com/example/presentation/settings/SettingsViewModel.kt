package com.example.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.model.CompressionPreset
import com.example.domain.model.ThemeMode
import com.example.domain.repository.PdfCompressionRepository
import com.example.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultPreset: CompressionPreset = CompressionPreset.RECOMMENDED,
    val cacheSizeBytes: Long = 0L
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val pdfRepository: PdfCompressionRepository
) : ViewModel() {

    private val _cacheSize = MutableStateFlow(pdfRepository.getCacheSizeBytes())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.themeMode,
        settingsRepository.defaultPreset,
        _cacheSize
    ) { theme, preset, cacheBytes ->
        SettingsUiState(
            themeMode = theme,
            defaultPreset = preset,
            cacheSizeBytes = cacheBytes
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun refreshCacheSize() {
        _cacheSize.value = pdfRepository.getCacheSizeBytes()
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setDefaultPreset(preset: CompressionPreset) {
        viewModelScope.launch {
            settingsRepository.setDefaultPreset(preset)
        }
    }

    fun clearWorkCache(onCleared: (Long) -> Unit) {
        viewModelScope.launch {
            val freed = pdfRepository.clearWorkCache()
            _cacheSize.value = pdfRepository.getCacheSizeBytes()
            onCleared(freed)
        }
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val pdfRepository: PdfCompressionRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsRepository, pdfRepository) as T
        }
    }
}
