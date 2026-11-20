package com.example.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object FileSizeFormatter {
    fun formatBytes(bytes: Long): String {
        val absBytes = abs(bytes)
        val prefix = if (bytes < 0) "-" else ""
        return when {
            absBytes < 1024L -> "$prefix$absBytes B"
            absBytes < 1024L * 1024L -> String.format(Locale.US, "%s%.1f KB", prefix, absBytes / 1024.0)
            absBytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%s%.2f MB", prefix, absBytes / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%s%.2f GB", prefix, absBytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun formatCompressionPercentage(percentage: Double): String {
        return String.format(Locale.US, "%.1f%%", percentage)
    }

    fun formatDuration(durationMs: Long): String {
        return when {
            durationMs < 1000L -> "${durationMs}ms"
            durationMs < 60_000L -> String.format(Locale.US, "%.1fs", durationMs / 1000.0)
            else -> {
                val mins = durationMs / 60_000L
                val secs = (durationMs % 60_000L) / 1000L
                "${mins}m ${secs}s"
            }
        }
    }

    fun formatDateTime(timestampMillis: Long): String {
        val formatter = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())
        return formatter.format(Date(timestampMillis))
    }

    fun buildDefaultCompressedFileName(originalName: String): String {
        val sanitized = originalName.trim().ifBlank { "document.pdf" }
        val base = if (sanitized.lowercase(Locale.US).endsWith(".pdf")) {
            sanitized.dropLast(4)
        } else {
            sanitized
        }
        val cleanBase = base.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "document" }
        return if (cleanBase.endsWith("_compressed")) {
            "$cleanBase.pdf"
        } else {
            "${cleanBase}_compressed.pdf"
        }
    }

    fun ensurePdfExtension(fileName: String): String {
        val trimmed = fileName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "compressed.pdf" }
        return if (trimmed.lowercase(Locale.US).endsWith(".pdf")) trimmed else "$trimmed.pdf"
    }
}
