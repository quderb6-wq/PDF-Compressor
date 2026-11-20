package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.domain.model.PdfDocumentInfo
import com.example.domain.model.PdfError
import com.example.domain.repository.InspectionOutcome
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class PdfInspector(
    private val context: Context,
    private val storageManager: PdfStorageManager
) {
    suspend fun inspect(uri: Uri): InspectionOutcome = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)

        val (queriedName, queriedSize) = storageManager.queryDisplayNameAndSize(uri)
        if (queriedSize > 0L && !storageManager.hasSufficientStorage(queriedSize * 2)) {
            return@withContext InspectionOutcome.Error(PdfError.InsufficientStorage)
        }

        val tempFile = storageManager.createWorkFile("inspect")
        try {
            val copiedBytes = storageManager.copyUriToFile(uri, tempFile)
            val actualSize = if (queriedSize > 0L) queriedSize else copiedBytes
            if (actualSize <= 0L || !hasPdfHeader(tempFile)) {
                return@withContext InspectionOutcome.Error(PdfError.UnsupportedFormat)
            }

            if (!storageManager.hasSufficientStorage(actualSize * 2)) {
                return@withContext InspectionOutcome.Error(PdfError.InsufficientStorage)
            }

            PDDocument.load(tempFile, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                if (document.isEncrypted) {
                    return@withContext InspectionOutcome.Error(PdfError.PasswordProtected)
                }
                val pageCount = document.numberOfPages
                if (pageCount <= 0) {
                    return@withContext InspectionOutcome.Error(PdfError.CorruptedOrUnsupported)
                }

                val imageCount = countUniqueImages(document)
                val info = document.documentInformation
                val thumbPath = renderFirstPageThumbnail(tempFile)

                InspectionOutcome.Success(
                    PdfDocumentInfo(
                        uriString = uri.toString(),
                        displayName = queriedName,
                        sizeBytes = actualSize,
                        pageCount = pageCount,
                        title = info?.title?.takeIf { it.isNotBlank() },
                        author = info?.author?.takeIf { it.isNotBlank() },
                        embeddedImageCount = imageCount,
                        thumbnailPath = thumbPath
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: InvalidPasswordException) {
            InspectionOutcome.Error(PdfError.PasswordProtected)
        } catch (_: IOException) {
            InspectionOutcome.Error(PdfError.CorruptedOrUnsupported)
        } catch (_: OutOfMemoryError) {
            InspectionOutcome.Error(PdfError.TooLargeOrOutOfMemory)
        } catch (e: Exception) {
            InspectionOutcome.Error(PdfError.CompressionFailed("Unable to inspect PDF: ${e.message ?: "Unknown error"}"))
        } finally {
            tempFile.delete()
        }
    }

    private fun hasPdfHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 5L) return false
        val headerBuf = ByteArray(1024)
        val read = FileInputStream(file).use { it.read(headerBuf) }
        if (read <= 0) return false
        val headerStr = String(headerBuf, 0, read, Charsets.ISO_8859_1)
        return headerStr.contains("%PDF-")
    }

    private fun countUniqueImages(document: PDDocument): Int {
        val seenImages = HashSet<COSBase>()
        val visitedForms = HashSet<COSBase>()

        fun scanResources(resources: PDResources?) {
            if (resources == null) return
            for (name in resources.xObjectNames) {
                val xObj = runCatching { resources.getXObject(name) }.getOrNull() ?: continue
                when (xObj) {
                    is PDImageXObject -> seenImages.add(xObj.cosObject)
                    is PDFormXObject -> {
                        if (visitedForms.add(xObj.cosObject)) {
                            scanResources(xObj.resources)
                        }
                    }
                }
            }
        }

        for (page in document.pages) {
            scanResources(page.resources)
        }
        return seenImages.size
    }

    private fun renderFirstPageThumbnail(pdfFile: File): String? {
        return try {
            val thumbFile = storageManager.createWorkFile("thumb", "png")
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount <= 0) return null
                    renderer.openPage(0).use { page ->
                        val targetWidth = 320
                        val ratio = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                        val targetHeight = (targetWidth * ratio).toInt().coerceIn(120, 480)
                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        try {
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            FileOutputStream(thumbFile).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                            }
                            thumbFile.absolutePath
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }
}
