package com.example.domain.model

import kotlinx.serialization.Serializable

enum class ThemeMode(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class CompressionPreset(
    val title: String,
    val subtitle: String,
    val badge: String
) {
    EXTREME(
        title = "Extreme Compression",
        subtitle = "Maximum size reduction by aggressively downsampling images and stripping redundant metadata. Image sharpness may noticeably decrease.",
        badge = "Max Savings"
    ),
    RECOMMENDED(
        title = "Recommended Compression",
        subtitle = "Optimal balance of smaller file size and crisp readability. Preserves searchable text, vectors, and clean image detail.",
        badge = "Balanced"
    ),
    LOW(
        title = "Low Compression",
        subtitle = "Prioritizes original visual fidelity with gentle image optimization while keeping documents sharp for printing.",
        badge = "High Quality"
    ),
    CUSTOM(
        title = "Custom Compression",
        subtitle = "Fine-tune JPEG quality, target image resolution, compression aggressiveness, and color or grayscale optimization.",
        badge = "Manual"
    )
}

enum class CompressionLevel(val label: String) {
    BALANCED("Balanced"),
    AGGRESSIVE("Aggressive"),
    MAXIMUM("Maximum")
}

enum class ResolutionOption(val label: String, val maxDimensionPx: Int) {
    DPI_72("72 DPI (Screen)", 900),
    DPI_110("110 DPI (Web)", 1250),
    DPI_150("150 DPI (eBook)", 1650),
    DPI_220("220 DPI (Print)", 2300),
    ORIGINAL("Original", 0)
}

data class CompressionConfig(
    val preset: CompressionPreset = CompressionPreset.RECOMMENDED,
    val imageQuality: Int = 72,
    val resolutionOption: ResolutionOption = ResolutionOption.DPI_150,
    val compressionLevel: CompressionLevel = CompressionLevel.BALANCED,
    val optimizeColorImages: Boolean = true,
    val grayscaleImages: Boolean = false,
    val stripMetadata: Boolean = true,
    val compressStreams: Boolean = true
) {
    companion object {
        fun forPreset(preset: CompressionPreset): CompressionConfig = when (preset) {
            CompressionPreset.EXTREME -> CompressionConfig(
                preset = CompressionPreset.EXTREME,
                imageQuality = 38,
                resolutionOption = ResolutionOption.DPI_72,
                compressionLevel = CompressionLevel.MAXIMUM,
                optimizeColorImages = true,
                grayscaleImages = false,
                stripMetadata = true,
                compressStreams = true
            )
            CompressionPreset.RECOMMENDED -> CompressionConfig(
                preset = CompressionPreset.RECOMMENDED,
                imageQuality = 72,
                resolutionOption = ResolutionOption.DPI_150,
                compressionLevel = CompressionLevel.BALANCED,
                optimizeColorImages = true,
                grayscaleImages = false,
                stripMetadata = true,
                compressStreams = true
            )
            CompressionPreset.LOW -> CompressionConfig(
                preset = CompressionPreset.LOW,
                imageQuality = 88,
                resolutionOption = ResolutionOption.DPI_220,
                compressionLevel = CompressionLevel.BALANCED,
                optimizeColorImages = true,
                grayscaleImages = false,
                stripMetadata = false,
                compressStreams = true
            )
            CompressionPreset.CUSTOM -> CompressionConfig(
                preset = CompressionPreset.CUSTOM,
                imageQuality = 70,
                resolutionOption = ResolutionOption.DPI_150,
                compressionLevel = CompressionLevel.BALANCED,
                optimizeColorImages = true,
                grayscaleImages = false,
                stripMetadata = true,
                compressStreams = true
            )
        }
    }
}

data class PdfDocumentInfo(
    val uriString: String,
    val displayName: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val title: String? = null,
    val author: String? = null,
    val embeddedImageCount: Int = 0,
    val thumbnailPath: String? = null
)

enum class CompressionStage(val message: String) {
    PREPARING("Preparing document workspace..."),
    LOADING("Reading PDF structure..."),
    OPTIMIZING_PAGES("Optimizing pages & embedded images..."),
    OPTIMIZING_STREAMS("Compressing streams & cleaning metadata..."),
    SAVING("Writing optimized PDF..."),
    VERIFYING("Verifying output integrity...")
}

data class CompressionProgress(
    val stage: CompressionStage = CompressionStage.PREPARING,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val imagesOptimized: Int = 0,
    val progressFraction: Float = 0f
)

enum class CompressionStatus {
    SUCCESS,
    LARGER_THAN_ORIGINAL,
    FAILED,
    CANCELLED
}

data class CompressionResult(
    val id: String,
    val tempFilePath: String,
    val originalInfo: PdfDocumentInfo,
    val suggestedOutputName: String,
    val compressedSizeBytes: Long,
    val spaceSavedBytes: Long,
    val compressionPercentage: Double,
    val isSmaller: Boolean,
    val configUsed: CompressionConfig,
    val imagesOptimizedCount: Int,
    val durationMs: Long,
    val status: CompressionStatus,
    val savedUriString: String? = null,
    val savedDisplayName: String? = null,
    val managedCopyPath: String? = null,
    val historyRecordId: Long? = null
)

data class CompressionHistoryItem(
    val id: Long = 0,
    val originalFileName: String,
    val compressedFileName: String,
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long,
    val spaceSavedBytes: Long,
    val compressionPercentage: Double,
    val compressionMode: String,
    val pageCount: Int,
    val timestampMillis: Long,
    val managedFilePath: String?,
    val exportedUriString: String?
)

sealed class PdfError(val userMessage: String) {
    data object NoPdfSelected : PdfError("Please select a PDF document first.")
    data object UnsupportedFormat : PdfError("The selected file is not a valid PDF document.")
    data object PasswordProtected : PdfError("This PDF is password-protected. Please unlock it first before compressing.")
    data object CorruptedOrUnsupported : PdfError("Unable to read this PDF safely. The file may be corrupted or use an unsupported structure.")
    data object InsufficientStorage : PdfError("Not enough free internal storage to process this PDF safely.")
    data object TooLargeOrOutOfMemory : PdfError("This document exceeds available processing memory. Try Low compression or free up device memory.")
    data object Cancelled : PdfError("Compression was cancelled.")
    data class OutputSaveFailed(val detail: String? = null) : PdfError(
        detail ?: "Could not save the compressed PDF to the chosen location."
    )
    data class CompressionFailed(val detail: String? = null) : PdfError(
        detail ?: "Compression could not be completed safely without risking document integrity."
    )
}
