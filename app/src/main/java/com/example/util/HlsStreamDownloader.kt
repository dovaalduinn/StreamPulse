package com.example.util

import android.content.Context
import android.net.Uri
import com.example.data.model.DownloadItemEntity
import com.example.data.repository.MediaPlayerRepository
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.StringReader
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Downloads HLS (m3u8) video streams by parsing the playlist,
 * resolving segment URLs, downloading each segment in order,
 * and stitching them into a unified transport stream (.ts) file.
 */
class HlsStreamDownloader(
    private val context: Context,
    private val repository: MediaPlayerRepository,
    private val okHttpClient: OkHttpClient,
    private val pausedIds: Set<Long>
) {

    suspend fun downloadHls(
        id: Long,
        item: DownloadItemEntity,
        headers: Map<String, String>,
        referer: String?,
        userAgent: String?
    ) {
        val isContentUri = item.localPath.startsWith("content://")

        repository.updateDownloadProgress(
            id = id,
            progress = item.progress,
            downloadedBytes = item.downloadedBytes,
            totalBytes = item.totalBytes,
            speed = "Çalma listesi ayrıştırılıyor...",
            status = "DOWNLOADING",
            localPath = item.localPath
        )

        try {
            // 1. Fetch the master or media m3u8 content
            val playlistUrl = item.url
            val playlistContent = fetchText(playlistUrl, headers, referer, userAgent)
                ?: throw Exception("HLS çalma listesi indirilemedi")

            // 2. If it's a master playlist with multiple variant streams, select the highest bandwidth / first stream
            val mediaPlaylistUrl = resolveMediaPlaylistUrl(playlistUrl, playlistContent, headers, referer, userAgent)
            val mediaPlaylistContent = if (mediaPlaylistUrl != playlistUrl) {
                fetchText(mediaPlaylistUrl, headers, referer, userAgent)
                    ?: throw Exception("Alt HLS çalma listesi indirilemedi")
            } else {
                playlistContent
            }

            // 3. Extract all segment URLs from the media playlist
            val segmentUrls = parseSegments(mediaPlaylistUrl, mediaPlaylistContent)
            if (segmentUrls.isEmpty()) {
                throw Exception("HLS çalma listesinde indirilecek video parçası bulunamadı")
            }

            val totalSegments = segmentUrls.size
            var outputStream: OutputStream? = null

            try {
                if (isContentUri) {
                    outputStream = context.contentResolver.openOutputStream(Uri.parse(item.localPath), "w")
                } else {
                    val targetFile = File(item.localPath)
                    targetFile.parentFile?.mkdirs()
                    outputStream = FileOutputStream(targetFile, false)
                }

                var downloadedBytesTotal = 0L
                var lastSpeedCalcTime = System.currentTimeMillis()
                var bytesSinceLastCalc = 0L
                var currentSpeed = ""

                for ((index, segUrl) in segmentUrls.withIndex()) {
                    if (pausedIds.contains(id) || !coroutineContext.isActive) {
                        repository.updateDownloadProgress(
                            id = id,
                            progress = ((index * 100) / totalSegments).coerceIn(0, 100),
                            downloadedBytes = downloadedBytesTotal,
                            totalBytes = 0L,
                            speed = "",
                            status = "PAUSED",
                            localPath = item.localPath
                        )
                        return
                    }

                    // Download this segment with retries
                    val segBytes = downloadSegmentWithRetry(segUrl, headers, referer, userAgent, maxRetries = 3)
                        ?: throw Exception("Video parçası (${index + 1}/$totalSegments) indirilemedi")

                    outputStream?.write(segBytes)
                    downloadedBytesTotal += segBytes.size
                    bytesSinceLastCalc += segBytes.size

                    val now = System.currentTimeMillis()
                    val deltaTime = now - lastSpeedCalcTime
                    if (deltaTime >= 1000 || index == totalSegments - 1) {
                        val speedBytesPerSec = if (deltaTime > 0) (bytesSinceLastCalc * 1000) / deltaTime else 0L
                        currentSpeed = formatSpeed(speedBytesPerSec)
                        val progressPercent = (((index + 1) * 100) / totalSegments).coerceIn(0, 100)

                        repository.updateDownloadProgress(
                            id = id,
                            progress = progressPercent,
                            downloadedBytes = downloadedBytesTotal,
                            totalBytes = 0L,
                            speed = "$currentSpeed • %$progressPercent (${index + 1}/$totalSegments)",
                            status = "DOWNLOADING",
                            localPath = item.localPath
                        )
                        lastSpeedCalcTime = now
                        bytesSinceLastCalc = 0L
                    }
                }

                outputStream?.flush()

                // Successfully finished downloading all segments
                repository.updateDownloadProgress(
                    id = id,
                    progress = 100,
                    downloadedBytes = downloadedBytesTotal,
                    totalBytes = downloadedBytesTotal,
                    speed = "Tamamlandı",
                    status = "COMPLETED",
                    localPath = item.localPath
                )

            } finally {
                outputStream?.close()
            }

        } catch (e: Exception) {
            if (pausedIds.contains(id)) return
            repository.updateDownloadProgress(
                id = id,
                progress = item.progress,
                downloadedBytes = item.downloadedBytes,
                totalBytes = item.totalBytes,
                speed = "",
                status = "FAILED",
                localPath = item.localPath,
                errorMessage = e.localizedMessage ?: "HLS akışı indirilemedi"
            )
        }
    }

    private fun fetchText(
        url: String,
        headers: Map<String, String>,
        referer: String?,
        userAgent: String?
    ): String? {
        val reqBuilder = Request.Builder().url(url)
        val defaultUa = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        reqBuilder.header("User-Agent", userAgent?.ifBlank { defaultUa } ?: defaultUa)
        if (!referer.isNullOrBlank()) {
            reqBuilder.header("Referer", referer)
        }
        for ((k, v) in headers) {
            if (!k.equals("User-Agent", ignoreCase = true) && !k.equals("Referer", ignoreCase = true)) {
                reqBuilder.header(k, v)
            }
        }

        return try {
            val resp = okHttpClient.newCall(reqBuilder.build()).execute()
            if (resp.isSuccessful) {
                resp.body?.string()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveMediaPlaylistUrl(
        baseUrl: String,
        content: String,
        headers: Map<String, String>,
        referer: String?,
        userAgent: String?
    ): String {
        if (!content.contains("#EXT-X-STREAM-INF")) {
            return baseUrl
        }

        // Master playlist detected. Find the stream with the highest BANDWIDTH or highest RESOLUTION
        var highestBandwidth = -1L
        var selectedUri: String? = null

        val reader = BufferedReader(StringReader(content))
        var line: String?
        var lastBandwidth = -1L

        while (reader.readLine().also { line = it } != null) {
            val l = line?.trim() ?: continue
            if (l.startsWith("#EXT-X-STREAM-INF:")) {
                val bandwidthMatch = Regex("BANDWIDTH=(\\d+)").find(l)
                lastBandwidth = bandwidthMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            } else if (l.isNotBlank() && !l.startsWith("#")) {
                if (lastBandwidth >= highestBandwidth) {
                    highestBandwidth = lastBandwidth
                    selectedUri = l
                }
                lastBandwidth = -1L
            }
        }

        return if (selectedUri != null) {
            resolveAbsoluteUrl(baseUrl, selectedUri)
        } else {
            baseUrl
        }
    }

    private fun parseSegments(baseUrl: String, playlistContent: String): List<String> {
        val segments = mutableListOf<String>()
        val reader = BufferedReader(StringReader(playlistContent))
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            val l = line?.trim() ?: continue
            if (l.isNotBlank() && !l.startsWith("#")) {
                val absUrl = resolveAbsoluteUrl(baseUrl, l)
                segments.add(absUrl)
            }
        }
        return segments
    }

    private fun downloadSegmentWithRetry(
        url: String,
        headers: Map<String, String>,
        referer: String?,
        userAgent: String?,
        maxRetries: Int
    ): ByteArray? {
        val defaultUa = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        val reqBuilder = Request.Builder().url(url)
        reqBuilder.header("User-Agent", userAgent?.ifBlank { defaultUa } ?: defaultUa)
        if (!referer.isNullOrBlank()) {
            reqBuilder.header("Referer", referer)
        }
        for ((k, v) in headers) {
            if (!k.equals("User-Agent", ignoreCase = true) && !k.equals("Referer", ignoreCase = true)) {
                reqBuilder.header(k, v)
            }
        }

        var attempt = 0
        while (attempt < maxRetries) {
            attempt++
            try {
                val resp = okHttpClient.newCall(reqBuilder.build()).execute()
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    if (bytes != null) return bytes
                }
            } catch (e: Exception) {
                if (attempt >= maxRetries) return null
                Thread.sleep(500L * attempt)
            }
        }
        return null
    }

    private fun resolveAbsoluteUrl(baseUrl: String, path: String): String {
        return try {
            val base = URI(baseUrl)
            base.resolve(path).toString()
        } catch (e: Exception) {
            if (path.startsWith("http://") || path.startsWith("https://")) {
                path
            } else {
                val cleanBase = baseUrl.substringBeforeLast("/")
                "$cleanBase/$path"
            }
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format(java.util.Locale.getDefault(), "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
            bytesPerSec >= 1024 -> String.format(java.util.Locale.getDefault(), "%.0f KB/s", bytesPerSec / 1024.0)
            else -> "$bytesPerSec B/s"
        }
    }
}
