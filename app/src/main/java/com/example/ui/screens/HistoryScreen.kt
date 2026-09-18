package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WatchHistoryEntity
import com.example.data.model.DownloadItemEntity
import com.example.data.model.BrowserHistoryEntity
import com.example.ui.viewmodel.MediaPlayerViewModel
import com.example.util.MediaTitleHelper
import com.example.util.MediaTitleParsed
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SdCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class SeriesGroup(
    val seriesName: String,
    val seasons: Map<String, List<WatchHistoryEntity>>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: MediaPlayerViewModel,
    onOpenBrowser: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val historyList by viewModel.historyList.collectAsState()
    val downloadsList by viewModel.downloads.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Kütüphane",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (selectedTabIndex) {
                                0 -> "İzleme geçmişiniz ve kayıtlarınız"
                                else -> "Çevrimdışı izlenebilir indirilen medyalar"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (selectedTabIndex == 0 && historyList.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.testTag("clear_history_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "İzleme Geçmişini Temizle",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("İzleme Geçmişi", fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("İndirilenler", fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal)
                            val activeCount = downloadsList.count { it.status == "DOWNLOADING" || it.status == "QUEUED" }
                            if (activeCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                    Text("$activeCount")
                                }
                            }
                        }
                    }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTabIndex) {
                    0 -> HistoryContent(
                        historyList = historyList,
                        viewModel = viewModel
                    )
                    else -> DownloadsContent(
                        downloadsList = downloadsList,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("İzleme Geçmişini Sıfırla") },
            text = { Text("Tüm video izleme geçmişiniz silinecektir. Bu işlem geri alınamaz.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Evet, Temizle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }
}

@Composable
fun HistoryContent(
    historyList: List<WatchHistoryEntity>,
    viewModel: MediaPlayerViewModel
) {
    if (historyList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "İzleme geçmişiniz henüz boş.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "İzlediğiniz videolar ve yayınlar otomatik olarak buraya eklenir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        val standalone = mutableListOf<WatchHistoryEntity>()
        val seriesMap = mutableMapOf<String, MutableMap<String, MutableList<WatchHistoryEntity>>>()
        
        historyList.forEach { item ->
            val parsed = MediaTitleHelper.parseTitle(item.title)
            if (parsed.isSeries && parsed.seriesName.isNotEmpty()) {
                seriesMap.getOrPut(parsed.seriesName) { mutableMapOf() }
                         .getOrPut(parsed.seasonName) { mutableListOf() }
                         .add(item)
            } else {
                standalone.add(item)
            }
        }
        
        val seriesGroups = seriesMap.map { (name, seasonsMap) ->
            val deduplicatedSeasons = seasonsMap.mapValues { (_, episodes) ->
                // Deduplicate episodes with the same episode name/number, picking the latest watched
                episodes.groupBy { MediaTitleHelper.parseTitle(it.title).episodeName }
                    .map { (_, epList) -> epList.maxByOrNull { it.lastWatchedTimestamp } ?: epList.first() }
                    .sortedBy { MediaTitleHelper.parseTitle(it.title).episodeNumber }
            }
            SeriesGroup(name, deduplicatedSeasons)
        }.sortedBy { it.seriesName }

        // Deduplicate standalone items by clean title/url, picking the latest watched
        val deduplicatedStandalone = standalone
            .groupBy {
                val parsed = MediaTitleHelper.parseTitle(it.title)
                if (parsed.cleanTitle.isNotBlank()) parsed.cleanTitle.lowercase() else it.url
            }
            .map { (_, list) -> list.maxByOrNull { it.lastWatchedTimestamp } ?: list.first() }
            .sortedByDescending { it.lastWatchedTimestamp }

        var selectedSeriesGroup by remember { mutableStateOf<SeriesGroup?>(null) }
        var expandedSeasons by remember { mutableStateOf<Set<String>>(emptySet()) }

        BackHandler(enabled = selectedSeriesGroup != null) {
            selectedSeriesGroup = null
        }

        Crossfade(targetState = selectedSeriesGroup, label = "HistoryContentTransition") { activeGroup ->
            if (activeGroup == null) {
                // Main List (Folders + Standalone)
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Render Series Folders
                    items(seriesGroups, key = { "series_${it.seriesName}" }) { seriesGroup ->
                        FolderHeader(
                            title = seriesGroup.seriesName,
                            subtitle = "${seriesGroup.seasons.values.sumOf { it.size }} Bölüm",
                            onClick = { selectedSeriesGroup = seriesGroup }
                        )
                    }
                    
                    // Standalone Movies & Videos
                    if (deduplicatedStandalone.isNotEmpty() && seriesGroups.isNotEmpty()) {
                        item {
                            Text(
                                text = "Filmler ve Diğer Videolar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                    }

                    items(deduplicatedStandalone, key = { it.id }) { item ->
                        HistoryCardItem(
                            item = item,
                            displayTitle = item.title,
                            isIndented = false,
                            onResumeClick = {
                                viewModel.playVideo(
                                    title = item.title,
                                    url = item.url,
                                    startPositionMs = item.lastPositionMs
                                )
                            },
                            onDeleteClick = {
                                viewModel.deleteHistoryItem(item.id)
                            }
                        )
                    }
                }
            } else {
                // Series Detail List
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header for Series Detail
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSeriesGroup = null }
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        IconButton(onClick = { selectedSeriesGroup = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Geri Dön",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = activeGroup.seriesName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        activeGroup.seasons.forEach { (seasonName, episodes) ->
                            val seasonKey = "${activeGroup.seriesName}_$seasonName"
                            item(key = "season_$seasonKey") {
                                val isSeasonExpanded = expandedSeasons.contains(seasonKey)
                                SeasonHeader(
                                    title = seasonName,
                                    subtitle = "${episodes.size} Bölüm",
                                    isExpanded = isSeasonExpanded,
                                    onClick = {
                                        expandedSeasons = if (isSeasonExpanded) expandedSeasons - seasonKey else expandedSeasons + seasonKey
                                    }
                                )
                            }

                            if (expandedSeasons.contains(seasonKey)) {
                                items(episodes, key = { it.id }) { item ->
                                    val parsed = MediaTitleHelper.parseTitle(item.title)
                                    HistoryCardItem(
                                        item = item,
                                        displayTitle = parsed.episodeName.ifBlank { item.title },
                                        isIndented = true,
                                        onResumeClick = {
                                            viewModel.playVideo(
                                                title = item.title,
                                                url = item.url,
                                                startPositionMs = item.lastPositionMs
                                            )
                                        },
                                        onDeleteClick = {
                                            viewModel.deleteHistoryItem(item.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class DownloadSeriesGroup(
    val seriesName: String,
    val seasons: Map<String, List<DownloadItemEntity>>
)

@Composable
fun DownloadsContent(
    downloadsList: List<DownloadItemEntity>,
    viewModel: MediaPlayerViewModel
) {
    if (downloadsList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Henüz İndirilen Medya Yok",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Oynatıcıdan, web tarayıcısından veya link giriş ekranından videoları çevrimdışı izlemek için tek tıkla indirebilirsiniz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        // Group completed downloads into Series / Folders & Standalone
        val standalone = mutableListOf<DownloadItemEntity>()
        val seriesMap = mutableMapOf<String, MutableMap<String, MutableList<DownloadItemEntity>>>()

        downloadsList.forEach { item ->
            val parsed = MediaTitleHelper.parseTitle(item.title)
            if (parsed.isSeries && parsed.seriesName.isNotEmpty()) {
                seriesMap.getOrPut(parsed.seriesName) { mutableMapOf() }
                    .getOrPut(parsed.seasonName) { mutableListOf() }
                    .add(item)
            } else {
                standalone.add(item)
            }
        }

        val seriesGroups = seriesMap.map { (name, seasonsMap) ->
            val sortedSeasons = seasonsMap.mapValues { (_, episodes) ->
                episodes.sortedBy { MediaTitleHelper.parseTitle(it.title).episodeNumber }
            }
            DownloadSeriesGroup(name, sortedSeasons)
        }.sortedBy { it.seriesName }

        var selectedDownloadSeriesGroup by remember { mutableStateOf<DownloadSeriesGroup?>(null) }
        var expandedDownloadSeasons by remember { mutableStateOf<Set<String>>(emptySet()) }

        BackHandler(enabled = selectedDownloadSeriesGroup != null) {
            selectedDownloadSeriesGroup = null
        }

        Crossfade(targetState = selectedDownloadSeriesGroup, label = "DownloadsContentTransition") { activeGroup ->
            if (activeGroup == null) {
                // Main Downloads Folder View
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Series Folders
                    items(seriesGroups, key = { "dl_series_${it.seriesName}" }) { group ->
                        val totalBytes = group.seasons.values.flatten().sumOf { it.downloadedBytes }
                        FolderHeader(
                            title = group.seriesName,
                            subtitle = "${group.seasons.values.sumOf { it.size }} Bölüm • ${formatBytes(totalBytes)}",
                            onClick = { selectedDownloadSeriesGroup = group }
                        )
                    }

                    // Standalone Videos & Movies
                    if (standalone.isNotEmpty() && seriesGroups.isNotEmpty()) {
                        item {
                            Text(
                                text = "Filmler ve Tekil Videolar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                    }

                    items(standalone, key = { "dl_standalone_${it.id}" }) { item ->
                        if (item.status == "COMPLETED") {
                            CompletedDownloadCard(
                                item = item,
                                onPlay = { viewModel.playDownloadedVideo(item) },
                                onDelete = { viewModel.deleteDownload(item.id) }
                            )
                        } else {
                            ActiveDownloadCard(
                                item = item,
                                onPause = { viewModel.pauseDownload(item.id) },
                                onResume = { viewModel.resumeDownload(item.id) },
                                onCancel = { viewModel.deleteDownload(item.id) }
                            )
                        }
                    }
                }
            } else {
                // Inside Series Folder
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDownloadSeriesGroup = null }
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        IconButton(onClick = { selectedDownloadSeriesGroup = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Geri Dön",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = activeGroup.seriesName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        activeGroup.seasons.forEach { (seasonName, episodes) ->
                            val seasonKey = "${activeGroup.seriesName}_$seasonName"
                            item(key = "dl_season_$seasonKey") {
                                val isSeasonExpanded = expandedDownloadSeasons.contains(seasonKey)
                                val seasonBytes = episodes.sumOf { it.downloadedBytes }
                                SeasonHeader(
                                    title = seasonName,
                                    subtitle = "${episodes.size} Bölüm • ${formatBytes(seasonBytes)}",
                                    isExpanded = isSeasonExpanded,
                                    onClick = {
                                        expandedDownloadSeasons = if (isSeasonExpanded) {
                                            expandedDownloadSeasons - seasonKey
                                        } else {
                                            expandedDownloadSeasons + seasonKey
                                        }
                                    }
                                )
                            }

                            if (expandedDownloadSeasons.contains(seasonKey)) {
                                items(episodes, key = { "dl_ep_${it.id}" }) { item ->
                                    val parsed = MediaTitleHelper.parseTitle(item.title)
                                    val epTitle = parsed.episodeName.ifBlank { item.title }
                                    if (item.status == "COMPLETED") {
                                        CompletedDownloadCard(
                                            item = item.copy(title = epTitle),
                                            onPlay = { viewModel.playDownloadedVideo(item) },
                                            onDelete = { viewModel.deleteDownload(item.id) }
                                        )
                                    } else {
                                        ActiveDownloadCard(
                                            item = item.copy(title = epTitle),
                                            onPause = { viewModel.pauseDownload(item.id) },
                                            onResume = { viewModel.resumeDownload(item.id) },
                                            onCancel = { viewModel.deleteDownload(item.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveDownloadCard(
    item: DownloadItemEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("download_card_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when (item.status) {
                                "DOWNLOADING" -> MaterialTheme.colorScheme.primaryContainer
                                "PAUSED" -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.errorContainer
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (item.status) {
                            "DOWNLOADING" -> Icons.Default.Download
                            "PAUSED" -> Icons.Default.Pause
                            else -> Icons.Default.ErrorOutline
                        },
                        contentDescription = null,
                        tint = when (item.status) {
                            "DOWNLOADING" -> MaterialTheme.colorScheme.primary
                            "PAUSED" -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    val statusText = when (item.status) {
                        "DOWNLOADING" -> if (item.speed.isNotBlank()) "${item.speed} • %${item.progress}" else "%${item.progress}"
                        "PAUSED" -> "Duraklatıldı • %${item.progress}"
                        "QUEUED" -> "Kuyrukta bekliyor..."
                        "FAILED" -> item.errorMessage ?: "İndirme başarısız"
                        else -> item.status
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.status == "FAILED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (item.status == "DOWNLOADING") {
                    IconButton(onClick = onPause) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Duraklat",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (item.status == "PAUSED" || item.status == "FAILED") {
                    IconButton(onClick = onResume) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Devam Et",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "İptal Et",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { (item.progress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = when (item.status) {
                    "FAILED" -> MaterialTheme.colorScheme.error
                    "PAUSED" -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            if (item.downloadedBytes > 0 || item.totalBytes > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${formatBytes(item.downloadedBytes)} / ${if (item.totalBytes > 0) formatBytes(item.totalBytes) else "--"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "%${item.progress}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun CompletedDownloadCard(
    item: DownloadItemEntity,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .testTag("completed_download_${item.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(height = 52.dp, width = 80.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Çevrimdışı Hazır • ${formatBytes(item.downloadedBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatDate(item.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Sil",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.getDefault(), "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.getDefault(), "%.0f KB", kb)
        else -> "$bytes B"
    }
}

@Composable
fun FolderHeader(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "İçeri Gir",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SeasonHeader(title: String, subtitle: String, isExpanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), // reduced start padding slightly since we are in a detail view now
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun HistoryCardItem(
    item: WatchHistoryEntity,
    displayTitle: String,
    isIndented: Boolean,
    onResumeClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isIndented) 16.dp else 0.dp) // reduced indentation since we are in detail view now
            .clickable { onResumeClick() }
            .testTag("history_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Video Preview Thumbnail Placeholder
                Box(
                    modifier = Modifier
                        .size(height = 50.dp, width = 80.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatDate(item.lastWatchedTimestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Progress Text
                    val positionFormatted = formatDuration(item.lastPositionMs)
                    val totalFormatted = formatDuration(item.durationMs)
                    val progressText = if (item.durationMs > 0) {
                        "Kaldığı Yer: $positionFormatted / $totalFormatted"
                    } else {
                        "Kaldığı Yer: $positionFormatted"
                    }
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Sil",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Progress Bar
            LinearProgressIndicator(
                progress = { item.progressPercentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.Builder().setLanguage("tr").build())
    return sdf.format(Date(timestamp))
}

private fun formatDuration(millis: Long): String {
    if (millis <= 0) return "00:00"
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
