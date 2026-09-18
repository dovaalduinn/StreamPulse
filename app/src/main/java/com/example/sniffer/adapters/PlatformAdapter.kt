package com.example.sniffer.adapters

import com.example.sniffer.MediaCandidate

interface PlatformAdapter {
    fun canHandle(url: String): Boolean
    fun getPlatformName(): String
    fun extractMetadata(candidate: MediaCandidate): MediaCandidate?
}
