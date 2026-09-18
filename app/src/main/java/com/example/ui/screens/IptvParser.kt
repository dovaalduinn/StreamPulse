package com.example.ui.screens

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class IptvChannel(
    val name: String,
    val logo: String,
    val group: String,
    val url: String
)

object IptvParser {
    suspend fun parseM3u(playlistUrl: String): List<IptvChannel> = withContext(Dispatchers.IO) {
        val channels = mutableListOf<IptvChannel>()
        var connection: HttpURLConnection? = null
        try {
            val url = URL(playlistUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
            }
            
            connection.inputStream.bufferedReader().useLines { lines ->
                var currentName = ""
                var currentLogo = ""
                var currentGroup = "Tümü"
                
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    
                    if (trimmed.startsWith("#EXTINF:")) {
                        // Extract name (usually after the last comma)
                        val nameParts = trimmed.split(",")
                        currentName = if (nameParts.size > 1) nameParts.last().trim() else "Bilinmeyen Kanal"
                        
                        // Extract logo
                        val logoMatch = Regex("tvg-logo=\"([^\"]+)\"").find(trimmed)
                        currentLogo = logoMatch?.groupValues?.get(1) ?: ""
                        
                        // Extract group
                        val groupMatch = Regex("group-title=\"([^\"]+)\"").find(trimmed)
                        currentGroup = groupMatch?.groupValues?.get(1) ?: "Diğer"
                        
                    } else if (!trimmed.startsWith("#")) {
                        // This should be the URL
                        if (trimmed.startsWith("http")) {
                            channels.add(
                                IptvChannel(
                                    name = currentName,
                                    logo = currentLogo,
                                    group = currentGroup,
                                    url = trimmed
                                )
                            )
                        }
                        // Reset
                        currentName = ""
                        currentLogo = ""
                        currentGroup = "Tümü"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { connection?.disconnect() } catch (_: Exception) {}
        }
        return@withContext channels
    }
}
