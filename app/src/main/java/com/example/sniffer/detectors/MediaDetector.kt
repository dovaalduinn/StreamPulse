package com.example.sniffer.detectors

import com.example.sniffer.MediaCandidate
import com.example.sniffer.MediaSourceType

interface MediaDetector {
    fun canHandle(sourceType: MediaSourceType, url: String, mimeType: String?): Boolean
    fun detect(sourceType: MediaSourceType, url: String, title: String, duration: String, quality: String, mimeType: String?): MediaCandidate?
}
