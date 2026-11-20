package com.example.pdf

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

class PdfStorageManager(private val context: Context) {

    val workDir: File
        get() = File(context.cacheDir, "pdf_work").apply { mkdirs() }

    val managedHistoryDir: File
        get() = File(context.filesDir, "compressed_history").apply { mkdirs() }

    fun hasSufficientStorage(requiredBytes: Long): Boolean {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            if (stat.totalBytes <= 0L) return true
            stat.availableBytes >= requiredBytes.coerceAtLeast(12L * 1024L * 1024L)
        } catch (_: Exception) {
            true
        }
    }

    fun createWorkFile(prefix: String, extension: String = "pdf"): File {
        return File(workDir, "${prefix}_${UUID.randomUUID()}.$extension")
    }

    fun createManagedHistoryFile(preferredName: String): File {
        val safeName = preferredName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val unique = "${System.currentTimeMillis()}_$safeName"
        return File(managedHistoryDir, unique)
    }

    fun queryDisplayNameAndSize(uri: Uri): Pair<String, Long> {
        var name = "document.pdf"
        var size = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) {
                    cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { name = it }
                }
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    size = cursor.getLong(sizeIdx)
                }
            }
        }
        return name to size
    }

    suspend fun copyUriToFile(uri: Uri, destination: File): Long {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open selected document stream.")
        var totalBytes = 0L
        input.use { inStream ->
            FileOutputStream(destination).use { outStream ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = inStream.read(buffer)
                    if (read == -1) break
                    outStream.write(buffer, 0, read)
                    totalBytes += read
                }
                outStream.flush()
            }
        }
        return totalBytes
    }

    suspend fun copyFileToUri(source: File, destinationUri: Uri): Long {
        val outStream = context.contentResolver.openOutputStream(destinationUri, "wt")
            ?: context.contentResolver.openOutputStream(destinationUri)
            ?: throw IOException("Unable to open destination file for writing.")
        var written = 0L
        outStream.use { output ->
            FileInputStream(source).use { input ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    written += read
                }
                output.flush()
            }
        }
        return written
    }

    fun verifyUriReadable(uri: Uri): Long {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Saved file could not be reopened for verification.")
        return pfd.use { descriptor ->
            val size = descriptor.statSize
            if (size == 0L) {
                throw IOException("Saved PDF file is empty.")
            }
            if (size > 0L) size else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val buf = ByteArray(8)
                    val read = stream.read(buf)
                    if (read <= 0) throw IOException("Saved PDF cannot be read.")
                }
                1L
            }
        }
    }

    fun deleteFileQuietly(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    fun clearWorkCache(): Long {
        var freed = 0L
        workDir.listFiles()?.forEach { file ->
            freed += file.length()
            file.delete()
        }
        return freed
    }

    fun getCacheSizeBytes(): Long {
        return workDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
}
