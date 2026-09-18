package com.example.ui.screens

import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

object AdblockDns {
    // Fast in-memory cache for evaluated host decisions
    private val hostCache = ConcurrentHashMap<String, Boolean>()

    // Whitelisted essential domains to never block
    private val WHITELIST = hashSetOf(
        "google.com", "google.com.tr", "gstatic.com", "googleapis.com",
        "youtube.com", "googlevideo.com", "ytimg.com",
        "wikipedia.org", "wikimedia.org", "cloudflare.com", "cdnjs.cloudflare.com",
        "jsdelivr.net", "unpkg.com", "github.com", "raw.githubusercontent.com",
        "yandex.com", "yandex.com.tr", "bing.com", "duckduckgo.com",
        "facebook.com", "instagram.com", "twitter.com", "x.com", "reddit.com",
        "twitch.tv", "vimeo.com", "dailymotion.com", "spotify.com", "netflix.com",
        "apple.com", "microsoft.com", "amazon.com", "yahoo.com"
    )

    // Curated high-precision ad servers, tracker domains & betting popup networks
    private val KNOWN_AD_DOMAINS = hashSetOf(
        // Google Ad & Tracking Networks (only ad subdomains)
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "adservice.google.com", "pagead2.googlesyndication.com", "partner.googleadservices.com",
        "ads.google.com", "google-analytics.com", "ssl.google-analytics.com",

        // Invasive Popups, Popunders & Redirect Networks
        "popads.net", "popcash.net", "adcash.com", "propellerads.com", "onclickads.net",
        "realsrv.com", "exoclick.com", "exosrv.com", "adsterra.com", "hilltopads.com",
        "monetag.com", "yadro.ru", "adk2x.com", "clksite.com", "trafficjunky.com",
        "juicyads.com", "ero-advertising.com", "adtrue.com", "adnxs.com", "clickadu.com",
        "popunder.net", "popmyads.com", "poponclick.com", "adxadserv.com", "zeroredirect.com",
        "onclickpredictiv.com", "syndication.exoclick.com", "syndication.realsrv.com",
        "richaudience.com", "yieldmo.com", "teads.tv", "bidswitch.net",

        // Major Ad Networks & Bidding Exchanges
        "amazon-adsystem.com", "criteo.com", "criteo.net", "adform.net", "rubiconproject.com",
        "taboola.com", "outbrain.com", "smartadserver.com", "openx.net", "casalemedia.com",
        "pubmatic.com", "indexexchange.com", "sovrn.com", "applovin.com", "unityads.unity3d.com",
        "vungle.com", "inmobi.com", "ironsrc.com", "supersonicads.com", "chartboost.com",
        "adcolony.com", "mobfox.com", "revcontent.com", "mgid.com", "media.net",

        // Telemetry, Trackers & Cryptominers
        "hotjar.com", "clarity.ms", "mixpanel.com", "segment.io", "amplitude.com",
        "scorecardresearch.com", "quantserve.com", "moatads.com", "doubleverify.com",
        "coinhive.com", "coin-hive.com", "jsecoin.com", "crypto-loot.com",

        // Video Ads & Preroll Ad Servers
        "imasdk.googleapis.com", "serving-sys.com", "spotxchange.com", "spotx.tv",
        "springserve.com", "tremorhub.com", "unrulymedia.com", "stickyadstv.com"
    )

    // Suspicious path patterns explicitly indicating ad resources
    private val AD_PATH_KEYWORDS = listOf(
        "/pagead/", "/adserver/", "/ad_banner", "/ads/banner", "/popunder",
        "popads", "onclickads", "/adsystem/", "adsbygoogle",
        "/doubleclick/", "/ad_frame", "banner_ad"
    )

    fun isWhitelisted(host: String): Boolean {
        if (host.isBlank()) return false
        val cleanHost = host.lowercase().trim().removePrefix("www.")
        return WHITELIST.contains(cleanHost) || WHITELIST.any { cleanHost == it || cleanHost.endsWith(".$it") }
    }

    fun isAdHost(host: String): Boolean {
        if (host.isBlank()) return false
        val cleanHost = host.lowercase().trim().removePrefix("www.")

        // 1. Whitelist Check (Absolute priority)
        if (isWhitelisted(cleanHost)) {
            return false
        }

        // Prevent memory leak on long browsing sessions
        if (hostCache.size > 2000) {
            hostCache.clear()
        }

        // 2. Cache Check
        hostCache[cleanHost]?.let { return it }

        // 3. Known Ad Network Domains
        if (KNOWN_AD_DOMAINS.contains(cleanHost) || KNOWN_AD_DOMAINS.any { cleanHost.endsWith(".$it") }) {
            hostCache[cleanHost] = true
            return true
        }

        // 4. Prefix/Subdomain heuristics (e.g., ads.example.com, popunder.site.com)
        if (cleanHost.startsWith("ad.") || cleanHost.startsWith("ads.") ||
            cleanHost.startsWith("adserver.") || cleanHost.startsWith("popunder.") ||
            cleanHost.startsWith("popup.") || cleanHost.startsWith("track.") ||
            cleanHost.startsWith("analytics.")) {
            hostCache[cleanHost] = true
            return true
        }

        hostCache[cleanHost] = false
        return false
    }

    fun isAdUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase().trim()

        val host = try {
            Uri.parse(url).host?.lowercase()?.trim()?.removePrefix("www.") ?: ""
        } catch (_: Exception) { "" }

        // 1. If host is whitelisted (e.g., google.com, gstatic.com, yandex.com), never block
        if (host.isNotEmpty() && isWhitelisted(host)) {
            return false
        }

        // 2. Host validation against known ad networks
        if (host.isNotEmpty() && isAdHost(host)) {
            return true
        }

        // 3. Path keywords matching for non-whitelisted domains
        if (AD_PATH_KEYWORDS.any { lower.contains(it) }) {
            return true
        }

        return false
    }
}
