package com.example.sniffer.adapters

import com.example.sniffer.MediaCandidate
import com.example.sniffer.MediaSourceType

class OkRuAdapter : PlatformAdapter {
    override fun canHandle(url: String): Boolean {
        return url.contains("ok.ru")
    }

    override fun getPlatformName(): String = "OK.ru"

    override fun extractMetadata(candidate: MediaCandidate): MediaCandidate? {
        // Just tag it for now. Correlation engine does the heavy lifting for MSE/blob
        if (candidate.source == MediaSourceType.IFRAME && candidate.url.contains("ok.ru/videoembed")) {
            return candidate.copy(title = "OK.ru Player")
        }
        return candidate
    }
}
