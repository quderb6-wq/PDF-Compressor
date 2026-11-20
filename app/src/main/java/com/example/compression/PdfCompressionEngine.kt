package com.example.compression

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import com.example.domain.model.CompressionConfig
import com.example.domain.model.CompressionLevel
import com.example.domain.model.CompressionProgress
import com.example.domain.model.CompressionResult
import com.example.domain.model.CompressionStage
import com.example.domain.model.CompressionStatus
import com.example.domain.model.PdfDocumentInfo
import com.example.domain.model.PdfError
import com.example.domain.repository.CompressionOutcome
import com.example.pdf.PdfStorageManager
import com.example.utils.FileSizeFormatter
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

class PdfCompressionEngine(
    private val context: Context,
    private val storageManager: PdfStorageManager
) {
    suspend fun compress(
        documentInfo: PdfDocumentInfo,
        config: CompressionConfig,
        onProgress: (CompressionProgress) -> Unit
    ): CompressionOutcome = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        PDFBoxResourceLoader.init(context.applicationContext)

        val requiredScratch = (documentInfo.sizeBytes * 2.5).toLong().coerceAtLeast(16L * 1024L * 1024L)
        if (!storageManager.hasSufficientStorage(requiredScratch)) {
            return@withContext CompressionOutcome.Error(PdfError.InsufficientStorage)
        }

        val sourceFile = storageManager.createWorkFile("src")
        val outputFile = storageManager.createWorkFile("out")

        try {
            onProgress(
                CompressionProgress(
                    stage = CompressionStage.PREPARING,
                    totalPages = documentInfo.pageCount,
                    progressFraction = 0.05f
                )
            )
            val sourceUri = Uri.parse(documentInfo.uriString)
            val actualOriginalSize = storageManager.copyUriToFile(sourceUri, sourceFile)
                .takeIf { it > 0L } ?: documentInfo.sizeBytes

            currentCoroutineContext().ensureActive()
            onProgress(
                CompressionProgress(
                    stage = CompressionStage.LOADING,
                    totalPages = documentInfo.pageCount,
                    progressFraction = 0.12f
                )
            )

            val totalPages: Int
            var imagesOptimizedCount = 0

            PDDocument.load(sourceFile, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                if (document.isEncrypted) {
                    outputFile.delete()
                    return@withContext CompressionOutcome.Error(PdfError.PasswordProtected)
                }

                totalPages = document.numberOfPages
                if (totalPages <= 0) {
                    outputFile.delete()
                    return@withContext CompressionOutcome.Error(PdfError.CorruptedOrUnsupported)
                }

                val processedImages = HashMap<COSBase, PDImageXObject?>()
                val visitedForms = HashSet<COSBase>()

                for (pageIndex in 0 until totalPages) {
                    currentCoroutineContext().ensureActive()
                    val page: PDPage = document.getPage(pageIndex)

                    onProgress(
                        CompressionProgress(
                            stage = CompressionStage.OPTIMIZING_PAGES,
                            currentPage = pageIndex + 1,
                            totalPages = totalPages,
                            imagesOptimized = imagesOptimizedCount,
                            progressFraction = 0.15f + (0.65f * ((pageIndex + 1).toFloat() / totalPages.toFloat()))
                        )
                    )

                    val pageOptimized = optimizeResources(
                        document = document,
                        resources = page.resources,
                        config = config,
                        processedImages = processedImages,
                        visitedForms = visitedForms
                    )
                    imagesOptimizedCount += pageOptimized

                    if (config.compressStreams) {
                        compressPageContentStreams(page)
                    }

                    if (config.stripMetadata && config.compressionLevel == CompressionLevel.MAXIMUM) {
                        page.cosObject.removeItem(COSName.THUMB)
                    }
                }

                currentCoroutineContext().ensureActive()
                onProgress(
                    CompressionProgress(
                        stage = CompressionStage.OPTIMIZING_STREAMS,
                        currentPage = totalPages,
                        totalPages = totalPages,
                        imagesOptimized = imagesOptimizedCount,
                        progressFraction = 0.85f
                    )
                )

                if (config.stripMetadata) {
                    stripRedundantMetadata(document, config.compressionLevel == CompressionLevel.MAXIMUM)
                }

                currentCoroutineContext().ensureActive()
                onProgress(
                    CompressionProgress(
                        stage = CompressionStage.SAVING,
                        currentPage = totalPages,
                        totalPages = totalPages,
                        imagesOptimized = imagesOptimizedCount,
                        progressFraction = 0.92f
                    )
                )

                document.save(outputFile)
            }

            currentCoroutineContext().ensureActive()
            onProgress(
                CompressionProgress(
                    stage = CompressionStage.VERIFYING,
                    currentPage = totalPages,
                    totalPages = totalPages,
                    imagesOptimized = imagesOptimizedCount,
                    progressFraction = 0.97f
                )
            )

            if (!outputFile.exists() || outputFile.length() <= 0L) {
                outputFile.delete()
                return@withContext CompressionOutcome.Error(
                    PdfError.CompressionFailed("Compressed output file could not be generated.")
                )
            }

            // Integrity check: verify output PDF reopens cleanly with intact page count
            val verifiedPageCount = PDDocument.load(outputFile, MemoryUsageSetting.setupTempFileOnly()).use { verified ->
                verified.numberOfPages
            }
            if (verifiedPageCount != totalPages) {
                outputFile.delete()
                return@withContext CompressionOutcome.Error(
                    PdfError.CompressionFailed("Output integrity verification failed (page count mismatch).")
                )
            }

            val compressedSize = outputFile.length()
            val spaceSaved = actualOriginalSize - compressedSize
            val percentage = if (actualOriginalSize > 0L) {
                ((actualOriginalSize - compressedSize).toDouble() / actualOriginalSize.toDouble()) * 100.0
            } else {
                0.0
            }
            val isSmaller = compressedSize < actualOriginalSize

            CompressionOutcome.Success(
                CompressionResult(
                    id = UUID.randomUUID().toString(),
                    tempFilePath = outputFile.absolutePath,
                    originalInfo = documentInfo.copy(
                        sizeBytes = actualOriginalSize,
                        pageCount = totalPages
                    ),
                    suggestedOutputName = FileSizeFormatter.buildDefaultCompressedFileName(documentInfo.displayName),
                    compressedSizeBytes = compressedSize,
                    spaceSavedBytes = spaceSaved,
                    compressionPercentage = percentage,
                    isSmaller = isSmaller,
                    configUsed = config,
                    imagesOptimizedCount = imagesOptimizedCount,
                    durationMs = System.currentTimeMillis() - startTime,
                    status = if (isSmaller) CompressionStatus.SUCCESS else CompressionStatus.LARGER_THAN_ORIGINAL
                )
            )
        } catch (e: CancellationException) {
            outputFile.delete()
            throw e
        } catch (_: InvalidPasswordException) {
            outputFile.delete()
            CompressionOutcome.Error(PdfError.PasswordProtected)
        } catch (_: IOException) {
            outputFile.delete()
            CompressionOutcome.Error(PdfError.CorruptedOrUnsupported)
        } catch (_: OutOfMemoryError) {
            outputFile.delete()
            CompressionOutcome.Error(PdfError.TooLargeOrOutOfMemory)
        } catch (e: Exception) {
            outputFile.delete()
            CompressionOutcome.Error(
                PdfError.CompressionFailed("Could not safely optimize PDF: ${e.message ?: "Unexpected error"}")
            )
        } finally {
            sourceFile.delete()
        }
    }

    private suspend fun optimizeResources(
        document: PDDocument,
        resources: PDResources?,
        config: CompressionConfig,
        processedImages: MutableMap<COSBase, PDImageXObject?>,
        visitedForms: MutableSet<COSBase>
    ): Int {
        if (resources == null) return 0
        var count = 0
        val names = resources.xObjectNames.toList()
        for (xObjName in names) {
            currentCoroutineContext().ensureActive()
            val xObj = runCatching { resources.getXObject(xObjName) }.getOrNull() ?: continue
            when (xObj) {
                is PDImageXObject -> {
                    val key = xObj.cosObject
                    if (processedImages.containsKey(key)) {
                        processedImages[key]?.let { cached ->
                            resources.put(xObjName, cached)
                        }
                    } else {
                        val optimized = optimizeImageXObject(document, xObj, config)
                        processedImages[key] = optimized
                        if (optimized != null) {
                            resources.put(xObjName, optimized)
                            count++
                        }
                    }
                }
                is PDFormXObject -> {
                    if (visitedForms.add(xObj.cosObject)) {
                        count += optimizeResources(
                            document = document,
                            resources = xObj.resources,
                            config = config,
                            processedImages = processedImages,
                            visitedForms = visitedForms
                        )
                    }
                }
            }
        }
        return count
    }

    private fun optimizeImageXObject(
        document: PDDocument,
        image: PDImageXObject,
        config: CompressionConfig
    ): PDImageXObject? {
        if (image.isStencil) return null
        if (!config.optimizeColorImages && !config.grayscaleImages) return null

        val origStreamBytes = image.cosObject.length
        val origWidth = image.width
        val origHeight = image.height
        if (origWidth <= 0 || origHeight <= 0) return null

        // Preserve tiny UI icons/sprites unless grayscale conversion is requested
        if (!config.grayscaleImages && (origWidth < 48 && origHeight < 48 || origStreamBytes < 3_072L)) {
            return null
        }

        // Guard against extreme multi-megapixel bitmap allocations on mobile heap
        if (origWidth.toLong() * origHeight.toLong() > 45_000_000L) {
            return null
        }

        val hasSoftMask = image.cosObject.containsKey(COSName.SMASK) ||
            runCatching { image.softMask != null }.getOrDefault(false)

        val maxDim = config.resolutionOption.maxDimensionPx
        val needsScale = maxDim > 0 && (origWidth > maxDim || origHeight > maxDim)
        val (targetWidth, targetHeight) = if (needsScale) {
            val scale = maxDim.toFloat() / max(origWidth, origHeight).toFloat()
            (origWidth * scale).roundToInt().coerceAtLeast(1) to
                (origHeight * scale).roundToInt().coerceAtLeast(1)
        } else {
            origWidth to origHeight
        }

        var rawBitmap: Bitmap? = null
        var workingBitmap: Bitmap? = null
        var finalBitmap: Bitmap? = null

        return try {
            rawBitmap = image.image ?: return null

            workingBitmap = if (targetWidth != rawBitmap.width || targetHeight != rawBitmap.height) {
                Bitmap.createScaledBitmap(rawBitmap, targetWidth, targetHeight, true)
            } else {
                rawBitmap
            }

            if (hasSoftMask && config.compressionLevel != CompressionLevel.MAXIMUM) {
                // Preserve alpha channel safely for logos/signatures/transparent graphics
                finalBitmap = if (config.grayscaleImages) {
                    toGrayscaleBitmap(workingBitmap, preserveAlpha = true)
                } else {
                    workingBitmap
                }
                val candidate = LosslessFactory.createFromImage(document, finalBitmap)
                if (isReplacementWorthwhile(candidate.cosObject.length, origStreamBytes, config.grayscaleImages)) {
                    candidate
                } else {
                    null
                }
            } else {
                // Composite onto white so any transparent edges never turn black in JPEG
                finalBitmap = prepareForJpeg(workingBitmap, config.grayscaleImages)
                val quality = when (config.compressionLevel) {
                    CompressionLevel.BALANCED -> config.imageQuality
                    CompressionLevel.AGGRESSIVE -> (config.imageQuality - 8).coerceAtLeast(15)
                    CompressionLevel.MAXIMUM -> (config.imageQuality - 15).coerceAtLeast(12)
                }.coerceIn(10, 95) / 100f

                val candidate = JPEGFactory.createFromImage(document, finalBitmap, quality)
                if (isReplacementWorthwhile(candidate.cosObject.length, origStreamBytes, config.grayscaleImages)) {
                    candidate
                } else {
                    null
                }
            }
        } catch (_: Throwable) {
            null
        } finally {
            if (finalBitmap != null && finalBitmap !== workingBitmap && finalBitmap !== rawBitmap) {
                finalBitmap.recycle()
            }
            if (workingBitmap != null && workingBitmap !== rawBitmap) {
                workingBitmap.recycle()
            }
            rawBitmap?.recycle()
        }
    }

    private fun prepareForJpeg(source: Bitmap, grayscale: Boolean): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val paint = if (grayscale) {
            Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            }
        } else {
            Paint(Paint.FILTER_BITMAP_FLAG)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    private fun toGrayscaleBitmap(source: Bitmap, preserveAlpha: Boolean): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (!preserveAlpha) {
            canvas.drawColor(Color.WHITE)
        }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    private fun isReplacementWorthwhile(
        newBytes: Long,
        originalBytes: Long,
        grayscaleRequested: Boolean
    ): Boolean {
        if (newBytes <= 0L) return false
        if (grayscaleRequested) {
            return newBytes <= (originalBytes * 1.05).toLong()
        }
        return newBytes < originalBytes
    }

    private fun compressPageContentStreams(page: PDPage) {
        val contents = page.contentStreams
        while (contents.hasNext()) {
            val pdStream: PDStream = contents.next()
            try {
                val filters = pdStream.filters
                if (filters.isNullOrEmpty()) {
                    val rawBytes = pdStream.createInputStream().use { it.readBytes() }
                    if (rawBytes.size > 128) {
                        pdStream.cosObject.createOutputStream(COSName.FLATE_DECODE).use { out ->
                            out.write(rawBytes)
                        }
                    }
                }
            } catch (_: Throwable) {
                // Leave stream untouched if re-encoding fails for any edge-case stream
            }
        }
    }

    private fun stripRedundantMetadata(document: PDDocument, aggressive: Boolean) {
        runCatching {
            val catalog = document.documentCatalog
            catalog.metadata = null
            catalog.cosObject.removeItem(COSName.PIECE_INFO)
            if (aggressive) {
                val info = document.documentInformation
                info.producer = null
                info.creator = null
                info.keywords = null
            }
        }
    }
}
