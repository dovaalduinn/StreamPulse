package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.player.CustomVideoPlayer
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.IptvScreen
import com.example.ui.screens.LinkInputScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.MediaPlayerViewModel

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : Screen("home", "Ana Sayfa", Icons.Filled.Home, Icons.Outlined.Home)
    object LinkInput : Screen("link_input", "Oynat", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircleOutline)
    object Browser : Screen("browser", "Tarayıcı", Icons.Filled.Language, Icons.Outlined.Language)
    object Downloads : Screen("downloads", "İndirmeler", Icons.Filled.FileDownload, Icons.Outlined.FileDownload)
    object History : Screen("history", "Kütüphane", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary)
    object Iptv : Screen("iptv", "Canlı TV", Icons.Filled.Tv, Icons.Filled.Tv)
    object Settings : Screen("settings", "Ayarlar", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun MainScreen(
    viewModel: MediaPlayerViewModel = viewModel()
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var browserStartUrl by remember { mutableStateOf<String?>(null) }
    val activeVideo by viewModel.activeVideo.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val activeDownloadCount = remember(downloads) {
        downloads.count { it.status == "DOWNLOADING" || it.status == "QUEUED" }
    }

    val navItems = listOf(
        Screen.Home,
        Screen.LinkInput,
        Screen.Browser,
        Screen.Iptv,
        Screen.Downloads,
        Screen.History,
        Screen.Settings
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (activeVideo == null && currentScreen != Screen.Browser) {
                    NavigationBar {
                        navItems.forEach { screen ->
                            val isSelected = currentScreen.route == screen.route
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { currentScreen = screen },
                                label = { 
                                    Text(
                                        text = screen.title,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        fontSize = 10.sp
                                    ) 
                                },
                                alwaysShowLabel = true,
                                icon = {
                                    if (screen == Screen.Downloads && activeDownloadCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge {
                                                    Text(
                                                        text = "$activeDownloadCount",
                                                        fontSize = 9.sp
                                                    )
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                                contentDescription = screen.title
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                            contentDescription = screen.title
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("nav_${screen.route}")
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues).consumeWindowInsets(paddingValues)
            ) {
                when (currentScreen) {
                    Screen.Home -> HomeScreen(
                        viewModel = viewModel,
                        onOpenBrowser = { url -> 
                            browserStartUrl = url
                            currentScreen = Screen.Browser
                        }
                    )
                    Screen.LinkInput -> LinkInputScreen(
                        viewModel = viewModel,
                        onOpenBrowser = { currentScreen = Screen.Browser }
                    )
                    Screen.Browser -> {
                        BrowserScreen(
                            viewModel = viewModel,
                            onBackToHome = { currentScreen = Screen.Home },
                            startUrl = browserStartUrl,
                            onStartUrlConsumed = { browserStartUrl = null }
                        )
                    }
                    Screen.Downloads -> DownloadsScreen(
                        viewModel = viewModel
                    )
                    Screen.History -> HistoryScreen(
                        viewModel = viewModel,
                        onOpenBrowser = { url ->
                            browserStartUrl = url
                            currentScreen = Screen.Browser
                        }
                    )
                    Screen.Iptv -> IptvScreen(
                        viewModel = viewModel
                    )
                    Screen.Settings -> SettingsScreen(
                        viewModel = viewModel
                    )
                }
            }
        }

        // Fullscreen Player Overlay
        AnimatedVisibility(
            visible = activeVideo != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            activeVideo?.let { videoState ->
                CustomVideoPlayer(
                    title = videoState.title,
                    url = videoState.url,
                    audioUrl = videoState.audioUrl,
                    subtitles = videoState.subtitles,
                    startPositionMs = videoState.startPositionMs,
                    isLive = videoState.isLive,
                    availableQualities = videoState.availableQualities,
                    isOffline = videoState.isOffline,
                    settings = settings,
                    referer = videoState.referer,
                    userAgent = videoState.userAgent,
                    headers = videoState.headers,
                    playerActionFlow = viewModel.playerActionFlow,
                    onProgressUpdate = { positionMs, durationMs ->
                        viewModel.updateWatchProgress(positionMs, durationMs)
                    },
                    onQualitySelected = { newUrl, positionMs ->
                        viewModel.switchVideoQuality(newUrl, positionMs)
                    },
                    onDownloadClick = {
                        val type = when {
                            videoState.url.contains(".m3u8") || videoState.url.contains("m3u8") -> "HLS_DOWNLOAD"
                            videoState.url.contains(".mpd") || videoState.url.contains("mpd") -> "DASH_DOWNLOAD"
                            else -> "DIRECT_DOWNLOAD"
                        }
                        viewModel.startDownload(
                            title = videoState.title,
                            url = videoState.url,
                            posterUrl = videoState.posterUrl ?: "",
                            type = type,
                            referer = videoState.referer,
                            userAgent = videoState.userAgent,
                            headers = videoState.headers
                        )
                    },
                    onClosePlayer = { pos, dur ->
                        viewModel.closePlayer(pos, dur)
                    }
                )
            }
        }

        // Global Virtual Mouse Overlay (Accessible on ALL screens: Browser, Top Bar, Bottom Bar, Player, Settings, etc.)
        val isTvCursorActive by viewModel.isTvCursorActive.collectAsState()
        val isDragScrollMode by viewModel.isDragScrollMode.collectAsState()
        val cursorX by viewModel.cursorX.collectAsState()
        val cursorY by viewModel.cursorY.collectAsState()
        val isCursorClicking by viewModel.isCursorClicking.collectAsState()
        val hudMessage by viewModel.cursorHudMessage.collectAsState()

        val isHighlighted = isCursorClicking || isDragScrollMode

        val clickScale by animateFloatAsState(
            targetValue = if (isHighlighted) 0.8f else 1f,
            label = "cursorClickScale"
        )

        LaunchedEffect(hudMessage) {
            if (hudMessage != null) {
                delay(2600)
                viewModel.clearCursorHudMessage()
            }
        }

        if (isTvCursorActive) {
            // TV Cursor Pointer Drawing
            val themeColor = when {
                isDragScrollMode -> Color(0xFFFFB300) // Amber for Drag/Scroll Mode
                isCursorClicking -> Color(0xFFFF9100) // Orange for Click/Drag
                else -> Color(0xFF00E5FF) // Cyan for Default Cursor
            }
            val glowColor = when {
                isDragScrollMode -> Color(0x77FFB300)
                isCursorClicking -> Color(0x77FF9100)
                else -> Color(0x6600E5FF)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Canvas(
                    modifier = Modifier
                        .offset { IntOffset((cursorX - 24f).roundToInt(), (cursorY - 24f).roundToInt()) }
                        .size(48.dp)
                ) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val baseRadius = 14.dp.toPx() * clickScale

                    // Outer ambient glow ring
                    drawCircle(
                        color = glowColor,
                        radius = baseRadius + (if (isHighlighted) 7.dp.toPx() else 4.dp.toPx()),
                        center = center
                    )

                    // Target ring
                    drawCircle(
                        color = themeColor,
                        radius = baseRadius,
                        center = center,
                        style = Stroke(width = if (isHighlighted) 3.dp.toPx() else 2.2.dp.toPx())
                    )

                    // Drag state inner highlight when holding OK or in scroll mode
                    if (isHighlighted) {
                        drawCircle(
                            color = if (isDragScrollMode) Color(0x44FFB300) else Color(0x44FF9100),
                            radius = baseRadius * 0.7f,
                            center = center
                        )
                    }

                    // Center dot
                    drawCircle(
                        color = if (isHighlighted) Color(0xFFFF3D00) else Color.White,
                        radius = (if (isHighlighted) 5.5.dp.toPx() else 3.8.dp.toPx()),
                        center = center
                    )

                    // Crosshair guides
                    val crossLength = (if (isHighlighted) 8.5.dp.toPx() else 7.dp.toPx())
                    drawLine(themeColor, Offset(center.x - baseRadius - crossLength, center.y), Offset(center.x - baseRadius + 2.dp.toPx(), center.y), strokeWidth = 2.dp.toPx())
                    drawLine(themeColor, Offset(center.x + baseRadius - 2.dp.toPx(), center.y), Offset(center.x + baseRadius + crossLength, center.y), strokeWidth = 2.dp.toPx())
                    drawLine(themeColor, Offset(center.x, center.y - baseRadius - crossLength), Offset(center.x, center.y - baseRadius + 2.dp.toPx()), strokeWidth = 2.dp.toPx())
                    drawLine(themeColor, Offset(center.x, center.y + baseRadius - 2.dp.toPx()), Offset(center.x, center.y + baseRadius + crossLength), strokeWidth = 2.dp.toPx())

                    // Pointer arrow top-left
                    val arrowPath = Path().apply {
                        moveTo(center.x - 3.dp.toPx(), center.y - 3.dp.toPx())
                        lineTo(center.x - 14.dp.toPx(), center.y - 4.dp.toPx())
                        lineTo(center.x - 4.dp.toPx(), center.y - 14.dp.toPx())
                        close()
                    }
                    drawPath(arrowPath, color = themeColor)
                }
            }
        }

        // Global HUD Notification (when turning on/off mouse or changing settings)
        AnimatedVisibility(
            visible = hudMessage != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -60 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -60 }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.94f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isTvCursorActive) Icons.Default.Mouse else Icons.Default.Tv,
                        contentDescription = null,
                        tint = if (isTvCursorActive) Color(0xFF00E5FF) else Color(0xFFFF5252),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = hudMessage.orEmpty(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
