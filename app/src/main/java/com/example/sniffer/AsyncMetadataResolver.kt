package com.example.sniffer

import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

class AsyncMetadataResolver {

    suspend fun enrichMetadata(candidate: MediaCandidate): MediaCandidate = withContext(Dispatchers.IO) {
        var updated = candidate
        val urlLower = candidate.url.lowercase()

        try {
            if (candidate.type == "HLS" || urlLower.contains(".m3u8")) {
                updated = resolveHls(updated)
            } else if (urlLower.contains(".mp4") || urlLower.contains(".mkv") || urlLower.contains(".webm")) {
                updated = resolveStandardMedia(updated)
            }
        } catch (e: Exception) {
            // Hata olursa (timeout vb.) orjinal veriyi koru
        }
        
        // Hala süre bulunamadıysa ve Canlı Yayın olduğu tespit edildiyse etiketi yapıştır
        if (updated.durationStr.isNullOrBlank() && updated.isLive) {
            updated = updated.copy(durationStr = "Canlı Yayın")
        }

        return@withContext updated
    }

    private fun fetchContent(urlString: String, candidate: MediaCandidate? = null): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.setRequestProperty("User-Agent", candidate?.userAgent ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
            if (candidate?.referer != null) {
                connection.setRequestProperty("Referer", candidate.referer)
                try {
                    val originUri = java.net.URI(candidate.referer)
                    connection.setRequestProperty("Origin", "${originUri.scheme}://${originUri.authority}")
                } catch(e: Exception) {}
            }
            candidate?.headers?.forEach { (k, v) ->
                connection.setRequestProperty(k, v)
            }
            val inputStream = connection.inputStream
            inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            try { connection?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun resolveHls(candidate: MediaCandidate): MediaCandidate {
        val content = fetchContent(candidate.url, candidate) ?: return candidate
        return parseM3u8(candidate, candidate.url, content, depth = 0)
    }

    private fun parseM3u8(candidate: MediaCandidate, baseUrl: String, content: String, depth: Int): MediaCandidate {
        if (depth > 1) return candidate // Sadece 1 seviye derine in (Master -> Variant)

        var durationSeconds = 0.0
        var highestResolution = 0
        var isLive = true
        var hasEndList = false
        var bestVariantUrl: String? = null
        
        val lines = content.split("\n")
        
        var expectingVariantUrl = false
        var currentRes = 0

        for (line in lines) {
            val l = line.trim()
            if (l.isEmpty() || l.startsWith("#") && !l.startsWith("#EXT")) continue

            if (l.startsWith("#EXTINF:")) {
                val timeStr = l.substringAfter("#EXTINF:").substringBefore(",").trim()
                timeStr.toDoubleOrNull()?.let { durationSeconds += it }
            }
            if (l.startsWith("#EXT-X-STREAM-INF:")) {
                if (l.contains("RESOLUTION=")) {
                    val resStr = l.substringAfter("RESOLUTION=").substringBefore(",").trim()
                    val height = resStr.substringAfter("x").toIntOrNull()
                    if (height != null) {
                        currentRes = height
                        if (height > highestResolution) {
                            highestResolution = height
                        }
                    }
                }
                expectingVariantUrl = true
                continue
            }
            if (l.startsWith("#EXT-X-ENDLIST")) {
                hasEndList = true
                isLive = false
            }

            if (expectingVariantUrl && !l.startsWith("#")) {
                if (currentRes >= highestResolution) {
                    bestVariantUrl = if (l.startsWith("http")) l else {
                        try {
                            val baseUri = java.net.URI(baseUrl)
                            var resolvedUri = baseUri.resolve(l)
                            if (baseUri.query != null && !l.contains("?")) {
                                resolvedUri = java.net.URI(
                                    resolvedUri.scheme, resolvedUri.authority, resolvedUri.path,
                                    baseUri.query, resolvedUri.fragment
                                )
                            }
                            resolvedUri.toString()
                        } catch(e: Exception) {
                            val base = baseUrl.substringBeforeLast("?")
                            val basePath = base.substringBeforeLast("/")
                            "$basePath/$l"
                        }
                    }
                }
                expectingVariantUrl = false
            }
        }

        var newQuality = candidate.quality
        if (highestResolution > 0) {
            newQuality = "${highestResolution}p"
        }

        // Eğer bu bir Master Playlist ise (EXTINF yok ama variant var), variantı indirip süresine bak
        if (durationSeconds == 0.0 && bestVariantUrl != null && depth == 0) {
            val variantContent = fetchContent(bestVariantUrl, candidate)
            if (variantContent != null) {
                // Recursive çağrı, variantı parse et
                val variantResult = parseM3u8(candidate.copy(quality = newQuality ?: candidate.quality), bestVariantUrl, variantContent, depth + 1)
                return variantResult
            }
        }

        var newDuration = candidate.durationStr
        if (hasEndList && durationSeconds > 0) {
            val totalSeconds = durationSeconds.roundToInt()
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            newDuration = if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        } else if (!hasEndList && lines.any { it.startsWith("#EXTINF") }) {
            newDuration = "Canlı Yayın"
            isLive = true
        } else if (durationSeconds > 0) {
             // Endlist yok ama süre hesaplanabildi, belki kopuk bir VOD
             val totalSeconds = durationSeconds.roundToInt()
             val hours = totalSeconds / 3600
             val minutes = (totalSeconds % 3600) / 60
             val seconds = totalSeconds % 60
             newDuration = if (hours > 0) {
                 String.format("%d:%02d:%02d", hours, minutes, seconds)
             } else {
                 String.format("%02d:%02d", minutes, seconds)
             }
        }

        return candidate.copy(
            quality = newQuality ?: candidate.quality,
            durationStr = newDuration ?: candidate.durationStr,
            isLive = isLive
        )
    }

    private fun resolveStandardMedia(candidate: MediaCandidate): MediaCandidate {
        val retriever = MediaMetadataRetriever()
        try {
            val headers = HashMap<String, String>()
            headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
            retriever.setDataSource(candidate.url, headers)
            
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            
            var newQuality = candidate.quality
            if (height != null && height > 0) {
                newQuality = "${height}p"
            }
            
            var newDuration = candidate.durationStr
            if (durationMs != null && durationMs > 0) {
                val totalSeconds = (durationMs / 1000).toInt()
                val hours = totalSeconds / 3600
                val minutes = (totalSeconds % 3600) / 60
                val seconds = totalSeconds % 60
                newDuration = if (hours > 0) {
                    String.format("%d:%02d:%02d", hours, minutes, seconds)
                } else {
                    String.format("%02d:%02d", minutes, seconds)
                }
            }
            
            return candidate.copy(
                quality = newQuality ?: candidate.quality,
                durationStr = newDuration ?: candidate.durationStr
            )
        } catch (e: Exception) {
            return candidate
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
    }
}
