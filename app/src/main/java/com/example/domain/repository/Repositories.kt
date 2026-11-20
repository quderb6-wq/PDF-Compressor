package com.example.domain.repository

import android.net.Uri
import com.example.domain.model.CompressionConfig
import com.example.domain.model.CompressionHistoryItem
import com.example.domain.model.CompressionPreset
import com.example.domain.model.CompressionProgress
import com.example.domain.model.CompressionResult
import com.example.domain.model.PdfDocumentInfo
import com.example.domain.model.PdfError
import com.example.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

sealed class InspectionOutcome {
    data class Success(val info: PdfDocumentInfo) : InspectionOutcome()
    data class Error(val error: PdfError) : InspectionOutcome()
}

sealed class CompressionOutcome {
    data class Success(val result: CompressionResult) : CompressionOutcome()
    data class Error(val error: PdfError) : CompressionOutcome()
}

sealed class SaveOutcome {
    data class Success(
        val savedUriString: String,
        val savedFileName: String,
        val verifiedSizeBytes: Long,
        val managedCopyPath: String?,
        val historyId: Long?
    ) : SaveOutcome()

    data class Error(val error: PdfError) : SaveOutcome()
}

interface PdfCompressionRepository {
    suspend fun inspectPdf(uri: Uri): InspectionOutcome
    suspend fun compressPdf(
        documentInfo: PdfDocumentInfo,
        config: CompressionConfig,
        onProgress: (CompressionProgress) -> Unit
    ): CompressionOutcome

    suspend fun saveCompressedPdf(
        result: CompressionResult,
        destinationUri: Uri,
        desiredFileName: String
    ): SaveOutcome

    suspend fun cleanupTempFile(path: String?)
    suspend fun clearWorkCache(): Long
    fun getCacheSizeBytes(): Long
}

interface CompressionHistoryRepository {
    fun observeAllHistory(): Flow<List<CompressionHistoryItem>>
    fun observeRecentHistory(limit: Int = 3): Flow<List<CompressionHistoryItem>>
    suspend fun insertRecord(item: CompressionHistoryItem): Long
    suspend fun deleteRecord(item: CompressionHistoryItem)
    suspend fun clearAllHistory()
}

interface SettingsRepository {
    val themeMode: Flow<ThemeMode>
    val defaultPreset: Flow<CompressionPreset>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDefaultPreset(preset: CompressionPreset)
}
