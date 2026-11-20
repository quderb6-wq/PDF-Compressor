package com.example

import android.app.Application
import com.example.compression.PdfCompressionEngine
import com.example.data.local.PreferencesDataStore
import com.example.data.repository.CompressionHistoryRepositoryImpl
import com.example.data.repository.PdfCompressionRepositoryImpl
import com.example.data.repository.SettingsRepositoryImpl
import com.example.database.AppDatabase
import com.example.domain.model.CompressionResult
import com.example.domain.repository.CompressionHistoryRepository
import com.example.domain.repository.PdfCompressionRepository
import com.example.domain.repository.SettingsRepository
import com.example.domain.usecase.CompressPdfUseCase
import com.example.domain.usecase.InspectPdfUseCase
import com.example.domain.usecase.SaveCompressedPdfUseCase
import com.example.pdf.PdfInspector
import com.example.pdf.PdfStorageManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AppContainer(application: Application) {
    val storageManager = PdfStorageManager(application)
    private val database = AppDatabase.getInstance(application)
    private val dataStore = PreferencesDataStore(application)
    private val inspector = PdfInspector(application, storageManager)
    private val engine = PdfCompressionEngine(application, storageManager)

    val historyRepository: CompressionHistoryRepository =
        CompressionHistoryRepositoryImpl(database.compressionHistoryDao(), storageManager)

    val settingsRepository: SettingsRepository =
        SettingsRepositoryImpl(dataStore)

    val pdfCompressionRepository: PdfCompressionRepository =
        PdfCompressionRepositoryImpl(inspector, engine, storageManager, historyRepository)

    val inspectPdfUseCase = InspectPdfUseCase(pdfCompressionRepository)
    val compressPdfUseCase = CompressPdfUseCase(pdfCompressionRepository)
    val saveCompressedPdfUseCase = SaveCompressedPdfUseCase(pdfCompressionRepository)

    // In-memory store for active compression results passed cleanly by ID via Navigation
    private val _activeResults = MutableStateFlow<Map<String, CompressionResult>>(emptyMap())
    val activeResults: StateFlow<Map<String, CompressionResult>> = _activeResults.asStateFlow()

    fun putActiveResult(result: CompressionResult) {
        _activeResults.update { it + (result.id to result) }
    }

    fun getActiveResult(id: String): CompressionResult? = _activeResults.value[id]
}

class PdfCompressorApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(this)
    }
}
