package com.example.data.repository

import com.example.data.dao.BookmarkDao
import com.example.data.dao.BrowserHistoryDao
import com.example.data.dao.DownloadDao
import com.example.data.dao.PlayerSettingsDao
import com.example.data.dao.SavedLinkDao
import com.example.data.dao.WatchHistoryDao
import com.example.data.model.BookmarkEntity
import com.example.data.model.BrowserHistoryEntity
import com.example.data.model.DownloadItemEntity
import com.example.data.model.PlayerSettingsEntity
import com.example.data.model.SavedLinkEntity
import com.example.data.model.WatchHistoryEntity
import com.example.util.MediaTitleHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MediaPlayerRepository(
    private val watchHistoryDao: WatchHistoryDao,
    private val savedLinkDao: SavedLinkDao,
    private val playerSettingsDao: PlayerSettingsDao,
    private val bookmarkDao: BookmarkDao,
    private val browserHistoryDao: BrowserHistoryDao,
    private val downloadDao: DownloadDao
) {
    val historyList: Flow<List<WatchHistoryEntity>> = watchHistoryDao.getAllHistory()
    val savedLinksList: Flow<List<SavedLinkEntity>> = savedLinkDao.getAllLinks()
    val bookmarksList: Flow<List<BookmarkEntity>> = bookmarkDao.getAllBookmarks()
    val browserHistoryList: Flow<List<BrowserHistoryEntity>> = browserHistoryDao.getAllHistory()
    val downloadsList: Flow<List<DownloadItemEntity>> = downloadDao.getAllDownloads()
    val settingsFlow: Flow<PlayerSettingsEntity> = playerSettingsDao.getSettingsFlow()
        .map { it ?: PlayerSettingsEntity() }

    init {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                watchHistoryDao.deleteLiveStreams()
            } catch (_: Exception) {}
        }
    }
        
    suspend fun getSettings(): PlayerSettingsEntity {
        return playerSettingsDao.getSettings() ?: PlayerSettingsEntity()
    }

    suspend fun saveOrUpdateWatchPosition(
        title: String,
        url: String,
        posterUrl: String?,
        positionMs: Long,
        durationMs: Long,
        streamType: String = "VIDEO"
    ) {
        if (url.isBlank() || streamType == "M3U_STREAM" || streamType == "LIVE") return
        val allHistory = watchHistoryDao.getAllHistoryList()
        val effectiveTitle = title.ifBlank { url.substringAfterLast("/").substringBefore("?") }

        // Find existing history record for the same video/episode (regardless of resolution / quality)
        val matchingList = allHistory.filter { existing ->
            MediaTitleHelper.isSameMedia(
                title1 = effectiveTitle,
                url1 = url,
                title2 = existing.title,
                url2 = existing.url
            )
        }

        val primaryMatch = matchingList.maxByOrNull { it.lastWatchedTimestamp }

        val entity = WatchHistoryEntity(
            id = primaryMatch?.id ?: 0L,
            title = effectiveTitle,
            url = url,
            posterUrl = posterUrl ?: primaryMatch?.posterUrl,
            lastPositionMs = positionMs,
            durationMs = if (durationMs > 0) durationMs else (primaryMatch?.durationMs ?: 0L),
            lastWatchedTimestamp = System.currentTimeMillis(),
            streamType = streamType
        )
        watchHistoryDao.insertOrUpdate(entity)

        // Delete any secondary duplicate rows in the database for the same episode/media
        matchingList.forEach { match ->
            if (primaryMatch != null && match.id != primaryMatch.id && match.id != 0L) {
                watchHistoryDao.deleteById(match.id)
            }
        }
    }

    suspend fun deleteHistoryItem(id: Long) {
        watchHistoryDao.deleteById(id)
    }

    suspend fun clearHistory() {
        watchHistoryDao.clearAll()
    }

    suspend fun addSavedLink(title: String, url: String, category: String, notes: String? = null) {
        val link = SavedLinkEntity(
            title = title.ifBlank { "Yayın/Video Linki" },
            url = url,
            category = category,
            notes = notes
        )
        savedLinkDao.insertLink(link)
    }

    suspend fun deleteSavedLink(id: Long) {
        savedLinkDao.deleteById(id)
    }

    suspend fun clearSavedLinks() {
        savedLinkDao.clearAll()
    }

    suspend fun updateSettings(settings: PlayerSettingsEntity) {
        playerSettingsDao.saveSettings(settings)
    }

    suspend fun addBookmark(title: String, url: String, imageUrl: String) {
        val bookmark = BookmarkEntity(
            title = title,
            url = url,
            imageUrl = imageUrl
        )
        bookmarkDao.insertBookmark(bookmark)
    }

    suspend fun updateBookmark(bookmark: BookmarkEntity) {
        bookmarkDao.updateBookmark(bookmark)
    }

    suspend fun deleteBookmark(bookmark: BookmarkEntity) {
        bookmarkDao.deleteBookmark(bookmark)
    }

    suspend fun addBrowserHistory(title: String, url: String) {
        if (url.isBlank() || url == "about:blank" || url.startsWith("data:") || url.startsWith("javascript:")) return
        try {
            browserHistoryDao.deleteByUrl(url)
            val history = BrowserHistoryEntity(
                title = title.ifBlank { url },
                url = url,
                timestamp = System.currentTimeMillis()
            )
            browserHistoryDao.insertHistory(history)
        } catch (_: Exception) {}
    }

    suspend fun deleteBrowserHistory(history: BrowserHistoryEntity) {
        try {
            browserHistoryDao.deleteHistory(history)
        } catch (_: Exception) {}
    }

    suspend fun clearBrowserHistory() {
        browserHistoryDao.clearHistory()
    }

    suspend fun insertDownload(download: DownloadItemEntity): Long {
        return downloadDao.insertDownload(download)
    }

    suspend fun updateDownload(download: DownloadItemEntity) {
        downloadDao.updateDownload(download)
    }

    suspend fun updateDownloadProgress(
        id: Long,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speed: String,
        status: String,
        localPath: String,
        errorMessage: String? = null
    ) {
        downloadDao.updateProgress(id, progress, downloadedBytes, totalBytes, speed, status, localPath, errorMessage)
    }

    suspend fun deleteDownload(id: Long) {
        downloadDao.deleteDownloadById(id)
    }

    suspend fun clearAllDownloads() {
        downloadDao.clearAllDownloads()
    }

    suspend fun getDownloadById(id: Long): DownloadItemEntity? {
        return downloadDao.getDownloadById(id)
    }
}
