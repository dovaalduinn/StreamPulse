package com.example.sniffer.engines

import com.example.sniffer.MediaCandidate

class MediaIdentityEngine {

    fun generateIdentity(candidate: MediaCandidate): String {
        return try {
            val uri = java.net.URI(candidate.url)
            val host = uri.host?.lowercase() ?: ""
            val path = uri.path ?: ""
            // Ignore token query parameters for identity
            val cleanQuery = uri.query?.split("&")?.filter { 
                !it.lowercase().startsWith("token=") && 
                !it.lowercase().startsWith("sig") &&
                !it.lowercase().startsWith("expires") &&
                !it.lowercase().startsWith("auth") &&
                !it.lowercase().startsWith("hdnea")
            }?.joinToString("&") ?: ""
            
            "$host$path?$cleanQuery"
        } catch (e: Exception) {
            candidate.url
        }
    }
}
