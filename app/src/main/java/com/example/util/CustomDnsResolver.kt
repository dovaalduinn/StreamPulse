package com.example.util

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object CustomDnsResolver : Dns {
    @Volatile
    var isEnabled: Boolean = false

    @Volatile
    var customDnsTarget: String = ""

    private val dnsCache = ConcurrentHashMap<String, List<InetAddress>>()

    private val directHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(Dns.SYSTEM)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    fun configure(enabled: Boolean, dnsTarget: String) {
        if (isEnabled != enabled || customDnsTarget != dnsTarget) {
            dnsCache.clear()
        }
        isEnabled = enabled
        customDnsTarget = dnsTarget.trim()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        if (!isEnabled || customDnsTarget.isBlank()) {
            return Dns.SYSTEM.lookup(hostname)
        }

        // Check in-memory DNS cache
        dnsCache[hostname]?.let { return it }

        val target = customDnsTarget
        // 1. If it's a DoH URL (https://...)
        if (target.startsWith("http://") || target.startsWith("https://")) {
            try {
                val resolved = resolveViaDoh(hostname, target)
                if (resolved.isNotEmpty()) {
                    dnsCache[hostname] = resolved
                    return resolved
                }
            } catch (_: Exception) {}
        }

        // 2. Fallback to system DNS
        val systemResult = Dns.SYSTEM.lookup(hostname)
        dnsCache[hostname] = systemResult
        return systemResult
    }

    private fun resolveViaDoh(hostname: String, dohUrl: String): List<InetAddress> {
        val cleanUrl = if (dohUrl.contains("?")) {
            "$dohUrl&name=${Uri.encode(hostname)}&type=A"
        } else {
            "$dohUrl?name=${Uri.encode(hostname)}&type=A"
        }

        val request = Request.Builder()
            .url(cleanUrl)
            .header("Accept", "application/dns-json")
            .build()

        directHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val bodyString = response.body?.string() ?: return emptyList()
            val json = JSONObject(bodyString)
            val answers = json.optJSONArray("Answer") ?: return emptyList()

            val addresses = mutableListOf<InetAddress>()
            for (i in 0 until answers.length()) {
                val ans = answers.getJSONObject(i)
                val type = ans.optInt("type", 0)
                val data = ans.optString("data", "")
                // Type 1 is DNS A record (IPv4)
                if (type == 1 && data.isNotBlank()) {
                    try {
                        addresses.add(InetAddress.getByName(data))
                    } catch (_: Exception) {}
                }
            }
            return addresses
        }
    }

    suspend fun testDns(target: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = target.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("DNS adresi boş olamaz."))
        }

        try {
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                val dohUrl = trimmed.toHttpUrlOrNull()
                    ?: return@withContext Result.failure(IllegalArgumentException("Geçersiz DoH URL formatı."))

                val testHost = "google.com"
                val resolved = resolveViaDoh(testHost, dohUrl.toString())
                if (resolved.isNotEmpty()) {
                    Result.success("Bağlantı başarılı! google.com -> ${resolved.first().hostAddress}")
                } else {
                    Result.failure(Exception("DoH sunucusundan yanıt alınamadı."))
                }
            } else {
                // IP format test
                val address = InetAddress.getByName(trimmed)
                if (address.isReachable(3000)) {
                    Result.success("DNS sunucusuna erişildi: ${address.hostAddress}")
                } else {
                    Result.success("DNS IP adresi doğrulandı: ${address.hostAddress}")
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
