package com.example.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ShareAndOpenHelper {

    fun resolveAccessibleUri(
        context: Context,
        exportedUriString: String?,
        managedFilePath: String?
    ): Uri? {
        if (!managedFilePath.isNullOrBlank()) {
            val file = File(managedFilePath)
            if (file.exists() && file.length() > 0L) {
                return FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
        }
        if (!exportedUriString.isNullOrBlank()) {
            return runCatching { Uri.parse(exportedUriString) }.getOrNull()
        }
        return null
    }

    fun openPdf(
        context: Context,
        uri: Uri
    ): Result<Unit> {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open PDF with"))
            Result.success(Unit)
        } catch (e: ActivityNotFoundException) {
            Result.failure(IllegalStateException("No PDF viewer app is installed on this device.", e))
        } catch (e: SecurityException) {
            Result.failure(IllegalStateException("Permission to access this external PDF expired. Open the saved copy from your file manager.", e))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Unable to open PDF: ${e.message}", e))
        }
    }

    fun sharePdf(
        context: Context,
        uri: Uri,
        displayName: String
    ): Result<Unit> {
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, displayName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share compressed PDF").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(IllegalStateException("Permission to access this external PDF expired.", e))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Unable to share PDF: ${e.message}", e))
        }
    }
}
