package com.example.ui.screens

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.MediaPlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvBoxSettingsScreen(
    viewModel: MediaPlayerViewModel,
    onBack: () -> Unit,
    onRequestKeyAssign: (KeyAssignTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()

    BackHandler {
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "TV Box & Kumanda Ayarları",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Sanal fare, kumanda tuş atamaları ve kısayollar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Sanal Fare Genel Ayarları Kartı
            SettingsHeader(title = "Sanal Fare (Global Virtual Mouse)", icon = Icons.Default.Mouse)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Sanal fare tüm uygulamada çalışır (tarayıcı, üst/alt barlar, oynatıcı). OK tuşuna basılı tutarak veya çift tıklayarak sayfaları akıcı biçimde kaydırabilirsiniz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sanal Fare Kısayol Tuşu
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onRequestKeyAssign(
                                    KeyAssignTarget(
                                        keyId = "tv_mouse",
                                        title = "Sanal Fareyi Aç/Kapat",
                                        description = "Kumandanızdan basıldığında fare imlecini anında açıp kapatacak tuş.",
                                        currentKeyName = settings.tvMouseShortcutKeyName,
                                        currentKeyCode = settings.tvMouseShortcutKeyCode
                                    )
                                )
                            }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sanal Fare Açma/Kapatma Tuşu",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Atanan Tuş: ${settings.tvMouseShortcutKeyName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                onRequestKeyAssign(
                                    KeyAssignTarget(
                                        keyId = "tv_mouse",
                                        title = "Sanal Fareyi Aç/Kapat",
                                        description = "Kumandanızdan basıldığında fare imlecini anında açıp kapatacak tuş.",
                                        currentKeyName = settings.tvMouseShortcutKeyName,
                                        currentKeyCode = settings.tvMouseShortcutKeyCode
                                    )
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Tuşu Ata", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    // Tarayıcıda Sanal Fareyi Otomatik Aç Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tarayıcıda Sanal Fareyi Otomatik Başlat",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Tarayıcı sekmesi açıldığında fare imleci otomatik olarak etkinleşir.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.tvAutoEnableMouse,
                            onCheckedChange = {
                                viewModel.updateSettings(settings.copy(tvAutoEnableMouse = it))
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    // Varsayılan İmleç Hızı
                    Text(
                        text = "Sanal Fare İmleç Hızı",
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "Yavaş" to 16f,
                            "Normal" to 28f,
                            "Hızlı" to 44f,
                            "Çok Hızlı" to 64f
                        ).forEach { (label, speed) ->
                            FilterChip(
                                selected = settings.tvCursorSpeed == speed,
                                onClick = {
                                    viewModel.updateSettings(settings.copy(tvCursorSpeed = speed))
                                },
                                label = { Text(label, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 2. Sanal Fare Yön & Tıklama Tuşları
            SettingsHeader(title = "Yön, Tıklama ve Kaydırma Tuşları (D-Pad)", icon = Icons.Default.Gamepad)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Kumandanızın yön, seçim veya renkli tuşları standart değilse buradan doğrudan tuşa basarak özel olarak atayabilirsiniz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val dpadKeyItems = listOf(
                        Triple("tv_dpad_up", "Yukarı Hareket", settings.tvKeyDpadUpName to settings.tvKeyDpadUp),
                        Triple("tv_dpad_down", "Aşağı Hareket", settings.tvKeyDpadDownName to settings.tvKeyDpadDown),
                        Triple("tv_dpad_left", "Sola Hareket", settings.tvKeyDpadLeftName to settings.tvKeyDpadLeft),
                        Triple("tv_dpad_right", "Sağa Hareket", settings.tvKeyDpadRightName to settings.tvKeyDpadRight),
                        Triple("tv_dpad_center", "Tıklama & Basılı Tutup Sürükleme (OK)", settings.tvKeyDpadCenterName to settings.tvKeyDpadCenter),
                        Triple("tv_scroll_mode", "Sürükleme / Kaydırma Modu Tuşu", settings.tvKeyScrollModeName to settings.tvKeyScrollMode),
                        Triple("tv_page_up", "Sayfa Yukarı Kaydır", settings.tvKeyPageUpName to settings.tvKeyPageUp),
                        Triple("tv_page_down", "Sayfa Aşağı Kaydır", settings.tvKeyPageDownName to settings.tvKeyPageDown)
                    )

                    dpadKeyItems.forEachIndexed { index, (id, title, keyPair) ->
                        val (name, code) = keyPair
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onRequestKeyAssign(
                                        KeyAssignTarget(
                                            keyId = id,
                                            title = title,
                                            description = "$title için kumandanızdaki tuşa basın veya listeden seçin.",
                                            currentKeyName = name,
                                            currentKeyCode = code
                                        )
                                    )
                                }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    text = "Atanan: $name",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    onRequestKeyAssign(
                                        KeyAssignTarget(
                                            keyId = id,
                                            title = title,
                                            description = "$title için kumandanızdaki tuşa basın veya listeden seçin.",
                                            currentKeyName = name,
                                            currentKeyCode = code
                                        )
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Ata", fontSize = 11.5.sp)
                            }
                        }
                        if (index < dpadKeyItems.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 3.dp), thickness = 0.5.dp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "💡 Sayfa Sürükleme ve Kaydırma Yöntemleri:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Hızlı Çift Tıklama (Double OK): OK tuşuna 2 kez hızlıca basarak '✋ Sürükleme Modu'nu açıp kapatabilir, yön tuşlarıyla sayfayı kolayca kaydırabilirsiniz.\n• Basılı Tutma: OK tuşuna basılı tutarken yön tuşlarına basarak sürükleme yapabilirsiniz.\n• Kısayol Tuşu: Yeşil tuşa basarak tek dokunuşla kaydırma moduna geçebilirsiniz.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 3. Medya Oynatıcı Tuş Kısayolları Bölümü
            SettingsHeader(title = "Video Oynatıcı Kumanda Kısayolları", icon = Icons.Default.SmartDisplay)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Video oynatılırken kumandanızdaki özel tuşlarla hızlı kontroller sağlayın.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val playerKeyItems = listOf(
                        Triple("key_play_pause", "Oynat / Duraklat", settings.tvKeyPlayPauseName to settings.tvKeyPlayPause),
                        Triple("key_forward", "10 Saniye İleri Sar (+10s)", settings.tvKeyForwardName to settings.tvKeyForward),
                        Triple("key_rewind", "10 Saniye Geri Sar (-10s)", settings.tvKeyRewindName to settings.tvKeyRewind),
                        Triple("key_fullscreen", "Tam Ekran / Yakınlaştır", settings.tvKeyFullscreenName to settings.tvKeyFullscreen),
                        Triple("key_subtitle", "Altyazı Değiştir / Kapat", settings.tvKeySubtitleName to settings.tvKeySubtitle),
                        Triple("key_audio", "Ses Kanalı / Dili Değiştir", settings.tvKeyAudioTrackName to settings.tvKeyAudioTrack),
                        Triple("key_mute", "Sesi Kapat / Aç (MUTE)", settings.tvKeyMuteName to settings.tvKeyMute),
                        Triple("key_next", "Sonraki Video", settings.tvKeyNextVideoName to settings.tvKeyNextVideo),
                        Triple("key_prev", "Önceki Video", settings.tvKeyPrevVideoName to settings.tvKeyPrevVideo)
                    )

                    playerKeyItems.forEachIndexed { index, (id, title, keyPair) ->
                        val (name, code) = keyPair
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onRequestKeyAssign(
                                        KeyAssignTarget(
                                            keyId = id,
                                            title = title,
                                            description = "$title fonksiyonunu çalıştırmak için kumanda tuşu seçin.",
                                            currentKeyName = name,
                                            currentKeyCode = code
                                        )
                                    )
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                                Text(
                                    text = "Atanan: $name",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    onRequestKeyAssign(
                                        KeyAssignTarget(
                                            keyId = id,
                                            title = title,
                                            description = "$title fonksiyonunu çalıştırmak için kumanda tuşu seçin.",
                                            currentKeyName = name,
                                            currentKeyCode = code
                                        )
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Ata", fontSize = 12.sp)
                            }
                        }
                        if (index < playerKeyItems.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tuşları Varsayılana Sıfırla
                    OutlinedButton(
                        onClick = {
                            viewModel.updateSettings(
                                settings.copy(
                                    tvMouseShortcutKeyCode = KeyEvent.KEYCODE_MENU,
                                    tvMouseShortcutKeyName = "Menü Tuşu (MENU / ≡)",
                                    tvKeyDpadUp = KeyEvent.KEYCODE_DPAD_UP,
                                    tvKeyDpadUpName = "Yukarı Tuşu (DPAD_UP)",
                                    tvKeyDpadDown = KeyEvent.KEYCODE_DPAD_DOWN,
                                    tvKeyDpadDownName = "Aşağı Tuşu (DPAD_DOWN)",
                                    tvKeyDpadLeft = KeyEvent.KEYCODE_DPAD_LEFT,
                                    tvKeyDpadLeftName = "Sol Tuşu (DPAD_LEFT)",
                                    tvKeyDpadRight = KeyEvent.KEYCODE_DPAD_RIGHT,
                                    tvKeyDpadRightName = "Sağ Tuşu (DPAD_RIGHT)",
                                    tvKeyDpadCenter = KeyEvent.KEYCODE_DPAD_CENTER,
                                    tvKeyDpadCenterName = "Orta / OK Tuşu (DPAD_CENTER)",
                                    tvKeyScrollMode = KeyEvent.KEYCODE_PROG_GREEN,
                                    tvKeyScrollModeName = "Sürükleme Modu Tuşu (Yeşil / GREEN)",
                                    tvKeyPageUp = KeyEvent.KEYCODE_PAGE_UP,
                                    tvKeyPageUpName = "Sayfa Yukarı (PAGE_UP / CH+)",
                                    tvKeyPageDown = KeyEvent.KEYCODE_PAGE_DOWN,
                                    tvKeyPageDownName = "Sayfa Aşağı (PAGE_DOWN / CH-)",
                                    tvKeyPlayPause = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                                    tvKeyPlayPauseName = "Oynat / Duraklat (PLAY_PAUSE)",
                                    tvKeyForward = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                                    tvKeyForwardName = "Hızlı İleri Sar (FAST_FORWARD)",
                                    tvKeyRewind = KeyEvent.KEYCODE_MEDIA_REWIND,
                                    tvKeyRewindName = "Hızlı Geri Sar (REWIND)",
                                    tvKeyFullscreen = KeyEvent.KEYCODE_PROG_BLUE,
                                    tvKeyFullscreenName = "Mavi Renkli Tuş (BLUE)",
                                    tvKeySubtitle = KeyEvent.KEYCODE_CAPTIONS,
                                    tvKeySubtitleName = "Altyazı Tuşu (SUBTITLE / CC)",
                                    tvKeyAudioTrack = KeyEvent.KEYCODE_PROG_YELLOW,
                                    tvKeyAudioTrackName = "Sarı Renkli Tuş (YELLOW)",
                                    tvKeyMute = KeyEvent.KEYCODE_VOLUME_MUTE,
                                    tvKeyMuteName = "Sesi Kapat (MUTE)",
                                    tvKeyNextVideo = KeyEvent.KEYCODE_MEDIA_NEXT,
                                    tvKeyNextVideoName = "Sonraki Video (NEXT)",
                                    tvKeyPrevVideo = KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                                    tvKeyPrevVideoName = "Önceki Video (PREV)"
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tüm Tuş Atamalarını Varsayılana Döndür", fontSize = 12.5.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun SettingsHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
