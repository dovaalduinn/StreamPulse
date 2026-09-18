package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.MediaPlayerViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.launch

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import com.example.util.GlobalKeyCaptureHolder
import com.example.util.defaultRemoteKeyOptions
import com.example.util.getKeyCodeFriendlyName

data class KeyAssignTarget(
    val keyId: String,
    val title: String,
    val description: String,
    val currentKeyName: String,
    val currentKeyCode: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MediaPlayerViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    var isTvBoxMenuOpen by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var activeKeyTarget by remember { mutableStateOf<KeyAssignTarget?>(null) }
    var customDnsInput by remember(settings.customDnsUrl) { mutableStateOf(settings.customDnsUrl) }
    var dnsTestStatus by remember { mutableStateOf<String?>(null) }
    var isTestingDns by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val subtitleLangs = listOf("Türkçe", "English", "Deutsch", "Español", "Kapalı")
    val subtitleColors = listOf(
        "Beyaz" to "#FFFFFF",
        "Sarı" to "#FFFF00",
        "Cam Göbeği" to "#00FFFF",
        "Yeşil" to "#00FF00"
    )

    val context = LocalContext.current
    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, flags)
            viewModel.updateSettings(settings.copy(downloadDirectoryUri = it.toString()))
        }
    }

    if (isTvBoxMenuOpen) {
        TvBoxSettingsScreen(
            viewModel = viewModel,
            onBack = { isTvBoxMenuOpen = false },
            onRequestKeyAssign = { activeKeyTarget = it },
            modifier = modifier
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Ayarlar",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
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
            // Section 1: General Settings
            SettingsSectionHeader(title = "Genel Oynatıcı Ayarları", icon = Icons.Default.Settings)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Player Engine Dropdown
                    var expandedEngine by remember { mutableStateOf(false) }
                    val engineOptions = listOf("ExoPlayer (AndroidX Media3)", "ExoPlayer (Düşük Gecikmeli Canlı Yayın)")
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { expandedEngine = true }.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Oynatıcı Motoru",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (settings.defaultPlayerEngine.contains("LibVLC")) "ExoPlayer (AndroidX Media3)" else settings.defaultPlayerEngine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Box {
                            IconButton(onClick = { expandedEngine = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Motor Seç")
                            }
                            DropdownMenu(
                                expanded = expandedEngine,
                                onDismissRequest = { expandedEngine = false }
                            ) {
                                engineOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            viewModel.updateSettings(settings.copy(defaultPlayerEngine = option))
                                            expandedEngine = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    // Hardware Acceleration Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Donanımsal Hızlandırma (GPU)",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Daha akıcı 4K ve 60fps video oynatma performansı sağlar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.hardwareAcceleration,
                            onCheckedChange = {
                                viewModel.updateSettings(settings.copy(hardwareAcceleration = it))
                            },
                            modifier = Modifier.testTag("hw_accel_switch")
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Subtitles Default Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Altyazılar Varsayılan Olarak Açık",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Oynatıcı açıldığında altyazıları otomatik etkinleştirir.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.subtitlesEnabled,
                            onCheckedChange = {
                                viewModel.updateSettings(settings.copy(subtitlesEnabled = it))
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Local Adblock Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reklam & İzleyici Filtresi",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Web sayfalarındaki açılır pencereleri, reklam bantlarını ve izleyicileri engeller.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.adblockEnabled,
                            onCheckedChange = {
                                viewModel.updateSettings(settings.copy(adblockEnabled = it))
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Hızlı Önbellek Modu
                    Text(
                        text = "Hızlı Önbellek Modu",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Video atlamalarında ve arka plan indirmelerinde kullanılacak önbellek mantığı.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val modes = listOf("Kapalı", "1. Çalışma Mantığı", "2. Çalışma Mantığı", "3. Çalışma Mantığı")
                        modes.forEachIndexed { index, modeName ->
                            FilterChip(
                                selected = settings.fastCacheMode == index,
                                onClick = {
                                    viewModel.updateSettings(settings.copy(fastCacheMode = index))
                                },
                                label = { Text(modeName, fontSize = 12.sp) }
                            )
                        }
                    }

                    val modeDescription = when (settings.fastCacheMode) {
                        0 -> "Standart oynatma. Sadece anlık izlenen kısım bellekte tutulur. Cihaz hafızası harcamaz ancak ileri-geri atlamalarda videonun tekrar yüklenmesi gerekir."
                        1 -> "Hızlı önbellek (Mod 1). Video arka planda cihazın depolamasına inmeye başlar. ExoPlayer diskin üzerinden seçilen RAM kotası kadar veriyi belleğe alır. Yalnızca diske inmiş sarı bölgelere atlanabilir."
                        2 -> "Akıllı önbellek (Mod 2). İndirme işlemi kaldığınız anlık konumdan başlayarak ileriye doğru yapılır. İstediğiniz her yere serbestçe atlayabilirsiniz; geride kalan eksik parçalar ileri kısımlar bittiğinde tamamlanır."
                        3 -> "Sıfır Yıpranma (Mod 3 - RAM-Only). Diske veri yazılmaz; video doğrudan seçilen RAM kotası kadar sadece belleğe indirilir. Diski hiç yıpratmaz, yeşil tampon göstergesiyle serbest atlama imkanı sunar."
                        else -> ""
                    }

                    if (modeDescription.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = modeDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    if (settings.fastCacheMode != 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "RAM Tampon Kotası (Mod ${settings.fastCacheMode})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ExoPlayer'ın diskin üzerinden RAM'e alacağı maksimum veri miktarı.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val ramOptions = listOf(50, 100, 250, 500, 1024)
                            ramOptions.forEach { mb ->
                                FilterChip(
                                    selected = settings.ramBufferLimitMb == mb,
                                    onClick = {
                                        viewModel.updateSettings(settings.copy(ramBufferLimitMb = mb))
                                    },
                                    label = { Text("${mb} MB", fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Background Play Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Arka Planda Oynatma",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Uygulama arka plana alındığında sesi çalmaya devam eder.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.backgroundPlay,
                            onCheckedChange = {
                                viewModel.updateSettings(settings.copy(backgroundPlay = it))
                            }
                        )
                    }
                }
            }

            // Section: Browser & Search Engine Settings
            SettingsSectionHeader(title = "Tarayıcı & Arama Motoru", icon = Icons.Default.TravelExplore)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Varsayılan Arama Motoru",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Adres çubuğunda yapılan aramaların yönlendirileceği motor",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val searchEngines = listOf(
                        "Google" to "Google",
                        "Yandex" to "Yandex",
                        "DuckDuckGo" to "DuckDuckGo",
                        "Bing" to "Bing",
                        "Brave" to "Brave Search"
                    )

                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        searchEngines.forEach { (id, label) ->
                            val isSelected = settings.defaultSearchEngine.equals(id, ignoreCase = true) ||
                                    settings.defaultSearchEngine.equals(label, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.updateSettings(settings.copy(defaultSearchEngine = id))
                                },
                                label = { Text(label, fontSize = 12.sp) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Pop-up Blocker Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pop-up / Yeni Pencere Engelleyici",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "İstenmeyen yeni sekme ve açılır pencereleri engeller.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.popupBlockerEnabled,
                            onCheckedChange = {
                                viewModel.updateSettings(settings.copy(popupBlockerEnabled = it))
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Adblock Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DNS Reklam Engelleyici",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Mullvad & AdGuard filtreleriyle reklam ve takipçileri engeller.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.adblockEnabled,
                            onCheckedChange = {
                                viewModel.updateSettings(settings.copy(adblockEnabled = it))
                            }
                        )
                    }
                }
            }

            // Section: DNS & Network Configuration
            SettingsSectionHeader(title = "DNS & Ağ Güvenliği", icon = Icons.Default.Public)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Özel DNS (Custom DNS / DoH)",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Sistem DNS'i yerine kendi belirlediğiniz Güvenli DoH veya DNS sunucusunu kullanır.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.useCustomDns,
                            onCheckedChange = {
                                viewModel.updateSettings(settings.copy(useCustomDns = it))
                            }
                        )
                    }

                    if (settings.useCustomDns) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        Text(
                            text = "Hızlı DNS Seçimi",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val presets = listOf(
                                "Cloudflare" to "https://cloudflare-dns.com/dns-query",
                                "Google" to "https://dns.google/dns-query",
                                "AdGuard" to "https://dns.adguard-dns.com/dns-query",
                                "Quad9" to "https://dns.quad9.net/dns-query",
                                "Mullvad" to "https://dns.mullvad.net/dns-query"
                            )

                            presets.forEach { (name, url) ->
                                FilterChip(
                                    selected = settings.customDnsUrl == url,
                                    onClick = {
                                        customDnsInput = url
                                        dnsTestStatus = null
                                        viewModel.updateSettings(settings.copy(customDnsUrl = url))
                                    },
                                    label = { Text(name, fontSize = 12.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = customDnsInput,
                            onValueChange = {
                                customDnsInput = it
                                dnsTestStatus = null
                            },
                            label = { Text("Özel DNS / DoH URL veya IP") },
                            placeholder = { Text("https://dns.example.com/dns-query veya 1.1.1.1") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                if (customDnsInput.isNotBlank()) {
                                    IconButton(onClick = {
                                        customDnsInput = ""
                                        dnsTestStatus = null
                                        viewModel.updateSettings(settings.copy(customDnsUrl = ""))
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Temizle")
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (customDnsInput.isNotBlank()) {
                                        isTestingDns = true
                                        dnsTestStatus = "Test ediliyor..."
                                        coroutineScope.launch {
                                            val res = viewModel.testDnsConnection(customDnsInput)
                                            isTestingDns = false
                                            dnsTestStatus = if (res.isSuccess) {
                                                "✓ ${res.getOrNull()}"
                                            } else {
                                                "✗ Hata: ${res.exceptionOrNull()?.message ?: "Bağlantı kurulamadı"}"
                                            }
                                        }
                                    }
                                },
                                enabled = customDnsInput.isNotBlank() && !isTestingDns,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isTestingDns) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text("Bağlantıyı Test Et", fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    viewModel.updateSettings(settings.copy(customDnsUrl = customDnsInput.trim()))
                                    dnsTestStatus = "✓ Ayar kaydedildi ve uygulandı."
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Kaydet", fontSize = 13.sp)
                            }
                        }

                        dnsTestStatus?.let { status ->
                            Spacer(modifier = Modifier.height(10.dp))
                            val isSuccess = status.startsWith("✓")
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSuccess) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSuccess) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Section 2: Subtitle Settings
            SettingsSectionHeader(title = "Altyazı Ayarları", icon = Icons.Default.Subtitles)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Varsayılan Altyazı Dili",
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        subtitleLangs.forEach { lang ->
                            FilterChip(
                                selected = settings.subtitleLanguage == lang,
                                onClick = {
                                    viewModel.updateSettings(settings.copy(subtitleLanguage = lang))
                                },
                                label = { Text(lang, fontSize = 12.sp) }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Font Size Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Yazı Boyutu",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${settings.subtitleSizeSp} sp",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = settings.subtitleSizeSp.toFloat(),
                        onValueChange = {
                            viewModel.updateSettings(settings.copy(subtitleSizeSp = it.toInt()))
                        },
                        valueRange = 14f..28f,
                        steps = 7
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Color Choice
                    Text(
                        text = "Altyazı Rengi",
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        subtitleColors.forEach { (name, hex) ->
                            val parsedColor = when (hex) {
                                "#FFFF00" -> Color.Yellow
                                "#00FFFF" -> Color.Cyan
                                "#00FF00" -> Color.Green
                                else -> Color.White
                            }
                            Surface(
                                shape = CircleShape,
                                color = parsedColor,
                                border = if (settings.subtitleColorHex == hex) {
                                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                } else null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable {
                                        viewModel.updateSettings(settings.copy(subtitleColorHex = hex))
                                    }
                            ) {
                                if (settings.subtitleColorHex == hex) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section: TV Box & Kumanda (Alt Menüye Yönlendirme)
            SettingsSectionHeader(title = "TV Box & Kumanda", icon = Icons.Default.Tv)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isTvBoxMenuOpen = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.SettingsRemote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "TV Box & Kumanda Ayarları",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Sanal fare, D-Pad yön/tıklama, kaydırma ve medya kısayolları",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Aç",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Section 3: Storage & Database
            SettingsSectionHeader(title = "Depolama & Geçmiş", icon = Icons.Default.Storage)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Download Folder Selection
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dirPickerLauncher.launch(null) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "İndirme Klasörü",
                                fontWeight = FontWeight.SemiBold
                            )
                            val folderDisplay = if (settings.downloadDirectoryUri.isNotBlank()) {
                                try {
                                    val uri = Uri.parse(settings.downloadDirectoryUri)
                                    val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                                    docFile?.name ?: "Özel Klasör Seçili"
                                } catch (e: Exception) {
                                    "Özel Klasör Seçili"
                                }
                            } else {
                                "Uygulama İçi (Varsayılan)"
                            }
                            Text(
                                text = folderDisplay,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Klasör Seç",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showClearHistoryDialog = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "İzleme Geçmişini Temizle",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Kaldığınız yerler ve izleme geçmişiniz yerel veritabanından tamamen silinir.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

    // Comprehensive TV Remote Key Capture & Selection Dialog
    activeKeyTarget?.let { target ->
        val captureFocusRequester = remember { FocusRequester() }

        fun applyKey(keyCode: Int, friendlyName: String) {
            val updated = when (target.keyId) {
                "tv_mouse" -> settings.copy(tvMouseShortcutKeyCode = keyCode, tvMouseShortcutKeyName = friendlyName)
                "tv_dpad_up" -> settings.copy(tvKeyDpadUp = keyCode, tvKeyDpadUpName = friendlyName)
                "tv_dpad_down" -> settings.copy(tvKeyDpadDown = keyCode, tvKeyDpadDownName = friendlyName)
                "tv_dpad_left" -> settings.copy(tvKeyDpadLeft = keyCode, tvKeyDpadLeftName = friendlyName)
                "tv_dpad_right" -> settings.copy(tvKeyDpadRight = keyCode, tvKeyDpadRightName = friendlyName)
                "tv_dpad_center" -> settings.copy(tvKeyDpadCenter = keyCode, tvKeyDpadCenterName = friendlyName)
                "tv_scroll_mode" -> settings.copy(tvKeyScrollMode = keyCode, tvKeyScrollModeName = friendlyName)
                "tv_page_up" -> settings.copy(tvKeyPageUp = keyCode, tvKeyPageUpName = friendlyName)
                "tv_page_down" -> settings.copy(tvKeyPageDown = keyCode, tvKeyPageDownName = friendlyName)
                "key_play_pause" -> settings.copy(tvKeyPlayPause = keyCode, tvKeyPlayPauseName = friendlyName)
                "key_forward" -> settings.copy(tvKeyForward = keyCode, tvKeyForwardName = friendlyName)
                "key_rewind" -> settings.copy(tvKeyRewind = keyCode, tvKeyRewindName = friendlyName)
                "key_fullscreen" -> settings.copy(tvKeyFullscreen = keyCode, tvKeyFullscreenName = friendlyName)
                "key_subtitle" -> settings.copy(tvKeySubtitle = keyCode, tvKeySubtitleName = friendlyName)
                "key_audio" -> settings.copy(tvKeyAudioTrack = keyCode, tvKeyAudioTrackName = friendlyName)
                "key_mute" -> settings.copy(tvKeyMute = keyCode, tvKeyMuteName = friendlyName)
                "key_next" -> settings.copy(tvKeyNextVideo = keyCode, tvKeyNextVideoName = friendlyName)
                "key_prev" -> settings.copy(tvKeyPrevVideo = keyCode, tvKeyPrevVideoName = friendlyName)
                else -> settings
            }
            viewModel.updateSettings(updated)
            activeKeyTarget = null
        }

        DisposableEffect(Unit) {
            GlobalKeyCaptureHolder.isCapturing = true
            GlobalKeyCaptureHolder.onKeyCaptured = { keyCode ->
                val name = getKeyCodeFriendlyName(keyCode)
                applyKey(keyCode, name)
            }
            onDispose {
                GlobalKeyCaptureHolder.isCapturing = false
                GlobalKeyCaptureHolder.onKeyCaptured = null
            }
        }

        LaunchedEffect(Unit) {
            captureFocusRequester.requestFocus()
        }

        AlertDialog(
            onDismissRequest = { activeKeyTarget = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SettingsRemote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(target.title, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(captureFocusRequester)
                        .focusable()
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                val keyCode = keyEvent.nativeKeyEvent.keyCode
                                if (keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_ESCAPE) {
                                    val name = getKeyCodeFriendlyName(keyCode)
                                    applyKey(keyCode, name)
                                    true
                                } else false
                            } else false
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Live Listening Banner
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = "Kumandanızdan bir tuşa basın",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Homatics kumandanızdaki herhangi bir tuş otomatik algılanıp kaydedilecektir.",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Veya aşağıdaki listeden bir tuş seçin:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        defaultRemoteKeyOptions.forEach { opt ->
                            val isSelected = target.currentKeyCode == opt.keyCode
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        applyKey(opt.keyCode, opt.name)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(opt.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(opt.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { activeKeyTarget = null }) {
                    Text("İptal")
                }
            }
        )
    }

    // Clear History Dialog
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Tüm Geçmişi Sil?") },
            text = { Text("Cihazdaki tüm izleme geçmişi verileri kalıcı olarak sıfırlanacaktır.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Geçmişi Sıfırla")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
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
