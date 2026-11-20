package com.example.data.repository

import android.net.Uri
import com.example.compression.PdfCompressionEngine
import com.example.data.local.PreferencesDataStore
import com.example.database.CompressionHistoryDao
import com.example.database.CompressionHistoryEntity
import com.example.domain.model.CompressionConfig
import com.example.domain.model.CompressionHistoryItem
import com.example.domain.model.CompressionPreset
import com.example.domain.model.CompressionProgress
import com.example.domain.model.CompressionResult
import com.example.domain.model.PdfDocumentInfo
import com.example.domain.model.PdfError
import com.example.domain.model.ThemeMode
import com.example.domain.repository.CompressionHistoryRepository
import com.example.domain.repository.CompressionOutcome
import com.example.domain.repository.InspectionOutcome
import com.example.domain.repository.PdfCompressionRepository
import com.example.domain.repository.SaveOutcome
import com.example.domain.repository.SettingsRepository
import com.example.pdf.PdfInspector
import com.example.pdf.PdfStorageManager
import com.example.utils.FileSizeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class PdfCompressionRepositoryImpl(
    private val inspector: PdfInspector,
    private val engine: PdfCompressionEngine,
    private val storageManager: PdfStorageManager,
    private val historyRepository: CompressionHistoryRepository
) : PdfCompressionRepository {

    override suspend fun inspectPdf(uri: Uri): InspectionOutcome = inspector.inspect(uri)

    override suspend fun compressPdf(
        documentInfo: PdfDocumentInfo,
        config: CompressionConfig,
        onProgress: (CompressionProgress) -> Unit
    ): CompressionOutcome = engine.compress(documentInfo, config, onProgress)

    override suspend fun saveCompressedPdf(
        result: CompressionResult,
        destinationUri: Uri,
        desiredFileName: String
    ): SaveOutcome = withContext(Dispatchers.IO) {
        val tempFile = File(result.tempFilePath)
        if (!tempFile.exists() || tempFile.length() <= 0L) {
            return@withContext SaveOutcome.Error(
                PdfError.OutputSaveFailed("Temporary compressed file is no longer available.")
            )
        }

        val finalFileName = FileSizeFormatter.ensurePdfExtension(desiredFileName)
        var managedCopy: File? = null
        try {
            storageManager.copyFileToUri(tempFile, destinationUri)
            val verifiedSize = storageManager.verifyUriReadable(destinationUri)

            // Keep an app-managed copy for reliable History Open/Share without broad storage permissions
            managedCopy = storageManager.createManagedHistoryFile(finalFileName)
            tempFile.copyTo(managedCopy, overwrite = true)

            val historyId = historyRepository.insertRecord(
                CompressionHistoryItem(
                    originalFileName = result.originalInfo.displayName,
                    compressedFileName = finalFileName,
                    originalSizeBytes = result.originalInfo.sizeBytes,
                    compressedSizeBytes = result.compressedSizeBytes,
                    spaceSavedBytes = result.spaceSavedBytes,
                    compressionPercentage = result.compressionPercentage,
                    compressionMode = result.configUsed.preset.title,
                    pageCount = result.originalInfo.pageCount,
                    timestampMillis = System.currentTimeMillis(),
                    managedFilePath = managedCopy.absolutePath,
                    exportedUriString = destinationUri.toString()
                )
            )

            SaveOutcome.Success(
                savedUriString = destinationUri.toString(),
                savedFileName = finalFileName,
                verifiedSizeBytes = if (verifiedSize > 1L) verifiedSize else result.compressedSizeBytes,
                managedCopyPath = managedCopy.absolutePath,
                historyId = historyId
            )
        } catch (e: CancellationException) {
            managedCopy?.delete()
            throw e
        } catch (e: Exception) {
            managedCopy?.delete()
            SaveOutcome.Error(
                PdfError.OutputSaveFailed("Could not save compressed PDF: ${e.message ?: "Write error"}")
            )
        }
    }

    override suspend fun cleanupTempFile(path: String?) {
        withContext(Dispatchers.IO) {
            storageManager.deleteFileQuietly(path)
        }
    }

    override suspend fun clearWorkCache(): Long = withContext(Dispatchers.IO) {
        storageManager.clearWorkCache()
    }

    override fun getCacheSizeBytes(): Long = storageManager.getCacheSizeBytes()
}

class CompressionHistoryRepositoryImpl(
    private val dao: CompressionHistoryDao,
    private val storageManager: PdfStorageManager
) : CompressionHistoryRepository {

    override fun observeAllHistory(): Flow<List<CompressionHistoryItem>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeRecentHistory(limit: Int): Flow<List<CompressionHistoryItem>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun insertRecord(item: CompressionHistoryItem): Long =
        dao.insert(item.toEntity())

    override suspend fun deleteRecord(item: CompressionHistoryItem) {
        withContext(Dispatchers.IO) {
            storageManager.deleteFileQuietly(item.managedFilePath)
            dao.deleteById(item.id)
        }
    }

    override suspend fun clearAllHistory() {
        withContext(Dispatchers.IO) {
            val all = dao.getAllOnce()
            all.forEach { storageManager.deleteFileQuietly(it.managedFilePath) }
            dao.deleteAll()
        }
    }

    private fun CompressionHistoryEntity.toDomain() = CompressionHistoryItem(
        id = id,
        originalFileName = originalFileName,
        compressedFileName = compressedFileName,
        originalSizeBytes = originalSizeBytes,
        compressedSizeBytes = compressedSizeBytes,
        spaceSavedBytes = spaceSavedBytes,
        compressionPercentage = compressionPercentage,
        compressionMode = compressionMode,
        pageCount = pageCount,
        timestampMillis = timestampMillis,
        managedFilePath = managedFilePath,
        exportedUriString = exportedUriString
    )

    private fun CompressionHistoryItem.toEntity() = CompressionHistoryEntity(
        id = id,
        originalFileName = originalFileName,
        compressedFileName = compressedFileName,
        originalSizeBytes = originalSizeBytes,
        compressedSizeBytes = compressedSizeBytes,
        spaceSavedBytes = spaceSavedBytes,
        compressionPercentage = compressionPercentage,
        compressionMode = compressionMode,
        pageCount = pageCount,
        timestampMillis = timestampMillis,
        managedFilePath = managedFilePath,
        exportedUriString = exportedUriString
    )
}

class SettingsRepositoryImpl(
    private val dataStore: PreferencesDataStore
) : SettingsRepository {
    override val themeMode: Flow<ThemeMode> = dataStore.themeMode
    override val defaultPreset: Flow<CompressionPreset> = dataStore.defaultPreset

    override suspend fun setThemeMode(mode: ThemeMode) = dataStore.setThemeMode(mode)
    override suspend fun setDefaultPreset(preset: CompressionPreset) = dataStore.setDefaultPreset(preset)
}
