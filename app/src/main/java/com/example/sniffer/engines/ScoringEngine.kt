package com.example.sniffer.engines

import com.example.sniffer.MediaCandidate
import com.example.sniffer.MediaSourceType
import com.example.sniffer.SnifferConfidence

object ScoringEngine {
    fun score(candidate: MediaCandidate): MediaCandidate {
        var score = candidate.score
        val urlLower = candidate.url.lowercase()
        val cleanLower = urlLower.substringBefore("?")
        val mimeLower = candidate.mimeType?.lowercase() ?: ""

        // Media MIME types
        if (mimeLower.startsWith("video/")) score += 100
        if (mimeLower.contains("mpegurl") || mimeLower.contains("x-mpegurl")) score += 100
        if (mimeLower.contains("dash+xml")) score += 100
        if (mimeLower.startsWith("audio/")) score += 50
        
        // Extensions
        if (cleanLower.endsWith(".m3u8")) score += 90
        if (cleanLower.endsWith(".mpd")) score += 90
        if (cleanLower.endsWith(".mp4")) score += 80
        if (cleanLower.endsWith(".webm") || cleanLower.endsWith(".mkv")) score += 60

        // Headers
        if (candidate.headers["Sec-Fetch-Dest"]?.lowercase() == "video") score += 70
        if (candidate.headers["Range"]?.lowercase()?.startsWith("bytes=") == true) score += 60

        // XHR/Fetch specific
        if (candidate.source == MediaSourceType.XHR || candidate.source == MediaSourceType.FETCH) {
            val respType = candidate.correlationContext?.lowercase() ?: ""
            if (respType.contains("arraybuffer") || respType.contains("blob")) score += 50
            if (candidate.byteLength != null && candidate.byteLength > 1024 * 1024) score += 40 // >1MB
        }
        
        // Byte length general
        if (candidate.byteLength != null && candidate.byteLength > 2 * 1024 * 1024) score += 40 // >2MB
        if (candidate.contentLength != null && candidate.contentLength > 2 * 1024 * 1024) score += 40

        // Correlations
        if (candidate.source == MediaSourceType.MSE || candidate.source == MediaSourceType.SOURCE_BUFFER || candidate.correlationContext?.contains("mse") == true) score += 40
        if (candidate.source == MediaSourceType.DOM_VIDEO) score += 30

        // Negatives
        if (mimeLower.startsWith("image/") || cleanLower.endsWith(".jpg") || cleanLower.endsWith(".png") || cleanLower.endsWith(".gif") || cleanLower.endsWith(".webp") || cleanLower.endsWith(".svg")) score -= 100
        if (mimeLower.contains("css") || cleanLower.endsWith(".css")) score -= 100
        if (mimeLower.contains("javascript") || cleanLower.endsWith(".js")) score -= 100
        if (mimeLower.contains("font") || cleanLower.endsWith(".woff") || cleanLower.endsWith(".woff2") || cleanLower.endsWith(".ttf")) score -= 100
        if (urlLower.contains("tracking") || urlLower.contains("analytics") || urlLower.contains("pixel") || urlLower.contains("scorecardresearch")) score -= 80
        
        if (mimeLower.contains("json") && (candidate.byteLength ?: 0) < 50 * 1024) score -= 50
        if (mimeLower.contains("text/html")) score -= 50

        // Segment grouping bonus
        if (candidate.correlationContext == "segment_family") score += 20
        if (candidate.correlationContext == "high_volume") score += 100 // Extracted from high volume MSE append
        if (candidate.correlationContext == "token_extraction") score += 90 // Decoded from custom player setup
        if (candidate.source == MediaSourceType.WEBSOCKET && (candidate.byteLength ?: 0) > 50000) score += 90 // Binary WebSocket
        

        val confidence = when {
            score >= 100 -> SnifferConfidence.VERY_HIGH
            score >= 70 -> SnifferConfidence.HIGH
            score >= 40 -> SnifferConfidence.MEDIUM
            else -> SnifferConfidence.LOW
        }

        return candidate.copy(score = score, confidence = confidence)
    }
}
