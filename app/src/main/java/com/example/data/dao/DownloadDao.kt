package com.example.data.dao

import androidx.room.*
import com.example.data.model.DownloadItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadItemEntity>>

    @Query("SELECT * FROM downloads")
    suspend fun getAllDownloadsList(): List<DownloadItemEntity>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt DESC")
    fun getDownloadsByStatus(status: String): Flow<List<DownloadItemEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: Long): DownloadItemEntity?

    @Query("SELECT * FROM downloads WHERE url = :url LIMIT 1")
    suspend fun getDownloadByUrl(url: String): DownloadItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadItemEntity): Long

    @Update
    suspend fun updateDownload(download: DownloadItemEntity)

    @Query("""
        UPDATE downloads 
        SET progress = :progress, 
            downloadedBytes = :downloadedBytes, 
            totalBytes = :totalBytes, 
            speed = :speed, 
            status = :status,
            localPath = :localPath,
            errorMessage = :errorMessage
        WHERE id = :id
    """)
    suspend fun updateProgress(
        id: Long,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speed: String,
        status: String,
        localPath: String,
        errorMessage: String? = null
    )

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: Long)

    @Query("DELETE FROM downloads")
    suspend fun clearAllDownloads()
}
