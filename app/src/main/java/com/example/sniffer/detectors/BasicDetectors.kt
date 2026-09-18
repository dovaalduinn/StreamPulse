package com.example.sniffer.detectors

import com.example.sniffer.MediaCandidate
import com.example.sniffer.MediaSourceType

class DomMediaDetector : MediaDetector {
    override fun canHandle(sourceType: MediaSourceType, url: String, mimeType: String?): Boolean {
        return sourceType == MediaSourceType.DOM_VIDEO || sourceType == MediaSourceType.DOM_SOURCE
    }

    override fun detect(sourceType: MediaSourceType, url: String, title: String, duration: String, quality: String, mimeType: String?): MediaCandidate? {
        if (url.isBlank() || url.startsWith("blob:")) return null
        return MediaCandidate(
            url = url,
            source = sourceType,
            type = if (mimeType?.contains("audio") == true) "Audio" else "Video",
            title = title,
            durationStr = duration,
            quality = quality,
            mimeType = mimeType
        )
    }
}

class NetworkMediaDetector : MediaDetector {
    override fun canHandle(sourceType: MediaSourceType, url: String, mimeType: String?): Boolean {
        return sourceType == MediaSourceType.WEBVIEW_NETWORK
    }

    override fun detect(sourceType: MediaSourceType, url: String, title: String, duration: String, quality: String, mimeType: String?): MediaCandidate? {
        if (url.isBlank() || url.startsWith("blob:")) return null
        return MediaCandidate(
            url = url,
            source = sourceType,
            title = title,
            mimeType = mimeType
        )
    }
}

class XhrFetchDetector : MediaDetector {
    override fun canHandle(sourceType: MediaSourceType, url: String, mimeType: String?): Boolean {
        return sourceType == MediaSourceType.XHR || sourceType == MediaSourceType.FETCH
    }

    override fun detect(sourceType: MediaSourceType, url: String, title: String, duration: String, quality: String, mimeType: String?): MediaCandidate? {
        if (url.isBlank() || url.startsWith("blob:")) return null
        return MediaCandidate(
            url = url,
            source = sourceType,
            title = title,
            mimeType = mimeType
        )
    }
}

class PerformanceResourceDetector : MediaDetector {
    override fun canHandle(sourceType: MediaSourceType, url: String, mimeType: String?): Boolean {
        return sourceType == MediaSourceType.PERFORMANCE
    }

    override fun detect(sourceType: MediaSourceType, url: String, title: String, duration: String, quality: String, mimeType: String?): MediaCandidate? {
        if (url.isBlank() || url.startsWith("blob:")) return null
        return MediaCandidate(
            url = url,
            source = sourceType,
            title = title,
            mimeType = mimeType
        )
    }
}

class JsonMediaDetector : MediaDetector {
    override fun canHandle(sourceType: MediaSourceType, url: String, mimeType: String?): Boolean {
        return sourceType == MediaSourceType.JSON
    }

    override fun detect(sourceType: MediaSourceType, url: String, title: String, duration: String, quality: String, mimeType: String?): MediaCandidate? {
        if (url.isBlank() || url.startsWith("blob:")) return null
        return MediaCandidate(
            url = url,
            source = sourceType,
            title = title,
            mimeType = mimeType
        )
    }
}

class SubtitleDetector : MediaDetector {
    override fun canHandle(sourceType: MediaSourceType, url: String, mimeType: String?): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".vtt") || lower.endsWith(".srt") || lower.endsWith(".ass") || mimeType?.contains("text/vtt") == true
    }

    override fun detect(sourceType: MediaSourceType, url: String, title: String, duration: String, quality: String, mimeType: String?): MediaCandidate? {
        return MediaCandidate(
            url = url,
            source = sourceType,
            type = "Subtitle",
            title = title,
            language = duration.takeIf { it.isNotBlank() } ?: "Unknown"
        )
    }
}
