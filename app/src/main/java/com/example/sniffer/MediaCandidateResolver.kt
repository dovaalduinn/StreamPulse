package com.example.sniffer

class MediaCandidateResolver {

    fun resolve(candidate: MediaCandidate): MediaCandidate {
        var resolved = candidate
        val lowerUrl = resolved.url.lowercase()

        // Type Resolution
        if (resolved.type == "Unknown" || resolved.type == "Video") {
            if (lowerUrl.contains(".m3u8") || resolved.mimeType?.contains("mpegurl") == true) {
                resolved = resolved.copy(type = "HLS", isAdaptive = true)
            } else if (lowerUrl.contains(".mpd") || resolved.mimeType?.contains("dash") == true) {
                resolved = resolved.copy(type = "DASH", isAdaptive = true)
            } else if (lowerUrl.contains(".mp4") || resolved.mimeType?.contains("mp4") == true) {
                resolved = resolved.copy(type = "MP4")
            } else if (lowerUrl.contains(".webm") || resolved.mimeType?.contains("webm") == true) {
                resolved = resolved.copy(type = "WebM")
            } else if (lowerUrl.endsWith(".vtt") || lowerUrl.endsWith(".srt")) {
                resolved = resolved.copy(type = "Subtitle")
            }
        }

        // Segment exclusion (M4S, TS alone are not standalone playable)
        if (lowerUrl.endsWith(".m4s") || lowerUrl.contains(".m4s?") || 
            lowerUrl.endsWith(".ts") || lowerUrl.contains(".ts?")) {
            resolved.isResolvable = false
        }
        
        // Blobs
        if (lowerUrl.startsWith("blob:")) {
            resolved.isResolvable = false
        }
        
        // Iframes and JSON endpoints are not playable media themselves
        if (resolved.source == MediaSourceType.IFRAME || resolved.source == MediaSourceType.JSON) {
            // Unless they successfully resolved to a concrete media type like HLS/MP4
            if (resolved.type == "Unknown" || resolved.type == "Video") {
                resolved.isResolvable = false
            }
        }

        return resolved
    }
}
