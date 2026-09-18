package com.example.sniffer

import com.example.sniffer.detectors.*
import com.example.sniffer.engines.*
import com.example.sniffer.adapters.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class StreamSnifferManager {

    private fun maskTokens(url: String): String {
        return url.replace(Regex("(?<=[?&]token=)[^&]+"), "***")
                  .replace(Regex("(?<=[?&]sig=)[^&]+"), "***")
                  .replace(Regex("(?<=[?&]signature=)[^&]+"), "***")
                  .replace(Regex("(?<=[?&]key=)[^&]+"), "***")
    }

    private fun logDebugEvent(candidate: MediaCandidate) {
        if (com.example.BuildConfig.DEBUG) {
            val maskedUrl = maskTokens(candidate.url)
            val sourceType = candidate.source.name
            android.util.Log.d("SNIFFER", "[$sourceType] ${candidate.mimeType ?: ""} -> $maskedUrl")
        }
    }

    private val _candidates = MutableStateFlow<List<MediaCandidate>>(emptyList())
    val candidates: StateFlow<List<MediaCandidate>> = _candidates.asStateFlow()

    private val knownIdentities = ConcurrentHashMap<String, Boolean>()
    private val resolver = MediaCandidateResolver()
    private val asyncResolver = AsyncMetadataResolver()
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val correlationEngine = MediaCorrelationEngine()
    private val identityEngine = MediaIdentityEngine()
    
    private val platformAdapters = listOf(OkRuAdapter())
    
    private val detectors = listOf(
        SubtitleDetector(),
        DomMediaDetector(),
        NetworkMediaDetector(),
        XhrFetchDetector(),
        PerformanceResourceDetector(),
        JsonMediaDetector()
    )

    private val MAX_CANDIDATES = 200

    fun clear() {
        _candidates.value = emptyList()
        knownIdentities.clear()
        correlationEngine.clear()
    }
    
    fun destroy() {
        clear()
        managerScope.coroutineContext.cancelChildren()
    }

    @Synchronized
    fun parseRawEvent(
        sourceType: String, 
        url: String, 
        type: String = "Unknown", 
        title: String = "", 
        duration: String = "", 
        quality: String = "", 
        mimeType: String? = null,
        isMse: Boolean = false,
        isBlob: Boolean = false,
        referer: String? = null,
        userAgent: String? = null,
        headers: Map<String, String> = emptyMap(),
        mediaSourceId: String? = null,
        sourceBufferId: String? = null,
        byteLength: Long? = null,
        topPageUrl: String? = null,
        initiatorType: String? = null,
        iframeUrl: String? = null
    , correlatedUrl: String? = null) {
        val source = try { MediaSourceType.valueOf(sourceType) } catch (e: Exception) { MediaSourceType.WEBVIEW_NETWORK }
        
        var bestCandidate: MediaCandidate? = null
        
        for (detector in detectors) {
            try {
                if (detector.canHandle(source, url, mimeType)) {
                    val candidate = detector.detect(source, url, title, duration, quality, mimeType)
                    if (candidate != null) {
                        bestCandidate = candidate.copy(
                            referer = referer, 
                            userAgent = userAgent, 
                            headers = headers,
                            mediaSourceId = mediaSourceId,
                            sourceBufferId = sourceBufferId,
                            byteLength = byteLength,
                            topPageUrl = topPageUrl,
                            initiatorType = initiatorType,
                            iframeUrl = iframeUrl,
                            correlatedUrl = correlatedUrl
                        )
                        break
                    }
                }
            } catch(e: Exception) {}
        }
        
        if (bestCandidate == null) {
            bestCandidate = MediaCandidate(
                url = url,
                source = source,
                type = type,
                title = title,
                durationStr = duration,
                quality = quality,
                mimeType = mimeType,
                isResolvable = !(isBlob || source == MediaSourceType.MSE || source == MediaSourceType.SOURCE_BUFFER || url.startsWith("blob:")),
                referer = referer,
                userAgent = userAgent,
                headers = headers,
                mediaSourceId = mediaSourceId,
                sourceBufferId = sourceBufferId,
                byteLength = byteLength,
                topPageUrl = topPageUrl,
                initiatorType = initiatorType,
                iframeUrl = iframeUrl,
                correlatedUrl = correlatedUrl
            )
        } else {
            bestCandidate = bestCandidate.copy(
                isResolvable = !(isBlob || source == MediaSourceType.MSE || source == MediaSourceType.SOURCE_BUFFER || url.startsWith("blob:"))
            )
        }

        var finalCandidate = bestCandidate!!
        
        for (adapter in platformAdapters) {
            try {
                if (adapter.canHandle(finalCandidate.url)) {
                    finalCandidate = adapter.extractMetadata(finalCandidate) ?: finalCandidate
                }
            } catch(e: Exception) {}
        }

        addCandidate(finalCandidate)
    }

    @Synchronized
    private fun addCandidate(candidate: MediaCandidate) {
        if (isAdOrTrackingUrl(candidate.url)) return

        // 1. Correlate (e.g. Blob -> Network Manifest)
        val correlated = correlationEngine.process(candidate)
        
        // 2. Resolve (Type, Segments)
        
        // 2. Resolve (Type, Segments)
        val resolved = resolver.resolve(correlated)
        logDebugEvent(resolved)

        android.util.Log.d("SNIFFER_DEBUG", "Received candidate: ${resolved.url}")
        
        if (!resolved.isResolvable) return
        
        // 3. Deduplicate
        val identity = identityEngine.generateIdentity(resolved)
        if (knownIdentities.containsKey(identity)) {
            // Update existing candidate if the new one has better metadata
            val currentList = _candidates.value.toMutableList()
            val index = currentList.indexOfFirst { identityEngine.generateIdentity(it) == identity }
            if (index != -1) {
                val existing = currentList[index]
                var changed = false
                
                var newQuality = existing.quality
                if (existing.quality.isNullOrBlank() && !resolved.quality.isNullOrBlank()) {
                    newQuality = resolved.quality
                    changed = true
                }
                
                var newDuration = existing.durationStr
                if ((existing.durationStr.isNullOrBlank() || existing.durationStr == "Canlı Yayın") && !resolved.durationStr.isNullOrBlank()) {
                    newDuration = resolved.durationStr
                    changed = true
                }
                
                var newTitle = existing.title
                if (existing.title.isNullOrBlank() && !resolved.title.isNullOrBlank()) {
                    newTitle = resolved.title
                    changed = true
                }

                if (changed) {
                    currentList[index] = existing.copy(
                        quality = newQuality,
                        durationStr = newDuration,
                        title = newTitle
                    )
                    _candidates.value = currentList
                }
            }
            return
        }
        knownIdentities[identity] = true

        // 4. Score
        val scoredCandidate = ScoringEngine.score(resolved)
        if (scoredCandidate.confidence == SnifferConfidence.LOW && scoredCandidate.type == "Unknown") return

        // 5. Add to UI and Async resolve
        val currentList = _candidates.value.toMutableList()
        
        // --- HLS Deduplication (Master Prioritization) ---
        var shouldAdd = true
        val urlLower = scoredCandidate.url.lowercase()
        val isHls = urlLower.contains(".m3u8") || scoredCandidate.type == "HLS"
        
        if (isHls) {
            val lastSlash = scoredCandidate.url.lastIndexOf("/")
            if (lastSlash != -1) {
                val baseUrl = scoredCandidate.url.substring(0, lastSlash + 1)
                val fileName = urlLower.substring(lastSlash + 1).substringBefore("?")
                
                val isMaster = fileName.contains("master") || fileName.contains("index") || fileName.contains("playlist")
                
                if (isMaster) {
                    // Remove all existing variants from this baseUrl
                    val iterator = currentList.iterator()
                    while (iterator.hasNext()) {
                        val existing = iterator.next()
                        if (existing.url.startsWith(baseUrl)) {
                            val existingFileName = existing.url.lowercase().substringAfterLast("/").substringBefore("?")
                            val existingIsMaster = existingFileName.contains("master") || existingFileName.contains("index") || existingFileName.contains("playlist")
                            if (!existingIsMaster) {
                                iterator.remove()
                            }
                        }
                    }
                } else {
                    // We are a variant. Check if a master already exists.
                    val masterExists = currentList.any { 
                        it.url.startsWith(baseUrl) && 
                        (it.url.lowercase().substringAfterLast("/").substringBefore("?").run {
                            contains("master") || contains("index") || contains("playlist")
                        })
                    }
                    if (masterExists) {
                        shouldAdd = false
                    }
                }
            }
        }
        
        if (!shouldAdd) return

        if (currentList.size < MAX_CANDIDATES) {
            android.util.Log.d("SNIFFER_DEBUG", "Added to list: ${scoredCandidate.url}")
            currentList.add(0, scoredCandidate)
            _candidates.value = currentList
            
            // Background metadata resolving (HLS/DASH manifest fetching)
            managerScope.launch {
                try {
                    val enriched = asyncResolver.enrichMetadata(scoredCandidate)
                    if (enriched != scoredCandidate) {
                        updateCandidate(enriched)
                    }
                } catch(e: Exception) {}
            }
        }
    }

    private fun updateCandidate(updated: MediaCandidate) {
        val currentList = _candidates.value.toMutableList()
        val index = currentList.indexOfFirst { it.originalUrl == updated.originalUrl }
        if (index != -1) {
            currentList[index] = updated
            _candidates.value = currentList
        }
    }

    private fun isAdOrTrackingUrl(url: String): Boolean {
        val lower = url.lowercase()
        val cleanLower = lower.substringBefore("?")
        
        return cleanLower.endsWith(".js") || cleanLower.endsWith(".css") || cleanLower.endsWith(".png") ||
               cleanLower.endsWith(".jpg") || cleanLower.endsWith(".webp") || cleanLower.endsWith(".gif") ||
               cleanLower.endsWith(".svg") || cleanLower.endsWith(".woff") || cleanLower.endsWith(".woff2") ||
               lower.contains("google-analytics") || lower.contains("doubleclick") ||
               lower.contains("googlesyndication") || lower.contains("tracking") ||
               lower.contains("analytics") || lower.contains("pixel") || lower.contains("favicon") ||
               lower.contains("jwpltx.com") || lower.contains("ping.gif") || lower.contains("scorecardresearch")
    }

    }