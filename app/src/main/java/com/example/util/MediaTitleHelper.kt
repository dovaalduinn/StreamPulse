package com.example.util

import com.example.data.model.WatchHistoryEntity

data class MediaTitleParsed(
    val seriesName: String,
    val seasonName: String,
    val episodeName: String,
    val isSeries: Boolean,
    val cleanTitle: String,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0
)

object MediaTitleHelper {

    private val QUALITY_AND_NOISE_REGEX = Regex(
        "(?i)\\b(4k|2160p|1440p|1080p|720p|480p|360p|240p|hd|fullhd|fhd|sd|uhd|bluray|web-dl|webrip|dvdrip|hls|m3u8|mp4|izle|tek\\s*parça|türkçe\\s*dublaj|türkçe\\s*altyazılı|altyazılı|dual|fragman)\\b|[\\[\\(](?:4k|2160p|1440p|1080p|720p|480p|360p|hd|sd)[^\\]\\)]*[\\]\\)]"
    )

    fun cleanMediaTitle(raw: String): String {
        return raw.replace(QUALITY_AND_NOISE_REGEX, " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    fun parseTitle(rawTitle: String): MediaTitleParsed {
        val t = rawTitle.trim()

        // 1. Sezon 5. Bölüm
        var match = "(?i)(.*?)\\s*(?:-?\\s*)?(\\d+)\\.?\\s*[Ss]ezon\\s*(\\d+)\\.?\\s*[Bb]ölüm".toRegex().find(t)
        if (match != null) {
            val series = cleanSeriesName(match.groupValues[1])
            val sNum = match.groupValues[2].toIntOrNull() ?: 1
            val eNum = match.groupValues[3].toIntOrNull() ?: 1
            val season = "$sNum. Sezon"
            val episode = "$eNum. Bölüm"
            return MediaTitleParsed(
                seriesName = series.ifBlank { "Bilinmeyen Dizi" },
                seasonName = season,
                episodeName = episode,
                isSeries = true,
                cleanTitle = "${series.ifBlank { "Bilinmeyen Dizi" }} $season $episode",
                seasonNumber = sNum,
                episodeNumber = eNum
            )
        }

        // S01E05
        match = "(?i)(.*?)\\s*(?:-?\\s*)?S(\\d+)\\s*E(\\d+)".toRegex().find(t)
        if (match != null) {
            val series = cleanSeriesName(match.groupValues[1])
            val sNum = match.groupValues[2].toIntOrNull() ?: 1
            val eNum = match.groupValues[3].toIntOrNull() ?: 1
            val season = "$sNum. Sezon"
            val episode = "$eNum. Bölüm"
            return MediaTitleParsed(
                seriesName = series.ifBlank { "Bilinmeyen Dizi" },
                seasonName = season,
                episodeName = episode,
                isSeries = true,
                cleanTitle = "${series.ifBlank { "Bilinmeyen Dizi" }} $season $episode",
                seasonNumber = sNum,
                episodeNumber = eNum
            )
        }

        // 1x05
        match = "(?i)(.*?)\\s*(?:-?\\s*)?\\b(\\d{1,2})x(\\d{1,3})\\b".toRegex().find(t)
        if (match != null) {
            val series = cleanSeriesName(match.groupValues[1])
            val sNum = match.groupValues[2].toIntOrNull() ?: 1
            val eNum = match.groupValues[3].toIntOrNull() ?: 1
            val season = "$sNum. Sezon"
            val episode = "$eNum. Bölüm"
            if (series.isNotEmpty() && !series.last().isDigit()) {
                return MediaTitleParsed(
                    seriesName = series.ifBlank { "Bilinmeyen Dizi" },
                    seasonName = season,
                    episodeName = episode,
                    isSeries = true,
                    cleanTitle = "${series.ifBlank { "Bilinmeyen Dizi" }} $season $episode",
                    seasonNumber = sNum,
                    episodeNumber = eNum
                )
            }
        }

        val cleaned = cleanMediaTitle(t).ifBlank { t }
        return MediaTitleParsed(
            seriesName = "",
            seasonName = "",
            episodeName = "",
            isSeries = false,
            cleanTitle = cleaned
        )
    }

    private fun cleanSeriesName(raw: String): String {
        return raw.replace(QUALITY_AND_NOISE_REGEX, " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    fun isSameMedia(title1: String, url1: String, title2: String, url2: String): Boolean {
        if (url1.isNotBlank() && url1 == url2) return true

        val p1 = parseTitle(title1)
        val p2 = parseTitle(title2)

        if (p1.isSeries && p2.isSeries) {
            return p1.seriesName.equals(p2.seriesName, ignoreCase = true) &&
                    p1.seasonNumber == p2.seasonNumber &&
                    p1.episodeNumber == p2.episodeNumber
        }

        // Both are standalone
        if (!p1.isSeries && !p2.isSeries) {
            val isGeneric1 = isGenericTitle(p1.cleanTitle)
            val isGeneric2 = isGenericTitle(p2.cleanTitle)
            if (!isGeneric1 && !isGeneric2) {
                return p1.cleanTitle.equals(p2.cleanTitle, ignoreCase = true)
            }
        }

        return false
    }

    private fun isGenericTitle(title: String): Boolean {
        val lower = title.lowercase().trim()
        return lower.isBlank() ||
                lower == "video" ||
                lower == "yakalanan akış" ||
                lower == "yakalanan web akışı" ||
                lower == "yayın/video linki" ||
                lower == "bilinmeyen dizi"
    }
}
