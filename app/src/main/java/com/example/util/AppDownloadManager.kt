package com.example.util

import android.content.Context
import android.os.Environment
import com.example.data.model.DownloadItemEntity
import com.example.data.repository.MediaPlayerRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class AppDownloadManager(
    private val context: Context,
    private val repository: MediaPlayerRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val pausedIds = ConcurrentHashMap.newKeySet<Long>()

    // Cache headers/referer per download ID for pause/resume
    private val downloadHeaders = ConcurrentHashMap<Long, Map<String, String>>()
    private val downloadReferers = ConcurrentHashMap<Long, String?>()
    private val downloadUserAgents = ConcurrentHashMap<Long, String?>()

    private val okHttpClient = OkHttpClient.Builder()
        .dns(CustomDnsResolver)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val hlsDownloader = HlsStreamDownloader(
        context = context,
        repository = repository,
        okHttpClient = okHttpClient,
        pausedIds = pausedIds
    )

    private fun getDownloadDir(): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) 
            ?: File(context.filesDir, "downloads")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(60)
            .ifBlank { "video_${System.currentTimeMillis()}" }
    }

    fun startOrResumeDownload(
        type: String = "DIRECT_DOWNLOAD", 
        title: String,
        url: String,
        posterUrl: String = "",
        existingId: Long? = null,
        referer: String? = null,
        userAgent: String? = null,
        headers: Map<String, String> = emptyMap()
    ) {
        scope.launch {
            val downloadId: Long
            val item: DownloadItemEntity

            if (existingId != null) {
                downloadId = existingId
                val found = repository.getDownloadById(existingId) ?: return@launch
                item = found
                pausedIds.remove(downloadId)
                if (headers.isNotEmpty()) downloadHeaders[downloadId] = headers
                if (!referer.isNullOrBlank()) downloadReferers[downloadId] = referer
                if (!userAgent.isNullOrBlank()) downloadUserAgents[downloadId] = userAgent
            } else {
                val isHls = type == "HLS_DOWNLOAD" || url.contains(".m3u8", ignoreCase = true) || url.contains("m3u8", ignoreCase = true)
                val cleanTitle = sanitizeFileName(title.ifBlank { url.substringAfterLast("/").substringBefore("?") })
                val extension = if (isHls) "ts" 
                    else if (url.contains(".mp4", ignoreCase = true)) "mp4" 
                    else if (url.contains(".mkv", ignoreCase = true)) "mkv" 
                    else if (url.contains(".webm", ignoreCase = true)) "webm" 
                    else "mp4"

                val settings = repository.getSettings()
                val mimeType = if (extension == "ts") "video/mp2t" else "video/$extension"
                
                var localPathString = ""
                if (settings.downloadDirectoryUri.isNotBlank()) {
                    try {
                        val rootUri = android.net.Uri.parse(settings.downloadDirectoryUri)
                        val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, rootUri)
                        if (rootDoc != null && rootDoc.exists() && rootDoc.canWrite()) {
                            val fileName = "${cleanTitle}_${System.currentTimeMillis()}.$extension"
                            val doc = rootDoc.createFile(mimeType, fileName)
                            if (doc != null) {
                                localPathString = doc.uri.toString()
                            }
                        }
                    } catch (e: Exception) {}
                }
                
                if (localPathString.isBlank()) {
                    val destinationFile = File(getDownloadDir(), "${cleanTitle}_${System.currentTimeMillis()}.$extension")
                    localPathString = destinationFile.absolutePath
                }

                val newEntity = DownloadItemEntity(
                    title = title.ifBlank { "Video İndirmesi" },
                    url = url,
                    localPath = localPathString,
                    posterUrl = posterUrl,
                    status = "DOWNLOADING",
                    progress = 0,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    speed = "Başlatılıyor...",
                    mimeType = mimeType
                )
                downloadId = repository.insertDownload(newEntity)
                item = newEntity.copy(id = downloadId)
                pausedIds.remove(downloadId)

                if (headers.isNotEmpty()) downloadHeaders[downloadId] = headers
                if (!referer.isNullOrBlank()) downloadReferers[downloadId] = referer
                if (!userAgent.isNullOrBlank()) downloadUserAgents[downloadId] = userAgent
            }

            // Cancel any old running job for this ID
            activeJobs[downloadId]?.cancel()

            val job = launch(Dispatchers.IO) {
                val isHlsStream = item.mimeType == "video/mp2t" || 
                                  item.mimeType == "application/x-mpegURL" ||
                                  item.url.contains(".m3u8", ignoreCase = true) ||
                                  item.url.contains("m3u8", ignoreCase = true)
                if (isHlsStream) {
                    val currentHeaders = downloadHeaders[downloadId] ?: emptyMap()
                    val currentReferer = downloadReferers[downloadId]
                    val currentUserAgent = downloadUserAgents[downloadId]
                    hlsDownloader.downloadHls(downloadId, item, currentHeaders, currentReferer, currentUserAgent)
                } else {
                    performDownload(downloadId, item)
                }
            }
            activeJobs[downloadId] = job
        }
    }

    private suspend fun performDownload(id: Long, item: DownloadItemEntity) {
        val isContentUri = item.localPath.startsWith("content://")
        val initialDownloaded = if (isContentUri) {
            try {
                val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, android.net.Uri.parse(item.localPath))
                doc?.length() ?: 0L
            } catch (e: Exception) { 0L }
        } else {
            val targetFile = File(item.localPath)
            if (targetFile.exists()) targetFile.length() else 0L
        }

        repository.updateDownloadProgress(
            id = id,
            progress = item.progress,
            downloadedBytes = initialDownloaded,
            totalBytes = item.totalBytes,
            speed = "Bağlanıyor...",
            status = "DOWNLOADING",
            localPath = item.localPath
        )

        try {
            val reqHeaders = downloadHeaders[id] ?: emptyMap()
            val reqReferer = downloadReferers[id]
            val reqUserAgent = downloadUserAgents[id]

            val defaultUa = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            val requestBuilder = Request.Builder()
                .url(item.url)
                .header("User-Agent", reqUserAgent?.ifBlank { defaultUa } ?: defaultUa)

            if (!reqReferer.isNullOrBlank()) {
                requestBuilder.header("Referer", reqReferer)
            }

            for ((k, v) in reqHeaders) {
                if (!k.equals("User-Agent", ignoreCase = true) && !k.equals("Referer", ignoreCase = true) && !k.equals("Range", ignoreCase = true)) {
                    requestBuilder.header(k, v)
                }
            }

            if (initialDownloaded > 0L) {
                requestBuilder.header("Range", "bytes=$initialDownloaded-")
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                repository.updateDownloadProgress(
                    id = id,
                    progress = item.progress,
                    downloadedBytes = initialDownloaded,
                    totalBytes = item.totalBytes,
                    speed = "",
                    status = "FAILED",
                    localPath = item.localPath,
                    errorMessage = "Sunucu yanıt vermedi (${response.code})"
                )
                response.close()
                return
            }

            val body = response.body
            if (body == null) {
                response.close()
                repository.updateDownloadProgress(
                    id = id,
                    progress = item.progress,
                    downloadedBytes = initialDownloaded,
                    totalBytes = item.totalBytes,
                    speed = "",
                    status = "FAILED",
                    localPath = item.localPath,
                    errorMessage = "Boş veri akışı"
                )
                return
            }

            val contentLength = body.contentLength()
            val totalBytes = if (contentLength > 0) {
                if (response.code == 206) contentLength + initialDownloaded else contentLength
            } else {
                item.totalBytes
            }

            var inputStream: InputStream? = null
            var outputStream: OutputStream? = null

            try {
                inputStream = body.byteStream()
                
                val isAppend = initialDownloaded > 0 && response.code == 206
                if (isContentUri) {
                    outputStream = context.contentResolver.openOutputStream(android.net.Uri.parse(item.localPath), if (isAppend) "wa" else "w")
                } else {
                    val targetFile = File(item.localPath)
                    targetFile.parentFile?.mkdirs()
                    outputStream = FileOutputStream(targetFile, isAppend)
                }

                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                var currentDownloaded = if (isAppend) initialDownloaded else 0L
                var lastSpeedCalcTime = System.currentTimeMillis()
                var bytesSinceLastCalc = 0L
                var currentSpeed = ""

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (pausedIds.contains(id)) {
                        repository.updateDownloadProgress(
                            id = id,
                            progress = if (totalBytes > 0) ((currentDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else 0,
                            downloadedBytes = currentDownloaded,
                            totalBytes = totalBytes,
                            speed = "",
                            status = "PAUSED",
                            localPath = item.localPath
                        )
                        return
                    }

                    outputStream?.write(buffer, 0, bytesRead)
                    currentDownloaded += bytesRead
                    bytesSinceLastCalc += bytesRead

                    val now = System.currentTimeMillis()
                    val deltaTime = now - lastSpeedCalcTime
                    if (deltaTime >= 1000) {
                        val speedBytesPerSec = (bytesSinceLastCalc * 1000) / deltaTime
                        currentSpeed = formatSpeed(speedBytesPerSec)
                        
                        val progress = if (totalBytes > 0) {
                            ((currentDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }

                        repository.updateDownloadProgress(
                            id = id,
                            progress = progress,
                            downloadedBytes = currentDownloaded,
                            totalBytes = totalBytes,
                            speed = currentSpeed,
                            status = "DOWNLOADING",
                            localPath = item.localPath
                        )
                        lastSpeedCalcTime = now
                        bytesSinceLastCalc = 0L
                    }
                }

                outputStream?.flush()

                // Finished successfully
                repository.updateDownloadProgress(
                    id = id,
                    progress = 100,
                    downloadedBytes = currentDownloaded,
                    totalBytes = if (totalBytes > 0) totalBytes else currentDownloaded,
                    speed = "Tamamlandı",
                    status = "COMPLETED",
                    localPath = item.localPath
                )

            } finally {
                inputStream?.close()
                outputStream?.close()
                response.close()
            }

        } catch (e: Exception) {
            if (pausedIds.contains(id)) {
                // Paused by user, don't mark as FAILED
                return
            }
            repository.updateDownloadProgress(
                id = id,
                progress = item.progress,
                downloadedBytes = initialDownloaded,
                totalBytes = item.totalBytes,
                speed = "",
                status = "FAILED",
                localPath = item.localPath,
                errorMessage = e.localizedMessage ?: "İndirme kesildi"
            )
        } finally {
            activeJobs.remove(id)
        }
    }

    fun pauseDownload(id: Long) {
        pausedIds.add(id)
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        scope.launch {
            val item = repository.getDownloadById(id) ?: return@launch
            repository.updateDownloadProgress(
                id = id,
                progress = item.progress,
                downloadedBytes = item.downloadedBytes,
                totalBytes = item.totalBytes,
                speed = "",
                status = "PAUSED",
                localPath = item.localPath
            )
        }
    }

    fun resumeDownload(id: Long) {
        pausedIds.remove(id)
        scope.launch {
            val item = repository.getDownloadById(id) ?: return@launch
            startOrResumeDownload(
                title = item.title,
                url = item.url,
                posterUrl = item.posterUrl,
                existingId = id
            )
        }
    }

    fun cancelOrDeleteDownload(id: Long) {
        pausedIds.remove(id)
        downloadHeaders.remove(id)
        downloadReferers.remove(id)
        downloadUserAgents.remove(id)
        activeJobs[id]?.cancel()
        activeJobs.remove(id)

        scope.launch {
            val item = repository.getDownloadById(id)
            if (item != null && item.localPath.isNotBlank()) {
                if (item.localPath.startsWith("content://")) {
                    try {
                        androidx.documentfile.provider.DocumentFile.fromSingleUri(context, android.net.Uri.parse(item.localPath))?.delete()
                    } catch (e: Exception) {}
                } else {
                    val file = File(item.localPath)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            }
            repository.deleteDownload(id)
        }
    }

    fun cancelAllDownloads() {
        pausedIds.clear()
        downloadHeaders.clear()
        downloadReferers.clear()
        downloadUserAgents.clear()
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()

        scope.launch {
            val dao = com.example.data.database.AppDatabase.getDatabase(context).downloadDao()
            val allItems = dao.getAllDownloadsList()
            for (item in allItems) {
                if (item.localPath.isNotBlank()) {
                    if (item.localPath.startsWith("content://")) {
                        try {
                            androidx.documentfile.provider.DocumentFile.fromSingleUri(context, android.net.Uri.parse(item.localPath))?.delete()
                        } catch (e: Exception) {}
                    } else {
                        val file = File(item.localPath)
                        if (file.exists()) file.delete()
                    }
                }
            }
            repository.clearAllDownloads()
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format(java.util.Locale.getDefault(), "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
            bytesPerSec >= 1024 -> String.format(java.util.Locale.getDefault(), "%.0f KB/s", bytesPerSec / 1024.0)
            else -> "$bytesPerSec B/s"
        }
    }

    fun cleanup() {
        scope.cancel()
    }
}
