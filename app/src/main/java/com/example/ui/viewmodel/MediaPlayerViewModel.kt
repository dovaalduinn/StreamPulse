package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.BookmarkEntity
import com.example.data.model.PlayerSettingsEntity
import com.example.data.model.SavedLinkEntity
import com.example.data.model.WatchHistoryEntity
import com.example.data.model.BrowserHistoryEntity
import com.example.data.model.DownloadItemEntity
import com.example.data.model.IptvPlaylistEntity
import com.example.data.repository.MediaPlayerRepository
import com.example.util.CustomDnsResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class SubtitleTrack(
    val url: String,
    val language: String,
    val label: String
)

data class ActiveVideoState(
    val title: String = "",
    val url: String = "",
    val audioUrl: String? = null,
    val posterUrl: String? = null,
    val startPositionMs: Long = 0L,
    val isLive: Boolean = false,
    val availableQualities: List<SniffedMedia> = emptyList(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    val referer: String? = null,
    val userAgent: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val isOffline: Boolean = false
)

data class SniffedMedia(
    val url: String,
    val title: String,
    val format: String = "Bilinmeyen",
    val quality: String = "Otomatik",
    val duration: String = "",
    val audioUrl: String? = null,
    val isMerged: Boolean = false,
    val referer: String? = null,
    val userAgent: String? = null,
    val headers: Map<String, String> = emptyMap()
)

enum class PlayerRemoteAction {
    PLAY_PAUSE,
    FORWARD_10S,
    REWIND_10S,
    TOGGLE_FULLSCREEN,
    CYCLE_SUBTITLE,
    CYCLE_AUDIO,
    TOGGLE_MUTE,
    NEXT_VIDEO,
    PREV_VIDEO,
    SHOW_CONTROLS
}

class MediaPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _isTvCursorActive = MutableStateFlow(false)
    val isTvCursorActive: StateFlow<Boolean> = _isTvCursorActive.asStateFlow()

    private val _isDragScrollMode = MutableStateFlow(false)
    val isDragScrollMode: StateFlow<Boolean> = _isDragScrollMode.asStateFlow()

    private val _cursorX = MutableStateFlow(640f)
    val cursorX: StateFlow<Float> = _cursorX.asStateFlow()

    private val _cursorY = MutableStateFlow(360f)
    val cursorY: StateFlow<Float> = _cursorY.asStateFlow()

    private val _cursorHudMessage = MutableStateFlow<String?>(null)
    val cursorHudMessage: StateFlow<String?> = _cursorHudMessage.asStateFlow()

    private val _isCursorClicking = MutableStateFlow(false)
    val isCursorClicking: StateFlow<Boolean> = _isCursorClicking.asStateFlow()

    private val _playerActionFlow = MutableSharedFlow<PlayerRemoteAction>(extraBufferCapacity = 16)
    val playerActionFlow: SharedFlow<PlayerRemoteAction> = _playerActionFlow.asSharedFlow()

    private val db = AppDatabase.getDatabase(application)
    private val repository = MediaPlayerRepository(
        watchHistoryDao = db.watchHistoryDao(),
        savedLinkDao = db.savedLinkDao(),
        playerSettingsDao = db.playerSettingsDao(),
        bookmarkDao = db.bookmarkDao(),
        browserHistoryDao = db.browserHistoryDao(),
        downloadDao = db.downloadDao()
    )

    val historyList: StateFlow<List<WatchHistoryEntity>> = repository.historyList
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val savedLinks: StateFlow<List<SavedLinkEntity>> = repository.savedLinksList
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val bookmarks: StateFlow<List<BookmarkEntity>> = repository.bookmarksList
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val settings: StateFlow<PlayerSettingsEntity> = repository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlayerSettingsEntity()
        )

    val browserHistory: StateFlow<List<BrowserHistoryEntity>> = repository.browserHistoryList
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val downloads: StateFlow<List<DownloadItemEntity>> = repository.downloadsList
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _activeVideo = MutableStateFlow<ActiveVideoState?>(null)
    val activeVideo: StateFlow<ActiveVideoState?> = _activeVideo.asStateFlow()

    private var lastSavedHistoryPositionMs: Long = -1L
    private var lastSavedHistoryTimestamp: Long = 0L

    val streamSnifferManager = com.example.sniffer.StreamSnifferManager()

    private val _sniffedLinks = MutableStateFlow<List<SniffedMedia>>(emptyList())
    val sniffedLinks: StateFlow<List<SniffedMedia>> = _sniffedLinks.asStateFlow()

    private val _sniffedSubtitles = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val sniffedSubtitles: StateFlow<List<SubtitleTrack>> = _sniffedSubtitles

    init {
        viewModelScope.launch {
            streamSnifferManager.candidates.collect { candidates ->
                val links = mutableListOf<SniffedMedia>()
                val subs = mutableListOf<SubtitleTrack>()
                for (c in candidates) {
                    if (c.type == "Subtitle") {
                        subs.add(SubtitleTrack(c.url, c.language ?: "Unknown", c.title ?: "Subtitle"))
                    } else {
                        links.add(SniffedMedia(
                            url = c.url,
                            title = c.title.takeIf { !it.isNullOrBlank() } ?: "Yakalanan Medya",
                            format = "${c.type} (${c.confidence})",
                            quality = c.quality ?: "Otomatik",
                            duration = c.durationStr ?: "",
                            referer = c.referer,
                            userAgent = c.userAgent,
                            headers = c.headers
                        ))
                    }
                }
                _sniffedLinks.value = links
                _sniffedSubtitles.value = subs
            }
        }

        // Configure Custom DNS from database settings
        viewModelScope.launch {
            settings.collect { s ->
                CustomDnsResolver.configure(s.useCustomDns, s.customDnsUrl)
            }
        }
    }

    fun playVideo(
        title: String,
        url: String,
        posterUrl: String? = null,
        startPositionMs: Long = 0L,
        isOffline: Boolean = false
    ) {
        val isM3u = url.endsWith(".m3u8") || url.contains("m3u8") || url.contains("live")
        val safeTitle = title.ifBlank { url.substringAfterLast("/").substringBefore("?") }
        
        val clickedLink = _sniffedLinks.value.find { it.url == url }
        val targetDuration = clickedLink?.duration ?: ""

        val qualities = _sniffedLinks.value.filter { 
            (it.title == safeTitle || it.title == title) &&
            (targetDuration.isBlank() || it.duration.isBlank() || it.duration == targetDuration)
        }.distinctBy { it.quality }.toMutableList()

        if (qualities.none { it.url == url }) {
            clickedLink?.let { qualities.add(it) } ?: run {
                qualities.add(SniffedMedia(url, safeTitle, format = if (isM3u) "M3U8" else "Bilinmeyen", quality = "Otomatik", duration = targetDuration))
            }
        }
        
        val isLiveStream = isM3u && targetDuration.isBlank()
        val offlineResolved = isOffline || url.startsWith("file://") || url.startsWith("content://")

        _activeVideo.value = ActiveVideoState(
            title = safeTitle,
            url = url,
            audioUrl = clickedLink?.audioUrl,
            posterUrl = posterUrl,
            startPositionMs = startPositionMs,
            isLive = isLiveStream,
            availableQualities = qualities,
            subtitles = _sniffedSubtitles.value,
            referer = clickedLink?.referer,
            userAgent = clickedLink?.userAgent,
            headers = clickedLink?.headers ?: emptyMap(),
            isOffline = offlineResolved
        )
    }

    fun switchVideoQuality(newUrl: String, currentPositionMs: Long) {
        val current = _activeVideo.value ?: return
        val isM3u = newUrl.endsWith(".m3u8") || newUrl.contains("m3u8") || newUrl.contains("live")
        val clickedLink = _sniffedLinks.value.find { it.url == newUrl }
        val targetDuration = clickedLink?.duration ?: ""
        val isLiveStream = isM3u && targetDuration.isBlank()

        _activeVideo.value = current.copy(
            url = newUrl,
            audioUrl = clickedLink?.audioUrl,
            startPositionMs = currentPositionMs,
            isLive = isLiveStream,
            subtitles = current.subtitles
        )
    }

    fun updateWatchProgress(positionMs: Long, durationMs: Long, force: Boolean = false) {
        val current = _activeVideo.value ?: return
        if (current.url.isBlank() || current.isLive) return
        
        // Prevent spurious 0-ms updates during initial stream preparation if started with positive offset
        if (positionMs == 0L && current.startPositionMs > 0L && !force) {
            return
        }

        val now = System.currentTimeMillis()
        val posDiff = kotlin.math.abs(positionMs - lastSavedHistoryPositionMs)
        val timeDiff = now - lastSavedHistoryTimestamp

        // Save if forced (closing/pausing/seeking), or if 2 seconds elapsed or user jumped > 3 seconds
        if (!force && timeDiff < 2000 && posDiff < 3000) {
            return
        }

        lastSavedHistoryPositionMs = positionMs
        lastSavedHistoryTimestamp = now

        viewModelScope.launch(Dispatchers.IO) {
            repository.saveOrUpdateWatchPosition(
                title = current.title,
                url = current.url,
                posterUrl = current.posterUrl,
                positionMs = positionMs,
                durationMs = durationMs,
                streamType = "VIDEO"
            )
        }
    }

    fun closePlayer(currentPositionMs: Long = -1L, durationMs: Long = -1L) {
        val current = _activeVideo.value
        if (current != null && current.url.isNotBlank() && !current.isLive) {
            val finalPos = if (currentPositionMs >= 0) {
                currentPositionMs
            } else if (lastSavedHistoryPositionMs >= 0) {
                lastSavedHistoryPositionMs
            } else {
                current.startPositionMs
            }
            val finalDur = if (durationMs > 0) durationMs else 0L

            viewModelScope.launch(Dispatchers.IO) {
                repository.saveOrUpdateWatchPosition(
                    title = current.title,
                    url = current.url,
                    posterUrl = current.posterUrl,
                    positionMs = finalPos,
                    durationMs = finalDur,
                    streamType = "VIDEO"
                )
            }
        }
        _activeVideo.value = null
        lastSavedHistoryPositionMs = -1L
        lastSavedHistoryTimestamp = 0L
    }

    fun addSniffedSubtitle(url: String, language: String = "", label: String = "") {
        streamSnifferManager.parseRawEvent("WEBVIEW_NETWORK", url, "Subtitle", label, language, "", "text/vtt")
        return
    // Legacy logic disabled below
        if (url.isBlank() || _sniffedSubtitles.value.any { it.url == url }) return
        val list = _sniffedSubtitles.value.toMutableList()
        list.add(SubtitleTrack(url, language, label))
        _sniffedSubtitles.value = list.takeLast(20)
    }

    fun addSniffedLinkJson(jsonStr: String) {
        try {
            val jsonObj = org.json.JSONObject(jsonStr)
            val sourceType = jsonObj.optString("sourceType")
            val url = jsonObj.optString("url")
            val type = jsonObj.optString("type", "Unknown")
            val title = jsonObj.optString("title")
            val duration = jsonObj.optString("duration")
            val quality = jsonObj.optString("quality")
            val mimeType = jsonObj.optString("mimeType")
            val isMse = jsonObj.optBoolean("isMse", false)
            val isBlob = jsonObj.optBoolean("isBlob", false)
            
            val iframeUrl = jsonObj.optString("iframeUrl", "")
            val topPageUrl = jsonObj.optString("topPageUrl", "")
            val referer = if (iframeUrl.isNotEmpty() && iframeUrl != topPageUrl) iframeUrl else topPageUrl
            
            val mediaSourceId = jsonObj.optString("mediaSourceId", "")
            val sourceBufferId = jsonObj.optString("sourceBufferId", "")
            val byteLength = jsonObj.optLong("byteLength", 0L)
            val initiatorType = jsonObj.optString("initiatorType", "")
            val correlatedUrl = jsonObj.optString("correlatedUrl", "")

            streamSnifferManager.parseRawEvent(
                sourceType = if (sourceType.isEmpty()) "JSON" else sourceType,
                url = url,
                type = type,
                title = title,
                duration = duration,
                quality = quality,
                mimeType = mimeType.takeIf { it.isNotEmpty() },
                isMse = isMse,
                isBlob = isBlob,
                referer = referer.takeIf { it.isNotEmpty() },
                mediaSourceId = mediaSourceId.takeIf { it.isNotEmpty() },
                sourceBufferId = sourceBufferId.takeIf { it.isNotEmpty() },
                byteLength = byteLength.takeIf { it > 0 },
                topPageUrl = topPageUrl.takeIf { it.isNotEmpty() },
                initiatorType = initiatorType.takeIf { it.isNotEmpty() },
                iframeUrl = iframeUrl.takeIf { it.isNotEmpty() },
                correlatedUrl = correlatedUrl.takeIf { it.isNotEmpty() }
            )
        } catch(e: Exception) { }
    }

    fun addSniffedLink(url: String, title: String, jsDuration: String = "", jsQuality: String = "", referer: String? = null, userAgent: String? = null, headers: Map<String, String> = emptyMap()) {
        streamSnifferManager.parseRawEvent("WEBVIEW_NETWORK", url, "Video", title, jsDuration, jsQuality, null, false, false, referer, userAgent, headers)

        streamSnifferManager.parseRawEvent("WEBVIEW_NETWORK", url, "Video", title, jsDuration, jsQuality, null, false, false, referer, userAgent)
        return
    // Legacy logic disabled below
        if (url.isBlank() || _sniffedLinks.value.any { it.url == url || it.audioUrl == url }) return

        // Strict Auto-Merge Logic for Demuxed Streams
        val currentUri = try { android.net.Uri.parse(url) } catch(e: Exception) { null }
        val filename = currentUri?.path?.substringAfterLast("/") ?: ""
        
        val isMaster = filename.equals("master.m3u8", ignoreCase = true) || filename.equals("index.m3u8", ignoreCase = true) || filename.equals("playlist.m3u8", ignoreCase = true)
        val isAudio = filename.contains("audio", ignoreCase = true) || filename.contains("aac", ignoreCase = true) || filename.contains("-a1", ignoreCase = true) || filename.contains("-a2", ignoreCase = true) || filename.contains("-a3", ignoreCase = true) || filename.contains("-a.", ignoreCase = true) || filename.contains("_a.", ignoreCase = true)
        val isVideo = filename.contains("video", ignoreCase = true) || filename.contains("-v1", ignoreCase = true) || filename.contains("-v2", ignoreCase = true) || filename.contains("-v3", ignoreCase = true) || filename.contains("-v.", ignoreCase = true) || filename.contains("_v.", ignoreCase = true)
        
        if (!isMaster && (isAudio || isVideo)) {
            try {
                val existingLinks = _sniffedLinks.value.toMutableList()
                var merged = false
                
                for (i in existingLinks.indices) {
                    val existing = existingLinks[i]
                    if (existing.isMerged) continue
                    
                    val existingUri = android.net.Uri.parse(existing.url)
                    val existingFilename = existingUri.path?.substringAfterLast("/") ?: ""
                    val existingIsMaster = existingFilename.equals("master.m3u8", ignoreCase = true) || existingFilename.equals("index.m3u8", ignoreCase = true) || existingFilename.equals("playlist.m3u8", ignoreCase = true)
                    
                    if (!existingIsMaster && currentUri?.host == existingUri.host) {
                        val currentDirPath = currentUri?.path?.substringBeforeLast("/") ?: ""
                        val existingDirPath = existingUri.path?.substringBeforeLast("/") ?: ""
                        
                        // Exact match on directory structure (Rapidrame puts a/v in same dir)
                        if (currentDirPath == existingDirPath && currentDirPath.isNotEmpty()) {
                            val existingIsAudio = existingFilename.contains("audio", ignoreCase = true) || existingFilename.contains("aac", ignoreCase = true) || existingFilename.contains("-a1", ignoreCase = true) || existingFilename.contains("-a2", ignoreCase = true) || existingFilename.contains("-a3", ignoreCase = true) || existingFilename.contains("-a.", ignoreCase = true) || existingFilename.contains("_a.", ignoreCase = true)
                            val existingIsVideo = existingFilename.contains("video", ignoreCase = true) || existingFilename.contains("-v1", ignoreCase = true) || existingFilename.contains("-v2", ignoreCase = true) || existingFilename.contains("-v3", ignoreCase = true) || existingFilename.contains("-v.", ignoreCase = true) || existingFilename.contains("_v.", ignoreCase = true)
                            
                            if (isAudio && (existingIsVideo || !existingIsAudio)) {
                                existingLinks[i] = existing.copy(audioUrl = url, isMerged = true)
                                _sniffedLinks.value = existingLinks
                                merged = true
                                break
                            } else if (isVideo && (existingIsAudio || !existingIsVideo)) {
                                existingLinks[i] = existing.copy(url = url, audioUrl = existing.url, isMerged = true)
                                _sniffedLinks.value = existingLinks
                                merged = true
                                break
                            }
                        }
                    }
                }
                if (merged) return
            } catch (e: Exception) {
                // Ignore parse errors and fallback to standard adding
            }
        }

        val format = guessFormat(url)
        val quality = if (jsQuality.isNotBlank()) jsQuality else guessQuality(url)
        val duration = jsDuration

        _sniffedLinks.value = (_sniffedLinks.value + SniffedMedia(url, title, format, quality, duration)).takeLast(20)

        // Background probing for manifest (M3U8) or MP4/TS data
        if (duration.isBlank() || quality == "Otomatik") {
            viewModelScope.launch(Dispatchers.IO) {
                probeMediaInfo(url)
            }
        }
    }

    private suspend fun probeMediaInfo(url: String) {
        try {
            var newQuality: String? = null
            var newDuration: String? = null

            if (url.contains(".m3u8") || url.contains("m3u8")) {
                var connection: HttpURLConnection? = null
                var childConn: HttpURLConnection? = null
                try {
                    connection = URL(url).openConnection() as HttpURLConnection
                    connection.connectTimeout = 3000
                    connection.readTimeout = 3000
                    val content = connection.inputStream.bufferedReader().use { it.readText() }
                    
                    val resRegex = "RESOLUTION=\\d+x(\\d+)".toRegex()
                    val resMatch = resRegex.find(content)
                    if (resMatch != null) {
                        resMatch.groupValues[1].toIntOrNull()?.let { newQuality = "${it}p" }
                    }
                    
                    val extinfRegex = "#EXTINF:([0-9\\.]+),".toRegex()
                    val extinfMatches = extinfRegex.findAll(content)
                    var totalSeconds = 0.0
                    for (match in extinfMatches) {
                        totalSeconds += match.groupValues[1].toDoubleOrNull() ?: 0.0
                    }
                    
                    // If master playlist has no EXTINF directly, check child variant playlists
                    if (totalSeconds == 0.0) {
                        val childM3u8Lines = content.lines().filter { line ->
                            val trimmed = line.trim()
                            trimmed.isNotBlank() && !trimmed.startsWith("#") && (trimmed.contains(".m3u8") || !trimmed.contains("://"))
                        }
                        if (childM3u8Lines.isNotEmpty()) {
                            val firstChild = childM3u8Lines.first().trim()
                            val childUrl = if (firstChild.startsWith("http://") || firstChild.startsWith("https://")) {
                                firstChild
                            } else {
                                val base = url.substringBeforeLast("/")
                                "$base/$firstChild"
                            }
                            try {
                                childConn = URL(childUrl).openConnection() as HttpURLConnection
                                childConn.connectTimeout = 3000
                                childConn.readTimeout = 3000
                                val childContent = childConn.inputStream.bufferedReader().use { it.readText() }
                                val childMatches = extinfRegex.findAll(childContent)
                                for (match in childMatches) {
                                    totalSeconds += match.groupValues[1].toDoubleOrNull() ?: 0.0
                                }
                            } catch (_: Exception) {}
                        }
                    }

                    if (totalSeconds > 0) {
                        val h = (totalSeconds / 3600).toInt()
                        val m = ((totalSeconds % 3600) / 60).toInt()
                        val s = (totalSeconds % 60).toInt()
                        newDuration = if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
                    }
                } finally {
                    try { connection?.disconnect() } catch (_: Exception) {}
                    try { childConn?.disconnect() } catch (_: Exception) {}
                }
            } else if (!url.endsWith(".m3u8")) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(url, HashMap<String, String>())
                    val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    
                    durStr?.toLongOrNull()?.let { ms ->
                        if (ms > 0) {
                            val seconds = ms / 1000
                            val h = seconds / 3600
                            val m = (seconds % 3600) / 60
                            val s = seconds % 60
                            newDuration = if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
                        }
                    }
                    heightStr?.toIntOrNull()?.let { h -> if (h > 0) newQuality = "${h}p" }
                } catch (e: Exception) {
                } finally {
                    retriever.release()
                }
            }

            if (newQuality != null || newDuration != null) {
                _sniffedLinks.update { list ->
                    list.map { item ->
                        if (item.url == url) {
                            item.copy(
                                quality = newQuality ?: item.quality,
                                duration = newDuration ?: item.duration
                            )
                        } else item
                    }
                }
            }
        } catch (e: Exception) { }
    }

    private fun guessFormat(url: String): String {
        val lowerUrl = url.lowercase()
        val withoutQuery = lowerUrl.substringBefore("?")
        return when {
            withoutQuery.endsWith(".m3u8") || lowerUrl.contains("m3u8") -> "M3U8 HLS"
            withoutQuery.endsWith(".mp4") || lowerUrl.contains(".mp4") -> "MP4"
            withoutQuery.endsWith(".ts") || lowerUrl.contains(".ts?") -> "TS Segment"
            withoutQuery.endsWith(".webm") || lowerUrl.contains(".webm") -> "WEBM"
            withoutQuery.endsWith(".mkv") -> "MKV"
            withoutQuery.endsWith(".avi") -> "AVI"
            withoutQuery.endsWith(".mpd") || lowerUrl.contains(".mpd") -> "DASH"
            else -> "Video"
        }
    }

    private fun guessQuality(url: String): String {
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.contains("4k") || lowerUrl.contains("2160p") -> "4K"
            lowerUrl.contains("1440p") -> "1440p"
            lowerUrl.contains("1080p") || lowerUrl.contains("1080") -> "1080p"
            lowerUrl.contains("720p") || lowerUrl.contains("720") -> "720p"
            lowerUrl.contains("480p") || lowerUrl.contains("480") -> "480p"
            lowerUrl.contains("360p") || lowerUrl.contains("360") -> "360p"
            lowerUrl.contains("240p") || lowerUrl.contains("240") -> "240p"
            lowerUrl.contains("hd") -> "HD"
            lowerUrl.contains("sd") -> "SD"
            else -> "Otomatik"
        }
    }

    fun clearSniffedLinks() {
        _sniffedLinks.value = emptyList()
        streamSnifferManager.clear()
    }

    fun saveLink(title: String, url: String, category: String, notes: String? = null) {
        viewModelScope.launch {
            repository.addSavedLink(title, url, category, notes)
        }
    }

    fun deleteSavedLink(id: Long) {
        viewModelScope.launch {
            repository.deleteSavedLink(id)
        }
    }

    fun clearSavedLinks() {
        viewModelScope.launch {
            repository.clearSavedLinks()
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteHistoryItem(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun updateSettings(newSettings: PlayerSettingsEntity) {
        viewModelScope.launch {
            repository.updateSettings(newSettings)
        }
    }

    fun addBookmark(title: String, url: String, imageUrl: String) {
        viewModelScope.launch {
            repository.addBookmark(title, url, imageUrl)
        }
    }

    fun updateBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            repository.updateBookmark(bookmark)
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmark)
        }
    }

    fun addBrowserHistory(title: String, url: String) {
        viewModelScope.launch {
            repository.addBrowserHistory(title, url)
        }
    }

    fun deleteBrowserHistory(history: BrowserHistoryEntity) {
        viewModelScope.launch {
            repository.deleteBrowserHistory(history)
        }
    }

    fun clearBrowserHistory() {
        viewModelScope.launch {
            repository.clearBrowserHistory()
        }
    }

    fun startDownload(
        title: String,
        url: String,
        posterUrl: String = "",
        type: String = "DIRECT_DOWNLOAD",
        referer: String? = null,
        userAgent: String? = null,
        headers: Map<String, String>? = null
    ) {
        if (url.isBlank()) return
        val application = getApplication<Application>()
        val intent = Intent(application, com.example.service.DownloadService::class.java).apply {
            action = "START"
            putExtra("title", title.ifBlank { "İndirilen Video" })
            putExtra("url", url)
            putExtra("posterUrl", posterUrl)
            putExtra("type", type)
            referer?.let { putExtra("referer", it) }
            userAgent?.let { putExtra("userAgent", it) }
            headers?.let {
                val bundle = android.os.Bundle()
                for ((k, v) in it) {
                    bundle.putString(k, v)
                }
                putExtra("headers", bundle)
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            application.startForegroundService(intent)
        } else {
            application.startService(intent)
        }
    }

    fun pauseDownload(id: Long) {
        val application = getApplication<Application>()
        val intent = Intent(application, com.example.service.DownloadService::class.java).apply {
            action = "PAUSE"
            putExtra("id", id)
        }
        application.startService(intent)
    }

    fun resumeDownload(id: Long) {
        val application = getApplication<Application>()
        val intent = Intent(application, com.example.service.DownloadService::class.java).apply {
            action = "RESUME"
            putExtra("id", id)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            application.startForegroundService(intent)
        } else {
            application.startService(intent)
        }
    }

    fun deleteDownload(id: Long) {
        val application = getApplication<Application>()
        val intent = Intent(application, com.example.service.DownloadService::class.java).apply {
            action = "CANCEL"
            putExtra("id", id)
        }
        application.startService(intent)
    }

    fun clearAllDownloads() {
        val application = getApplication<Application>()
        val intent = Intent(application, com.example.service.DownloadService::class.java).apply {
            action = "CLEAR_ALL"
        }
        application.startService(intent)
    }

    fun playDownloadedVideo(item: DownloadItemEntity) {
        val isLocal = item.localPath.startsWith("content://") || (item.localPath.isNotBlank() && File(item.localPath).exists())
        val playUrl = if (item.localPath.startsWith("content://")) {
            item.localPath
        } else if (item.localPath.isNotBlank() && File(item.localPath).exists()) {
            "file://${item.localPath}"
        } else {
            item.url
        }
        playVideo(
            title = item.title,
            url = playUrl,
            posterUrl = item.posterUrl,
            isOffline = isLocal
        )
    }

    suspend fun testDnsConnection(target: String): Result<String> {
        return CustomDnsResolver.testDns(target)
    }

    fun toggleVirtualMouse(forceState: Boolean? = null) {
        val newState = forceState ?: !_isTvCursorActive.value
        _isTvCursorActive.value = newState
        if (!newState) {
            _isDragScrollMode.value = false
        }
        _cursorHudMessage.value = if (newState) "🎯 Sanal Fare: AÇIK (OK: Tıkla | OK Basılı Tut: Sürükle)" else "❌ Sanal Fare: KAPALI"
    }

    fun toggleDragScrollMode(forceState: Boolean? = null) {
        val newState = forceState ?: !_isDragScrollMode.value
        _isDragScrollMode.value = newState
        if (newState) {
            _isTvCursorActive.value = true
        }
        _cursorHudMessage.value = if (newState) "✋ Sürükleme / Kaydırma Modu: AÇIK" else "🎯 Normal Fare Modu: AÇIK"
    }

    fun clearCursorHudMessage() {
        _cursorHudMessage.value = null
    }

    private var lastTouchDownTime: Long = 0L

    fun moveCursor(dx: Float, dy: Float, boundWidth: Float, boundHeight: Float, decorView: View? = null) {
        val newX = (_cursorX.value + dx).coerceIn(10f, boundWidth.coerceAtLeast(100f) - 10f)
        val newY = (_cursorY.value + dy).coerceIn(10f, boundHeight.coerceAtLeast(100f) - 10f)
        _cursorX.value = newX
        _cursorY.value = newY

        val isDragging = _isCursorClicking.value || _isDragScrollMode.value

        // 1. If dragging or in Drag Scroll mode, dispatch native MotionEvent.ACTION_MOVE
        if (isDragging && decorView != null) {
            performSimulatedTouch(decorView, MotionEvent.ACTION_MOVE)
            // 2. Also directly scroll active WebViews/ScrollViews for 100% responsiveness on Android TV
            val scrollMultiplier = 3.2f
            scrollViewsRecursively(decorView, (dx * scrollMultiplier).toInt(), (dy * scrollMultiplier).toInt())
        }
    }

    fun scrollPage(decorView: View?, directionUp: Boolean) {
        if (decorView == null) return
        val deltaY = if (directionUp) -600 else 600
        scrollViewsRecursively(decorView, 0, deltaY)
    }

    private fun scrollViewsRecursively(root: View, dx: Int, dy: Int) {
        if (root is android.webkit.WebView) {
            root.scrollBy(dx, dy)
            root.evaluateJavascript("window.scrollBy({top: $dy, left: $dx, behavior: 'auto'});", null)
            return
        }
        if (root is android.widget.ScrollView) {
            root.smoothScrollBy(dx, dy)
            return
        }
        if (root is androidx.core.widget.NestedScrollView) {
            root.smoothScrollBy(dx, dy)
            return
        }
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                scrollViewsRecursively(root.getChildAt(i), dx, dy)
            }
        }
    }

    fun setCursorPosition(x: Float, y: Float) {
        _cursorX.value = x
        _cursorY.value = y
    }

    fun performSimulatedTouch(decorView: View, action: Int) {
        val x = _cursorX.value
        val y = _cursorY.value
        val now = SystemClock.uptimeMillis()

        if (action == MotionEvent.ACTION_DOWN) {
            lastTouchDownTime = now
            _isCursorClicking.value = true
        }

        val downTime = if (lastTouchDownTime > 0L) lastTouchDownTime else now
        val motionEvent = MotionEvent.obtain(downTime, now, action, x, y, 0)
        try {
            decorView.dispatchTouchEvent(motionEvent)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            motionEvent.recycle()
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            _isCursorClicking.value = false
            lastTouchDownTime = 0L
        }
    }

    fun dispatchPlayerAction(action: PlayerRemoteAction) {
        _playerActionFlow.tryEmit(action)
    }

    val iptvPlaylists: kotlinx.coroutines.flow.Flow<List<IptvPlaylistEntity>> = db.iptvDao().getAllPlaylists()

    fun addIptvPlaylist(name: String, url: String, isSingleChannel: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            db.iptvDao().insertPlaylist(IptvPlaylistEntity(name = name, url = url, isSingleChannel = isSingleChannel))
        }
    }

    fun deleteIptvPlaylist(playlist: IptvPlaylistEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.iptvDao().deletePlaylist(playlist)
        }
    }

    override fun onCleared() {
        super.onCleared()
        streamSnifferManager.destroy()
    }
}