package com.example.domain.usecase

import android.net.Uri
import com.example.domain.model.CompressionConfig
import com.example.domain.model.CompressionProgress
import com.example.domain.model.CompressionResult
import com.example.domain.model.PdfDocumentInfo
import com.example.domain.repository.CompressionOutcome
import com.example.domain.repository.InspectionOutcome
import com.example.domain.repository.PdfCompressionRepository
import com.example.domain.repository.SaveOutcome

class InspectPdfUseCase(
    private val repository: PdfCompressionRepository
) {
    suspend operator fun invoke(uri: Uri): InspectionOutcome = repository.inspectPdf(uri)
}

class CompressPdfUseCase(
    private val repository: PdfCompressionRepository
) {
    suspend operator fun invoke(
        documentInfo: PdfDocumentInfo,
        config: CompressionConfig,
        onProgress: (CompressionProgress) -> Unit
    ): CompressionOutcome = repository.compressPdf(documentInfo, config, onProgress)
}

class SaveCompressedPdfUseCase(
    private val repository: PdfCompressionRepository
) {
    suspend operator fun invoke(
        result: CompressionResult,
        destinationUri: Uri,
        desiredFileName: String
    ): SaveOutcome = repository.saveCompressedPdf(result, destinationUri, desiredFileName)
}
