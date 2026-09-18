package com.example.sniffer

enum class SnifferConfidence {
    VERY_HIGH, HIGH, MEDIUM, LOW
}

enum class MediaSourceType {
    DOM_VIDEO, DOM_SOURCE, XHR, FETCH, PERFORMANCE, WEBVIEW_NETWORK, MSE, SOURCE_BUFFER, BLOB, IFRAME, JSON, PLAYER_METADATA, PLATFORM_ADAPTER, WEBSOCKET
}

data class MediaCandidate(
    val url: String,
    val originalUrl: String = url,
    val type: String = "Unknown", // VIDEO, AUDIO, SUBTITLE, HLS, DASH, MP4, WebM
    val container: String? = null,
    val mimeType: String? = null,
    val codec: String? = null,
    val quality: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val bitrate: Long? = null,
    val fps: Float? = null,
    val language: String? = null,
    val durationStr: String? = null,
    val title: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val source: MediaSourceType,
    var confidence: SnifferConfidence = SnifferConfidence.MEDIUM,
    var score: Int = 0,
    val isLive: Boolean = false,
    val isAdaptive: Boolean = false,
    val isEncrypted: Boolean = false,
    var isResolvable: Boolean = true,
    val manifestUrl: String? = null,
    val parentUrl: String? = null,
    val iframeUrl: String? = null,
    val referer: String? = null,
    val userAgent: String? = null,
    val discoveredAt: Long = System.currentTimeMillis(),
    
    // NEW CORRELATION FIELDS
    val mediaSourceId: String? = null,
    val sourceBufferId: String? = null,
    val byteLength: Long? = null,
    val topPageUrl: String? = null,
    val initiatorType: String? = null,
    val contentLength: Long? = null,
    var correlationContext: String? = null,
    val correlatedUrl: String? = null
) {
    fun toDisplayString(): String {
        return "$quality • $type • $confidence"
    }
}
