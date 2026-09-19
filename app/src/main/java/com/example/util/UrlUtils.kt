package com.example.util

import android.net.Uri

object UrlUtils {
    /**
     * Güvenlik Kontrolü:
     * Yalnızca geçerli "http://" veya "https://" protokolüne sahip URL'leri kabul eder.
     * Potansiyel güvenlik açığı ve enjeksiyon oluşturabilecek diğer tüm şemaları
     * (file://, content://, javascript:, data:, blob:, chrome:, intent: vb.) sessizce reddeder.
     */
    fun isValidHttpUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        val isHttpScheme = trimmed.startsWith("http://", ignoreCase = true) || 
                           trimmed.startsWith("https://", ignoreCase = true)
        if (!isHttpScheme) return false
        
        return try {
            val parsed = Uri.parse(trimmed)
            val scheme = parsed.scheme?.lowercase()
            (scheme == "http" || scheme == "https") && !parsed.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }
}
