package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.IptvPlaylistEntity
import com.example.ui.viewmodel.MediaPlayerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IptvScreen(
    viewModel: MediaPlayerViewModel,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<IptvPlaylistEntity?>(null) }
    var channels by remember { mutableStateOf<List<IptvChannel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf("Tümü") }
    
    val playlists by viewModel.iptvPlaylists.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(selectedPlaylist) {
        if (selectedPlaylist != null && !selectedPlaylist!!.isSingleChannel) {
            isLoading = true
            channels = IptvParser.parseM3u(selectedPlaylist!!.url)
            selectedGroup = "Tümü"
            isLoading = false
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var isSingle by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(if (isSingle) "Tekli Medya/Kanal Ekle" else "IPTV Listesi Ekle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TabRow(selectedTabIndex = if (isSingle) 1 else 0) {
                        Tab(
                            selected = !isSingle,
                            onClick = { isSingle = false },
                            text = { Text("M3U Listesi") }
                        )
                        Tab(
                            selected = isSingle,
                            onClick = { isSingle = true },
                            text = { Text("Tekli Kanal") }
                        )
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Adı") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("URL (örn: .m3u8, .mp4)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank() && url.isNotBlank()) {
                        viewModel.addIptvPlaylist(name, url, isSingle)
                        showAddDialog = false
                    }
                }) {
                    Text("Ekle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (selectedPlaylist != null) {
                        Text(selectedPlaylist!!.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Text("IPTV Listelerim", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    if (selectedPlaylist != null) {
                        IconButton(onClick = { selectedPlaylist = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                        }
                    }
                },
                actions = {
                    if (selectedPlaylist == null) {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Ekle")
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (selectedPlaylist == null) {
                // List of Playlists
                if (playlists.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Henüz bir medya veya IPTV listesi eklenmedi.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { showAddDialog = true }) {
                                Text("İçerik Ekle")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(playlists) { playlist ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (playlist.isSingleChannel) {
                                        viewModel.playVideo(title = playlist.name, url = playlist.url)
                                    } else {
                                        selectedPlaylist = playlist
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        if (playlist.isSingleChannel) {
                                            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        } else {
                                            Icon(Icons.Default.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(playlist.name, fontWeight = FontWeight.Bold)
                                            Text(playlist.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    IconButton(onClick = { viewModel.deleteIptvPlaylist(playlist) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Show Channels
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (channels.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Kanal bulunamadı veya liste yüklenemedi.", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    val groups = listOf("Tümü") + channels.map { it.group }.distinct().sorted()
                    val filteredChannels = if (selectedGroup == "Tümü") channels else channels.filter { it.group == selectedGroup }
                    
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Group Selector
                        ScrollableTabRow(
                            selectedTabIndex = groups.indexOf(selectedGroup).takeIf { it >= 0 } ?: 0,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            groups.forEach { group ->
                                Tab(
                                    selected = selectedGroup == group,
                                    onClick = { selectedGroup = group },
                                    text = { Text(group, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                        }
                        
                        // Channels Grid
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(150.dp),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredChannels) { channel ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable {
                                        viewModel.playVideo(title = channel.name, url = channel.url)
                                    },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        if (channel.logo.isNotBlank()) {
                                            AsyncImage(
                                                model = channel.logo,
                                                contentDescription = channel.name,
                                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Fit
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                        } else {
                                            Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                        Text(
                                            text = channel.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
