package com.example.util

import android.view.KeyEvent

object GlobalKeyCaptureHolder {
    var isCapturing: Boolean = false
    var onKeyCaptured: ((Int) -> Unit)? = null
}

data class RemoteKeyOption(
    val name: String,
    val keyCode: Int,
    val description: String
)

val defaultRemoteKeyOptions = listOf(
    RemoteKeyOption("Yukarı Yön Tuşu (DPAD_UP)", KeyEvent.KEYCODE_DPAD_UP, "Kumanda yukarı yön tuşu"),
    RemoteKeyOption("Aşağı Yön Tuşu (DPAD_DOWN)", KeyEvent.KEYCODE_DPAD_DOWN, "Kumanda aşağı yön tuşu"),
    RemoteKeyOption("Sol Yön Tuşu (DPAD_LEFT)", KeyEvent.KEYCODE_DPAD_LEFT, "Kumanda sol yön tuşu"),
    RemoteKeyOption("Sağ Yön Tuşu (DPAD_RIGHT)", KeyEvent.KEYCODE_DPAD_RIGHT, "Kumanda sağ yön tuşu"),
    RemoteKeyOption("Orta / OK Tuşu (DPAD_CENTER)", KeyEvent.KEYCODE_DPAD_CENTER, "Kumanda orta onay/seçim tuşu"),
    RemoteKeyOption("Giriş / Enter Tuşu (ENTER)", KeyEvent.KEYCODE_ENTER, "Klavyedeki/kumandadaki Enter tuşu"),
    RemoteKeyOption("Menü Tuşu (MENU / ≡)", KeyEvent.KEYCODE_MENU, "Standart kumanda menü tuşu"),
    RemoteKeyOption("Bilgi Tuşu (INFO / i)", KeyEvent.KEYCODE_INFO, "Bilgi / Info tuşu"),
    RemoteKeyOption("Kırmızı Renkli Tuş (RED)", KeyEvent.KEYCODE_PROG_RED, "Kumandadaki Kırmızı renkli tuş"),
    RemoteKeyOption("Yeşil Renkli Tuş (GREEN)", KeyEvent.KEYCODE_PROG_GREEN, "Kumandadaki Yeşil renkli tuş"),
    RemoteKeyOption("Sarı Renkli Tuş (YELLOW)", KeyEvent.KEYCODE_PROG_YELLOW, "Kumandadaki Sarı renkli tuş"),
    RemoteKeyOption("Mavi Renkli Tuş (BLUE)", KeyEvent.KEYCODE_PROG_BLUE, "Kumandadaki Mavi renkli tuş"),
    RemoteKeyOption("Kılavuz / Rehber (GUIDE / EPG)", KeyEvent.KEYCODE_GUIDE, "TV Rehber / Guide tuşu"),
    RemoteKeyOption("Oynat / Duraklat (PLAY_PAUSE)", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Medya oynat/durdur tuşu"),
    RemoteKeyOption("Hızlı İleri Sar (FAST_FORWARD / >>)", KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, "Hızlı ileri sarma tuşu"),
    RemoteKeyOption("Hızlı Geri Sar (REWIND / <<)", KeyEvent.KEYCODE_MEDIA_REWIND, "Hızlı geri sarma tuşu"),
    RemoteKeyOption("Kanal + (CH UP)", KeyEvent.KEYCODE_CHANNEL_UP, "Sonraki kanal tuşu"),
    RemoteKeyOption("Kanal - (CH DOWN)", KeyEvent.KEYCODE_CHANNEL_DOWN, "Önceki kanal tuşu"),
    RemoteKeyOption("Altyazı Tuşu (SUBTITLE / CC)", KeyEvent.KEYCODE_CAPTIONS, "Altyazı / CC tuşu"),
    RemoteKeyOption("Sesi Kapat (MUTE)", KeyEvent.KEYCODE_VOLUME_MUTE, "Sesi kapatma tuşu"),
    RemoteKeyOption("Sonraki Parça / Video (NEXT)", KeyEvent.KEYCODE_MEDIA_NEXT, "Sonraki medya tuşu"),
    RemoteKeyOption("Önceki Parça / Video (PREV)", KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Önceki medya tuşu"),
    RemoteKeyOption("Yer İmi / Favori (BOOKMARK)", KeyEvent.KEYCODE_BOOKMARK, "Yer imi tuşu"),
    RemoteKeyOption("TV Kaynak (SOURCE / INPUT)", KeyEvent.KEYCODE_TV_INPUT, "Kaynak / Giriş tuşu")
)

fun getKeyCodeFriendlyName(keyCode: Int): String {
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> "Yukarı Tuşu (DPAD_UP)"
        KeyEvent.KEYCODE_DPAD_DOWN -> "Aşağı Tuşu (DPAD_DOWN)"
        KeyEvent.KEYCODE_DPAD_LEFT -> "Sol Tuşu (DPAD_LEFT)"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "Sağ Tuşu (DPAD_RIGHT)"
        KeyEvent.KEYCODE_DPAD_CENTER -> "Orta / OK Tuşu (DPAD_CENTER)"
        KeyEvent.KEYCODE_ENTER -> "Giriş / OK Tuşu (ENTER)"
        KeyEvent.KEYCODE_NUMPAD_ENTER -> "Numpad Giriş (ENTER)"
        KeyEvent.KEYCODE_MENU -> "Menü Tuşu (MENU / ≡)"
        KeyEvent.KEYCODE_INFO -> "Bilgi Tuşu (INFO / i)"
        KeyEvent.KEYCODE_GUIDE -> "Rehber Tuşu (GUIDE / EPG)"
        KeyEvent.KEYCODE_PROG_RED -> "Kırmızı Renkli Tuş (RED)"
        KeyEvent.KEYCODE_PROG_GREEN -> "Yeşil Renkli Tuş (GREEN)"
        KeyEvent.KEYCODE_PROG_YELLOW -> "Sarı Renkli Tuş (YELLOW)"
        KeyEvent.KEYCODE_PROG_BLUE -> "Mavi Renkli Tuş (BLUE)"
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "Oynat / Duraklat (PLAY_PAUSE)"
        KeyEvent.KEYCODE_MEDIA_PLAY -> "Oynat (PLAY)"
        KeyEvent.KEYCODE_MEDIA_PAUSE -> "Duraklat (PAUSE)"
        KeyEvent.KEYCODE_MEDIA_STOP -> "Durdur (STOP)"
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "Hızlı İleri Sar (FAST_FORWARD)"
        KeyEvent.KEYCODE_MEDIA_REWIND -> "Hızlı Geri Sar (REWIND)"
        KeyEvent.KEYCODE_MEDIA_NEXT -> "Sonraki Video (NEXT)"
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "Önceki Video (PREV)"
        KeyEvent.KEYCODE_CHANNEL_UP -> "Kanal + (CH UP)"
        KeyEvent.KEYCODE_CHANNEL_DOWN -> "Kanal - (CH DOWN)"
        KeyEvent.KEYCODE_VOLUME_UP -> "Ses +"
        KeyEvent.KEYCODE_VOLUME_DOWN -> "Ses -"
        KeyEvent.KEYCODE_VOLUME_MUTE -> "Sesi Kapat (MUTE)"
        KeyEvent.KEYCODE_CAPTIONS -> "Altyazı Tuşu (CC / Subtitle)"
        KeyEvent.KEYCODE_BOOKMARK -> "Yer İmi (Bookmark)"
        KeyEvent.KEYCODE_TV_INPUT -> "TV Giriş (Source / Input)"
        KeyEvent.KEYCODE_PAGE_UP -> "Sayfa Yukarı (PAGE_UP)"
        KeyEvent.KEYCODE_PAGE_DOWN -> "Sayfa Aşağı (PAGE_DOWN)"
        KeyEvent.KEYCODE_HELP -> "Yardım Tuşu (Help)"
        KeyEvent.KEYCODE_SEARCH -> "Arama Tuşu (Search)"
        KeyEvent.KEYCODE_HOME -> "Ana Ekran (Home)"
        KeyEvent.KEYCODE_TV -> "Canlı TV (Live TV)"
        KeyEvent.KEYCODE_WINDOW -> "Pencere Tuşu"
        KeyEvent.KEYCODE_SETTINGS -> "Ayarlar Tuşu"
        KeyEvent.KEYCODE_F1 -> "F1 Tuşu"
        KeyEvent.KEYCODE_F2 -> "F2 Tuşu"
        KeyEvent.KEYCODE_F3 -> "F3 Tuşu"
        KeyEvent.KEYCODE_F4 -> "F4 Tuşu"
        KeyEvent.KEYCODE_0 -> "Tuş 0"
        KeyEvent.KEYCODE_1 -> "Tuş 1"
        KeyEvent.KEYCODE_2 -> "Tuş 2"
        KeyEvent.KEYCODE_3 -> "Tuş 3"
        KeyEvent.KEYCODE_4 -> "Tuş 4"
        KeyEvent.KEYCODE_5 -> "Tuş 5"
        KeyEvent.KEYCODE_6 -> "Tuş 6"
        KeyEvent.KEYCODE_7 -> "Tuş 7"
        KeyEvent.KEYCODE_8 -> "Tuş 8"
        KeyEvent.KEYCODE_9 -> "Tuş 9"
        else -> KeyEvent.keyCodeToString(keyCode).replace("KEYCODE_", "Tuş: ")
    }
}
