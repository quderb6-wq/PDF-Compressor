package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "compression_history")
data class CompressionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
