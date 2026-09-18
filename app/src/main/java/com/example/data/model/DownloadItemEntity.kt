package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val localPath: String = "",
    val posterUrl: String = "",
    val status: String = "QUEUED", // QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED
    val progress: Int = 0, // 0 - 100
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speed: String = "",
    val mimeType: String = "video/mp4",
    val downloadId: Long = -1L,
    val createdAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)
