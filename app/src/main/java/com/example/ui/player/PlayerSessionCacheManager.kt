package com.example.ui.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultAllocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
class PlayerSessionCacheManager(
    private val context: Context,
    private val referer: String? = null,
    private val userAgent: String? = null,
    private val headers: Map<String, String> = emptyMap(),
    private val allowInsecureSsl: Boolean = false,
    private val ramBufferLimitMb: Int = 100
) {

    private val sessionDir = File(context.cacheDir, "player_sessions/session_${System.currentTimeMillis()}").apply {
        mkdirs()
    }
    
    private val databaseProvider = StandaloneDatabaseProvider(context)
    // Dynamic unlimited cache for this single session:
    private val evictor = NoOpCacheEvictor()
    val cache = SimpleCache(sessionDir, evictor, databaseProvider)

    private val okHttpClient = run {
        val builder = okhttp3.OkHttpClient.Builder()
            .dns(com.example.util.CustomDnsResolver)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)

        if (allowInsecureSsl) {
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }
            )
            val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            val sslSocketFactory = sslContext.socketFactory
            builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                .hostnameVerifier { _, _ -> true }
        }

        builder.build()
    }

    private val httpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient).apply {
        setUserAgent(userAgent ?: "VLC/3.0.18 LibVLC/3.0.18") // Use VLC User-Agent to bypass strict scraper protections if no real UA is sniffed
        val defaultHeaders = mutableMapOf<String, String>()
        headers.forEach { (k, v) -> 
            val kl = k.lowercase()
            if (kl != "accept-encoding" && kl != "host" && kl != "connection" && kl != "range" && kl != "content-length" && kl != "origin" && !kl.startsWith("sec-")) {
                defaultHeaders[k] = v 
            }
        }
        if (!referer.isNullOrEmpty()) {
            defaultHeaders["Referer"] = referer
        }
        if (defaultHeaders.isNotEmpty()) {
            setDefaultRequestProperties(defaultHeaders)
        }
    }

    private val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)

    val cacheDataSourceFactory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(defaultDataSourceFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    private var prefetchJob: Job? = null

    val smartDataSourceFactory = androidx.media3.datasource.DataSource.Factory {
        val defaultDs = defaultDataSourceFactory.createDataSource()
        val cacheDs = cacheDataSourceFactory.createDataSource()
        object : androidx.media3.datasource.DataSource {
            var isManifest = false
            var currentDs: androidx.media3.datasource.DataSource? = null
            
            override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
                defaultDs.addTransferListener(transferListener)
                cacheDs.addTransferListener(transferListener)
            }
            
            override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
                val uriStr = dataSpec.uri.toString()
                isManifest = uriStr.contains(".m3u8") || uriStr.contains("m3u8") || uriStr.contains(".m3u") || uriStr.contains(".mpd") || uriStr.contains("live")
                currentDs = if (isManifest) defaultDs else cacheDs
                return currentDs!!.open(dataSpec)
            }
            
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                return currentDs!!.read(buffer, offset, length)
            }
            
            override fun getUri(): android.net.Uri? {
                return currentDs?.uri
            }
            
            override fun close() {
                currentDs?.close()
            }
            
            override fun getResponseHeaders(): Map<String, List<String>> {
                return currentDs?.responseHeaders ?: emptyMap()
            }
        }
    }

    @Volatile
    private var pausePreloadUntil = 0L

    private var currentFastCacheMode: Int = 0

    fun notifySeekOccurred() {
        pausePreloadUntil = System.currentTimeMillis() + 800L
    }

    fun buildExoPlayer(fastCacheMode: Int, isLiveStream: Boolean = false): ExoPlayer {
        this.currentFastCacheMode = fastCacheMode
        val loadControlBuilder = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, androidx.media3.common.C.DEFAULT_BUFFER_SEGMENT_SIZE))

        if (isLiveStream) {
            loadControlBuilder
                .setBufferDurationsMs(
                    /* minBufferMs = */ 6000,
                    /* maxBufferMs = */ 20000,
                    /* bufferForPlaybackMs = */ 1000,
                    /* bufferForPlaybackAfterRebufferMs = */ 2000
                )
                .setBackBuffer(0, false)
                .setTargetBufferBytes(androidx.media3.common.C.LENGTH_UNSET)
        } else {
            when (fastCacheMode) {
            0 -> { // Kapalı (Standart Düşük Tampon)
                loadControlBuilder
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 15_000,
                        /* maxBufferMs = */ 50_000,
                        /* bufferForPlaybackMs = */ 500,
                        /* bufferForPlaybackAfterRebufferMs = */ 1000
                    )
                    .setBackBuffer(0, false)
                    .setTargetBufferBytes(androidx.media3.common.C.LENGTH_UNSET)
            }
            1, 2 -> { // Mod 1 & Mod 2 (Arka Plan Disk İndirme & RAM Kotası)
                loadControlBuilder
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 5000,
                        /* maxBufferMs = */ 15000,
                        /* bufferForPlaybackMs = */ 250,
                        /* bufferForPlaybackAfterRebufferMs = */ 500
                    )
                    .setBackBuffer(0, false)
                    .setTargetBufferBytes(ramBufferLimitMb * 1024 * 1024)
                    .setPrioritizeTimeOverSizeThresholds(true)
            }
            3 -> { // Mod 3 (Sıfır Yıpranma - RAM-Only / Diskless)
                loadControlBuilder
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 15_000,
                        /* maxBufferMs = */ 1_800_000, // 30 Dakika ileriye kadar RAM tamponu
                        /* bufferForPlaybackMs = */ 250,
                        /* bufferForPlaybackAfterRebufferMs = */ 500
                    )
                    .setBackBuffer(
                        /* backBufferDurationMs = */ 15_000,
                        /* retainBackBufferFromKeyframe = */ true
                    )
                    .setTargetBufferBytes(ramBufferLimitMb * 1024 * 1024)
                    .setPrioritizeTimeOverSizeThresholds(true)
            }
        }
    }

        val mediaSourceFactory = DefaultMediaSourceFactory(smartDataSourceFactory).setLoadErrorHandlingPolicy(androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(6))
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControlBuilder.build())
            .setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    fun startFastPreload(
        url: String,
        coroutineScope: CoroutineScope,
        getCurrentPositionMs: () -> Long = { 0L },
        getTotalDurationMs: () -> Long = { 0L }
    ) {
        if (currentFastCacheMode == 3) return
        if (url.isBlank() || url.startsWith("file://") || url.startsWith("content://")) return
        if (url.contains("live") || url.contains("m3u8") || url.contains(".m3u")) {
            return // HLS streams manage their own segment buffering natively via ExoPlayer. Aggressive preload breaks them.
        }

        prefetchJob?.cancel()
        prefetchJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                val uri = Uri.parse(url)
                val chunkSize = 5L * 1024 * 1024 // 5 MB chunks

                var consecutiveFailures = 0
                val cacheKey = cacheDataSourceFactory.cacheKeyFactory.buildCacheKey(androidx.media3.datasource.DataSpec(uri))

                while (isActive) {
                    while (isActive && System.currentTimeMillis() < pausePreloadUntil) {
                        kotlinx.coroutines.delay(100)
                    }

                    val totalLength = cache.getContentMetadata(cacheKey).get(androidx.media3.datasource.cache.ContentMetadata.KEY_CONTENT_LENGTH, -1L)
                    val checkLength = if (totalLength > 0) totalLength else Long.MAX_VALUE

                    val curMs = getCurrentPositionMs().coerceAtLeast(0L)
                    val durMs = getTotalDurationMs().coerceAtLeast(0L)

                    val startByte = if (totalLength > 0 && durMs > 0) {
                        ((curMs.toDouble() / durMs.toDouble()) * totalLength).toLong().coerceIn(0L, totalLength)
                    } else {
                        0L
                    }

                    var nextMissingByte = -1L
                    var foundMissing = false

                    // Phase 1: Search from startByte to end of file (Priority Preload from current position)
                    var currentPos = startByte
                    while (currentPos < checkLength) {
                        val cachedLen = cache.getCachedLength(cacheKey, currentPos, checkLength - currentPos)
                        if (cachedLen <= 0L) {
                            nextMissingByte = currentPos
                            foundMissing = true
                            break
                        } else {
                            currentPos += cachedLen
                        }
                    }

                    // Phase 2: If startByte..end is 100% cached, search backward from 0 to startByte (Backward completion)
                    if (!foundMissing && startByte > 0L) {
                        currentPos = 0L
                        while (currentPos < startByte) {
                            val cachedLen = cache.getCachedLength(cacheKey, currentPos, startByte - currentPos)
                            if (cachedLen <= 0L) {
                                nextMissingByte = currentPos
                                foundMissing = true
                                break
                            } else {
                                currentPos += cachedLen
                            }
                        }
                    }

                    if (!foundMissing && totalLength > 0) {
                        // Entire file is cached!
                        kotlinx.coroutines.delay(2000)
                        continue
                    }

                    if (nextMissingByte < 0L) {
                        nextMissingByte = 0L
                    }

                    val requestLength = if (totalLength > 0) minOf(chunkSize, totalLength - nextMissingByte) else chunkSize
                    if (requestLength <= 0L) {
                        kotlinx.coroutines.delay(1000)
                        continue
                    }

                    val dataSpec = androidx.media3.datasource.DataSpec.Builder()
                        .setUri(uri)
                        .setPosition(nextMissingByte)
                        .setLength(requestLength)
                        .setFlags(androidx.media3.datasource.DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
                        .build()

                    val dataSource = cacheDataSourceFactory.createDataSource()
                    val cacheWriter = CacheWriter(
                        dataSource,
                        dataSpec,
                        ByteArray(256 * 1024),
                        androidx.media3.datasource.cache.CacheWriter.ProgressListener { _, _, newBytesCached ->
                            if (newBytesCached > 0) {
                                consecutiveFailures = 0 
                            }
                        }
                    )

                    try {
                        cacheWriter.cache()
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        if (e is java.io.InterruptedIOException || e is InterruptedException) continue
                        consecutiveFailures++
                        if (consecutiveFailures > 5) {
                            kotlinx.coroutines.delay(5000)
                        } else {
                            kotlinx.coroutines.delay(1000)
                        }
                    }
                }
            } catch (e: Exception) {
                // Background preload completed or cancelled
            }
        }
    }

    
    data class CacheSpanRange(val startRatio: Float, val endRatio: Float)

    fun getCachedPercentage(url: String): Float {
        if (url.isBlank()) return 0f
        val uri = android.net.Uri.parse(url)
        val cacheKey = cacheDataSourceFactory.cacheKeyFactory.buildCacheKey(androidx.media3.datasource.DataSpec(uri))
        val totalLength = cache.getContentMetadata(cacheKey).get(androidx.media3.datasource.cache.ContentMetadata.KEY_CONTENT_LENGTH, -1L)
        if (totalLength <= 0) return 0f
        
        var cachedLen = 0L
        val spans = cache.getCachedSpans(cacheKey)
        for (span in spans) {
            cachedLen += span.length
        }
        return (cachedLen.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
    }

    fun getContiguousCachedRatio(url: String): Float {
        if (url.isBlank()) return 0f
        try {
            val uri = android.net.Uri.parse(url)
            val cacheKey = cacheDataSourceFactory.cacheKeyFactory.buildCacheKey(androidx.media3.datasource.DataSpec(uri))
            val totalLength = cache.getContentMetadata(cacheKey).get(androidx.media3.datasource.cache.ContentMetadata.KEY_CONTENT_LENGTH, -1L)
            if (totalLength <= 0) return 0f

            val spans = cache.getCachedSpans(cacheKey).sortedBy { it.position }
            if (spans.isEmpty()) return 0f

            var contiguousEnd = 0L
            for (span in spans) {
                if (span.position <= contiguousEnd + 256 * 1024L) { // allow small gap or contiguous
                    val end = span.position + span.length
                    if (end > contiguousEnd) {
                        contiguousEnd = end
                    }
                } else if (span.position > contiguousEnd) {
                    break
                }
            }
            return (contiguousEnd.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
        } catch (_: Exception) {
            return 0f
        }
    }

    fun getCachedSpansRatios(url: String): List<CacheSpanRange> {
        if (url.isBlank()) return emptyList()
        try {
            val uri = android.net.Uri.parse(url)
            val cacheKey = cacheDataSourceFactory.cacheKeyFactory.buildCacheKey(androidx.media3.datasource.DataSpec(uri))
            val totalLength = cache.getContentMetadata(cacheKey).get(androidx.media3.datasource.cache.ContentMetadata.KEY_CONTENT_LENGTH, -1L)
            if (totalLength <= 0) return emptyList()
            
            val spans = cache.getCachedSpans(cacheKey).sortedBy { it.position }
            if (spans.isEmpty()) return emptyList()

            val mergedSpans = mutableListOf<CacheSpanRange>()
            var currentStart = spans[0].position
            var currentEnd = spans[0].position + spans[0].length

            for (i in 1 until spans.size) {
                val span = spans[i]
                if (span.position <= currentEnd) {
                    currentEnd = maxOf(currentEnd, span.position + span.length)
                } else {
                    val sRatio = (currentStart.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
                    val eRatio = (currentEnd.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
                    if (eRatio > sRatio) {
                        mergedSpans.add(CacheSpanRange(sRatio, eRatio))
                    }
                    currentStart = span.position
                    currentEnd = span.position + span.length
                }
            }
            val sRatio = (currentStart.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
            val eRatio = (currentEnd.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
            if (eRatio > sRatio) {
                mergedSpans.add(CacheSpanRange(sRatio, eRatio))
            }
            return mergedSpans
        } catch (_: Exception) {
            return emptyList()
        }
    }

    fun releaseAndCleanUp() {
        try {
            prefetchJob?.cancel()
        } catch (_: Exception) {}

        try {
            cache.release()
        } catch (_: Exception) {}

        try {
            sessionDir.deleteRecursively()
        } catch (_: Exception) {}
    }

    companion object {
        fun cleanAllOldSessions(context: Context) {
            try {
                val parentDir = File(context.cacheDir, "player_sessions")
                if (parentDir.exists()) {
                    parentDir.deleteRecursively()
                }
            } catch (_: Exception) {}
        }
    }
}
