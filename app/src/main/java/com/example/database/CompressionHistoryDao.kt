package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CompressionHistoryDao {
    @Query("SELECT * FROM compression_history ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<CompressionHistoryEntity>>

    @Query("SELECT * FROM compression_history ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<CompressionHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CompressionHistoryEntity): Long

    @Query("DELETE FROM compression_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM compression_history")
    suspend fun getAllOnce(): List<CompressionHistoryEntity>

    @Query("DELETE FROM compression_history")
    suspend fun deleteAll()
}
