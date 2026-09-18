package com.example.sniffer.engines

import com.example.sniffer.MediaCandidate
import com.example.sniffer.MediaSourceType

data class MediaActivityContext(
    val mediaSourceId: String,
    val sourceBufferIds: MutableSet<String> = mutableSetOf(),
    val mimeTypes: MutableSet<String> = mutableSetOf(),
    var startedAt: Long = System.currentTimeMillis(),
    var lastActivityAt: Long = System.currentTimeMillis(),
    var totalBytesAppended: Long = 0
)

class MediaCorrelationEngine {

    private val networkEvents = mutableListOf<MediaCandidate>()
    private val mseEvents = mutableListOf<MediaCandidate>()
    private val blobEvents = mutableListOf<MediaCandidate>()
    private val jsonEvents = mutableListOf<MediaCandidate>()
    
    private val activeContexts = mutableMapOf<String, MediaActivityContext>()

    @Synchronized
    fun process(candidate: MediaCandidate): MediaCandidate {
        // Limit list sizes to prevent memory leaks (max 500 network events)
        if (networkEvents.size > 500) networkEvents.removeAt(0)
        if (mseEvents.size > 200) mseEvents.removeAt(0)
        if (blobEvents.size > 100) blobEvents.removeAt(0)
        if (jsonEvents.size > 200) jsonEvents.removeAt(0)

        // Clean up old events (30 seconds window)
        val now = System.currentTimeMillis()
        networkEvents.removeAll { now - it.discoveredAt > 30000 }
        
        // Context Tracking
        if (candidate.mediaSourceId != null) {
            val ctx = activeContexts.getOrPut(candidate.mediaSourceId) { MediaActivityContext(candidate.mediaSourceId) }
            ctx.lastActivityAt = now
            if (candidate.sourceBufferId != null) ctx.sourceBufferIds.add(candidate.sourceBufferId)
            if (candidate.mimeType != null) ctx.mimeTypes.add(candidate.mimeType)
            if (candidate.byteLength != null) ctx.totalBytesAppended += candidate.byteLength
        }
        
        // Clean up old contexts
        activeContexts.entries.removeAll { now - it.value.lastActivityAt > 60000 }

        // Segment filtering for standalone UI rejection
        val urlL = candidate.url.lowercase()
        val isSegment = urlL.contains(".ts") || urlL.contains(".m4s") || 
                        urlL.contains(".cmfv") || urlL.contains(".cmfa") ||
                        urlL.contains("segment") || urlL.contains("chunk") ||
                        Regex(""".*_\d+\.(mp4|aac|m4a)$""").matches(urlL) ||
                        Regex(""".*\d+-\d+\.(mp4|aac|m4a)$""").matches(urlL)
        
        when (candidate.source) {
            MediaSourceType.MSE, MediaSourceType.SOURCE_BUFFER -> mseEvents.add(candidate)
            MediaSourceType.BLOB -> blobEvents.add(candidate)
            MediaSourceType.JSON, MediaSourceType.PLAYER_METADATA -> jsonEvents.add(candidate)
            MediaSourceType.WEBVIEW_NETWORK, MediaSourceType.XHR, MediaSourceType.FETCH, MediaSourceType.PERFORMANCE, MediaSourceType.WEBSOCKET -> networkEvents.add(candidate)
            else -> {}
        }
        
        var correlated = candidate
        
        // Use explicitly passed JS correlated URL if available (Direct 1:1 mapping)
        if (candidate.correlationContext != null && candidate.correlationContext!!.startsWith("http")) {
             val explicitNetwork = networkEvents.find { it.url == candidate.correlationContext }
             if (explicitNetwork != null) {
                 return explicitNetwork.copy(
                     source = MediaSourceType.MSE,
                     isResolvable = true,
                     correlationContext = "JS_EXPLICIT_CORRELATION"
                 )
             }
        }
        
        // If it's a BLOB, MSE, or SOURCE_BUFFER, we don't return it as resolvable directly.
        // We try to find the most recent network event that correlates.
        if (candidate.source == MediaSourceType.BLOB || candidate.source == MediaSourceType.MSE || candidate.source == MediaSourceType.SOURCE_BUFFER) {
            
            // Bypass for high volume / token
            if (candidate.correlationContext == "high_volume" || candidate.correlationContext == "token_extraction") {
                return candidate.copy(isResolvable = true)
            }
            
            // First pass: Direct JS correlatedUrl check
            if (!candidate.correlatedUrl.isNullOrEmpty()) {
                val directMatch = networkEvents.find { it.url == candidate.correlatedUrl }
                if (directMatch != null) {
                    return directMatch.copy(
                        source = MediaSourceType.MSE,
                        isResolvable = true,
                        correlationContext = "JS_EXPLICIT_CORRELATION"
                    )
                }
            }
                        
            // Correlation window: ± 15 seconds
            val recentNetwork = networkEvents.filter { 
                Math.abs(it.discoveredAt - now) < 15000 
            }

            var bestNetwork: MediaCandidate? = null
            var bestScore = -1

            for (net in recentNetwork) {
                // Extension checks
                val isManifest = net.url.contains(".m3u8") || net.url.contains(".mpd")
                val isProgressive = net.url.contains(".mp4") || net.url.contains(".webm") || net.url.contains(".mkv")
                
                // Header / MIME checks
                val isMediaMime = net.mimeType?.contains("video") == true || net.mimeType?.contains("audio") == true || net.mimeType?.contains("mpegurl") == true || net.mimeType?.contains("dash") == true || net.mimeType?.contains("octet-stream") == true
                
                // Range header check
                val hasRange = net.headers.keys.any { it.equals("Range", ignoreCase = true) }
                
                // Sec-Fetch-Dest
                val fetchDest = net.headers.entries.firstOrNull { it.key.equals("Sec-Fetch-Dest", ignoreCase = true) }?.value ?: ""
                val isDestMedia = fetchDest.contains("video") || fetchDest.contains("audio")
                
                // Segment check
                val isNetSegment = net.url.contains(".ts") || net.url.contains(".m4s") || net.url.contains("segment") || net.url.contains("chunk") || net.url.contains(".cmfv") || net.url.contains(".cmfa")
                
                var score = 0
                
                if (isMediaMime) {
                    if (net.mimeType?.contains("video") == true) score += 50
                    else if (net.mimeType?.contains("audio") == true) score += 45
                    else score += 20
                }
                
                if (hasRange) score += 25
                if (isDestMedia) score += 30
                
                if (net.source == MediaSourceType.FETCH || net.source == MediaSourceType.XHR) score += 10
                
                if (isManifest) score += 40
                if (isNetSegment) score -= 20
                
                // MSE Correlation Boost
                val isMseActive = activeContexts.values.any { now - it.lastActivityAt < 5000 }
                if (isMseActive) score += 30
                
                // Same Host / Frame boost
                try {
                    val cUri = java.net.URI(candidate.url)
                    val nUri = java.net.URI(net.url)
                    if (cUri.host != null && cUri.host == nUri.host) score += 10
                } catch(e: Exception) {}
                
                if (candidate.iframeUrl != null && candidate.iframeUrl == net.iframeUrl) score += 15

                // Accept candidate if it has a decent score (e.g., progressive stream without extension but with video mime)
                if (score > 10 && (isManifest || isProgressive || isMediaMime || hasRange || isDestMedia)) {
                    if (score > bestScore) {
                        bestScore = score
                        bestNetwork = net
                    }
                }
            }

            if (bestNetwork != null) {
                // Return the network request as the correlated candidate
                correlated = bestNetwork.copy(
                    source = MediaSourceType.MSE, // Mark as correlated
                    isResolvable = true,
                    correlationContext = "BLOB_MSE_NETWORK"
                )
            } else {
                correlated.isResolvable = false
            }
        } else if (isSegment) {
             correlated.isResolvable = false
        }
        
        return correlated
    }
    
    @Synchronized
    fun clear() {
        networkEvents.clear()
        mseEvents.clear()
        blobEvents.clear()
        jsonEvents.clear()
        activeContexts.clear()
    }
}
