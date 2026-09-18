package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class PlayerSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val hardwareAcceleration: Boolean = true,
    val defaultPlayerEngine: String = "ExoPlayer (AndroidX Media3)",
    val autoPlayNext: Boolean = true,
    val backgroundPlay: Boolean = false,
    val subtitlesEnabled: Boolean = false,
    val subtitleLanguage: String = "Türkçe", // "Türkçe", "English", "Deutsch", "Español", "Otomatik"
    val subtitleSizeSp: Int = 18, // 14, 18, 22, 26
    val subtitleColorHex: String = "#FFFFFF", // White, Yellow, Green, Cyan
    val subtitleBgOpacityPercentage: Int = 50, // 0 to 100%
    val adblockEnabled: Boolean = true, // Mullvad DNS adblocker
    val fastCacheMode: Int = 2, // 0: Kapalı, 1: Mod 1 (Arka Plan), 2: Mod 2 (Akıllı Tampon)
    val downloadDirectoryUri: String = "", // Custom download directory URI (SAF)
    val popupBlockerEnabled: Boolean = true, // Block new window popups in browser
    val defaultSearchEngine: String = "Google", // "Google", "Yandex", "DuckDuckGo", "Bing", "Brave"
    val useCustomDns: Boolean = false, // Enable Custom DNS / DoH
    val customDnsUrl: String = "", // Custom DNS DoH URL or IP (e.g., https://dns.adguard-dns.com/dns-query, 1.1.1.1)
    val allowInsecureSsl: Boolean = false, // Allow insecure SSL certificates for custom IPTV streams
    val ramBufferLimitMb: Int = 100, // RAM buffer limit in MB for Mode 1 (50, 100, 250, 500, 1024)
    val tvMouseShortcutKeyCode: Int = 82, // 82: KeyEvent.KEYCODE_MENU
    val tvMouseShortcutKeyName: String = "Menü Tuşu (MENU / ≡)",
    val tvAutoEnableMouse: Boolean = false, // Automatically turn on virtual mouse when opening browser
    val tvCursorSpeed: Float = 28f, // Mouse speed in pixels per tick
    val tvKeyPlayPause: Int = 85, // KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
    val tvKeyPlayPauseName: String = "Oynat / Duraklat (PLAY_PAUSE)",
    val tvKeyForward: Int = 90, // KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
    val tvKeyForwardName: String = "10sn İleri Sar (FAST_FORWARD / >>)",
    val tvKeyRewind: Int = 89, // KeyEvent.KEYCODE_MEDIA_REWIND
    val tvKeyRewindName: String = "10sn Geri Sar (REWIND / <<)",
    val tvKeyFullscreen: Int = 186, // KeyEvent.KEYCODE_PROG_BLUE
    val tvKeyFullscreenName: String = "Tam Ekran (Mavi Tuş / PROG_BLUE)",
    val tvKeySubtitle: Int = 175, // KeyEvent.KEYCODE_CAPTIONS
    val tvKeySubtitleName: String = "Altyazı Aç/Kapat (CAPTIONS / CC)",
    val tvKeyAudioTrack: Int = 185, // KeyEvent.KEYCODE_PROG_YELLOW
    val tvKeyAudioTrackName: String = "Ses Dili Değiştir (Sarı Tuş / PROG_YELLOW)",
    val tvKeyMute: Int = 164, // KeyEvent.KEYCODE_VOLUME_MUTE
    val tvKeyMuteName: String = "Sesi Kapat / Aç (MUTE)",
    val tvKeyNextVideo: Int = 87, // KeyEvent.KEYCODE_MEDIA_NEXT
    val tvKeyNextVideoName: String = "Sonraki Video (NEXT)",
    val tvKeyPrevVideo: Int = 88, // KeyEvent.KEYCODE_MEDIA_PREVIOUS
    val tvKeyPrevVideoName: String = "Önceki Video (PREV)",
    val tvKeyDpadUp: Int = 19, // KeyEvent.KEYCODE_DPAD_UP
    val tvKeyDpadUpName: String = "Yukarı Tuşu (DPAD_UP)",
    val tvKeyDpadDown: Int = 20, // KeyEvent.KEYCODE_DPAD_DOWN
    val tvKeyDpadDownName: String = "Aşağı Tuşu (DPAD_DOWN)",
    val tvKeyDpadLeft: Int = 21, // KeyEvent.KEYCODE_DPAD_LEFT
    val tvKeyDpadLeftName: String = "Sol Tuşu (DPAD_LEFT)",
    val tvKeyDpadRight: Int = 22, // KeyEvent.KEYCODE_DPAD_RIGHT
    val tvKeyDpadRightName: String = "Sağ Tuşu (DPAD_RIGHT)",
    val tvKeyDpadCenter: Int = 23, // KeyEvent.KEYCODE_DPAD_CENTER
    val tvKeyDpadCenterName: String = "Orta / OK Tuşu (DPAD_CENTER)",
    val tvKeyScrollMode: Int = 184, // KeyEvent.KEYCODE_PROG_GREEN
    val tvKeyScrollModeName: String = "Sürükleme Modu Tuşu (Yeşil / GREEN)",
    val tvKeyPageUp: Int = 92, // KeyEvent.KEYCODE_PAGE_UP
    val tvKeyPageUpName: String = "Sayfa Yukarı (PAGE_UP / CH+)",
    val tvKeyPageDown: Int = 93, // KeyEvent.KEYCODE_PAGE_DOWN
    val tvKeyPageDownName: String = "Sayfa Aşağı (PAGE_DOWN / CH-)"
)
