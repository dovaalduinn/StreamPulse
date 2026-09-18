package com.example.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import kotlin.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape

import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusable

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.common.MimeTypes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.PlayerSettingsEntity
import com.example.ui.viewmodel.SniffedMedia
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit

val VlcOrange = Color(0xFFFF7700)
val VlcBufferOrange = Color(0xFFFFB366).copy(alpha = 0.65f)
val VlcBufferYellow = Color(0xFFFFEB3B).copy(alpha = 0.90f)
val VlcBufferGreen = Color(0xFF4CAF50)

data class TrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val name: String,
    val isSelected: Boolean,
    val trackGroup: TrackGroup
)


fun Modifier.tvFocusHighlight(shape: Shape = CircleShape): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    this.onFocusChanged { isFocused = it.isFocused }
        .focusable(true)
        .border(if (isFocused) 2.dp else 0.dp, if (isFocused) VlcOrange else Color.Transparent, shape)
}

@android.annotation.SuppressLint("UnsafeOptInUsageError")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomVideoPlayer(
    title: String,
    url: String,
    audioUrl: String? = null,
    subtitles: List<com.example.ui.viewmodel.SubtitleTrack> = emptyList(),
    startPositionMs: Long = 0L,
    isLive: Boolean = false,
    availableQualities: List<SniffedMedia> = emptyList(),
    isOffline: Boolean = false,
    settings: PlayerSettingsEntity,
    referer: String? = null,
    userAgent: String? = null,
    headers: Map<String, String> = emptyMap(),
    playerActionFlow: kotlinx.coroutines.flow.SharedFlow<com.example.ui.viewmodel.PlayerRemoteAction>? = null,
    onProgressUpdate: (positionMs: Long, durationMs: Long) -> Unit,
    onQualitySelected: (String, Long) -> Unit = { _, _ -> },
    onDownloadClick: (() -> Unit)? = null,
    onClosePlayer: (currentPositionMs: Long, totalDurationMs: Long) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val window = activity?.window
    val coroutineScope = rememberCoroutineScope()

    val isDownloadedVideo = isOffline || url.startsWith("file://") || url.startsWith("content://") || url.startsWith("/")
    val isLiveStream = isLive
    val effectiveFastCacheMode = if (isDownloadedVideo || isLiveStream) 0 else settings.fastCacheMode

    // Temporary session cache with full auto-cleanup on dispose
    val exoPlayerAndCache = remember(effectiveFastCacheMode, settings.allowInsecureSsl, settings.ramBufferLimitMb) {
        val newCacheManager = PlayerSessionCacheManager(context, referer, userAgent, headers, settings.allowInsecureSsl, settings.ramBufferLimitMb)
        val newPlayer = newCacheManager.buildExoPlayer(effectiveFastCacheMode, isLiveStream)
        Pair(newPlayer, newCacheManager)
    }
    val exoPlayer = exoPlayerAndCache.first
    val cacheManager = exoPlayerAndCache.second

    // Screen lock in landscape mode and immersive mode
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        window?.let {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(it, false)
            val insetsController = androidx.core.view.WindowInsetsControllerCompat(it, it.decorView)
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            activity?.requestedOrientation = originalOrientation
            window?.let {
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(it, true)
                androidx.core.view.WindowInsetsControllerCompat(it, it.decorView).show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    var hasAppliedInitialSeek by remember(url) { mutableStateOf(startPositionMs <= 0L) }

    BackHandler {
        val curPos = exoPlayer.currentPosition.coerceAtLeast(0L)
        val dur = exoPlayer.duration.coerceAtLeast(0L)
        onClosePlayer(curPos, dur)
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(startPositionMs) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var diskBufferedPositionMs by remember { mutableLongStateOf(0L) }
    var contiguousDiskCacheRatio by remember { mutableFloatStateOf(0f) }
    var cachedSpansRatios by remember { mutableStateOf<List<PlayerSessionCacheManager.CacheSpanRange>>(emptyList()) }
    // Cumulative persistent cached spans across the entire session to ensure yellow bar never shrinks or disappears when seeking backwards
    var accumulatedCachedSpans by remember { mutableStateOf<List<PlayerSessionCacheManager.CacheSpanRange>>(emptyList()) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekSliderPosition by remember { mutableFloatStateOf(startPositionMs.toFloat()) }

    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }

    // Double tap feedback
    var doubleTapFeedback by remember { mutableStateOf<String?>(null) }

    var audioTracks by remember { mutableStateOf<List<TrackInfo>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<TrackInfo>>(emptyList()) }

    val aspectRatios = listOf(
        Pair("Sığdır (Orijinal)", AspectRatioFrameLayout.RESIZE_MODE_FIT),
        Pair("Doldur (Kırp)", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
        Pair("Tam Ekran (Uzat)", AspectRatioFrameLayout.RESIZE_MODE_FILL),
        Pair("Sabit Genişlik", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH),
        Pair("Sabit Yükseklik", AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT)
    )
    var currentAspectRatioIndex by remember { mutableIntStateOf(0) }
    var aspectRatioFeedback by remember { mutableStateOf<String?>(aspectRatios[0].first) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    var interactionTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) }
    var currentVolume by remember { mutableIntStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)) }

    var currentBrightness by remember {
        mutableFloatStateOf(
            try {
                android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
            } catch (_: Exception) {
                0.5f
            }
        )
    }

    var showVolumeIndicator by remember { mutableStateOf(false) }
    var volumeIndicatorValue by remember { mutableIntStateOf(0) }

    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var brightnessIndicatorValue by remember { mutableIntStateOf(0) }

    var showQualityMenu by remember { mutableStateOf(false) }

    fun clampTargetTimeToValidCachedSpans(targetMs: Long): Long {
        if (effectiveFastCacheMode != 1 || totalDurationMs <= 0L) return targetMs
        val targetRatio = (targetMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)

        val activeSpans = if (accumulatedCachedSpans.isNotEmpty()) accumulatedCachedSpans else cachedSpansRatios
        if (activeSpans.isEmpty()) {
            val fallbackRatio = contiguousDiskCacheRatio
            val maxAllowed = (totalDurationMs * fallbackRatio).toLong()
            return targetMs.coerceAtMost(maxAllowed)
        }

        // Direct match in cached spans
        for (span in activeSpans) {
            if (targetRatio in span.startRatio..span.endRatio) {
                return targetMs
            }
        }

        val precedingSpans = activeSpans.filter { it.startRatio <= targetRatio }
        return if (precedingSpans.isNotEmpty()) {
            val maxEndRatio = precedingSpans.maxOf { it.endRatio }
            (totalDurationMs * maxEndRatio).toLong().coerceAtMost(targetMs)
        } else {
            val minStartRatio = activeSpans.minOfOrNull { it.startRatio } ?: 0f
            (totalDurationMs * minStartRatio).toLong().coerceAtMost(targetMs)
        }
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragType by remember { mutableIntStateOf(0) }
    var dragStartY by remember { mutableFloatStateOf(0f) }
    var initialVolume by remember { mutableIntStateOf(0) }
    var initialBrightness by remember { mutableFloatStateOf(0f) }
    
    val playerFocusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        playerFocusRequester.requestFocus()
    }

    LaunchedEffect(currentBrightness) {
        window?.attributes = window?.attributes?.apply {
            screenBrightness = currentBrightness
        }
    }

    // Auto-hide controls timer
    LaunchedEffect(showControls, isPlaying, interactionTimestamp, isSeeking) {
        if (showControls && isPlaying && !isSeeking) {
            delay(4000)
            showControls = false
        }
    }

    // Initialize media playback and aggressive background preload
    LaunchedEffect(url) {
        val targetSeek = if (startPositionMs > 0) startPositionMs else exoPlayer.currentPosition
        val subtitleConfigs = subtitles.map { sub ->
            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                .setMimeType(if (sub.url.endsWith(".vtt") || sub.url.contains("vtt")) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
                .setLanguage(sub.language.ifBlank { "TR" })
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        }
        
        if (audioUrl != null) {
            val isHls = url.contains(".m3u8") || url.contains("m3u8") || isLive
            val videoMediaItem = MediaItem.Builder()
                .setUri(android.net.Uri.parse(url))
                .setSubtitleConfigurations(subtitleConfigs)
                .apply { 
                    if (isHls) setMimeType(MimeTypes.APPLICATION_M3U8) 
                    if (isLive) setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
                }
                .build()
            val audioMediaItem = MediaItem.fromUri(android.net.Uri.parse(audioUrl))
            
            val videoSource = DefaultMediaSourceFactory(cacheManager.smartDataSourceFactory).createMediaSource(videoMediaItem)
            val audioSource = DefaultMediaSourceFactory(cacheManager.smartDataSourceFactory).createMediaSource(audioMediaItem)
            
            val mergedSource = MergingMediaSource(videoSource, audioSource)
            exoPlayer.setMediaSource(mergedSource)
        } else {
            val isHls = url.contains(".m3u8") || url.contains("m3u8") || isLive
            val mediaItem = MediaItem.Builder()
                .setUri(android.net.Uri.parse(url))
                .setSubtitleConfigurations(subtitleConfigs)
                .apply { 
                    if (isHls) setMimeType(MimeTypes.APPLICATION_M3U8) 
                    if (isLive) setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
                }
                .build()
            exoPlayer.setMediaItem(mediaItem)
        }
        exoPlayer.prepare()
        if (targetSeek > 0) {
            exoPlayer.seekTo(targetSeek)
            currentPositionMs = targetSeek
            seekSliderPosition = targetSeek.toFloat()
        }
        exoPlayer.playWhenReady = true
        hasAppliedInitialSeek = true

        // Mod 1, Mod 2 & Mod 3 CacheWriter'ı arkaplanda çalıştırarak diske indirmeyi başlatır (çevrimdışı ve canlı videolarda çalışmaz)
        if (!isDownloadedVideo && !isLiveStream && (effectiveFastCacheMode == 1 || effectiveFastCacheMode == 2 || effectiveFastCacheMode == 3)) {
            cacheManager.startFastPreload(
                url = url,
                coroutineScope = coroutineScope,
                getCurrentPositionMs = { exoPlayer.currentPosition.coerceAtLeast(0L) },
                getTotalDurationMs = { exoPlayer.duration.coerceAtLeast(0L) }
            )
        }
    }

    // Progress updates loop
    LaunchedEffect(exoPlayer, hasAppliedInitialSeek) {
        while (true) {
            if (!isSeeking && hasAppliedInitialSeek) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration.coerceAtLeast(0L)
                currentPositionMs = pos
                
                val exoBuffered = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                val diskCacheRatio = if (isDownloadedVideo) 1f else cacheManager.getCachedPercentage(url)
                bufferedPositionMs = if (isDownloadedVideo) dur else exoBuffered
                diskBufferedPositionMs = if (isDownloadedVideo) dur else if (diskCacheRatio >= 0f && dur > 0) {
                    (dur * diskCacheRatio).toLong()
                } else {
                    0L
                }
                
                if (effectiveFastCacheMode != 0 && effectiveFastCacheMode != 3) {
                    contiguousDiskCacheRatio = cacheManager.getContiguousCachedRatio(url)
                    val diskSpans = cacheManager.getCachedSpansRatios(url)
                    cachedSpansRatios = diskSpans

                    // Merge diskSpans, current ExoPlayer RAM buffer [pos/dur .. exoBuffered/dur], and existing accumulatedSpans
                    val allSpans = mutableListOf<PlayerSessionCacheManager.CacheSpanRange>()
                    allSpans.addAll(accumulatedCachedSpans)
                    allSpans.addAll(diskSpans)
                    if (dur > 0L && exoBuffered > pos) {
                        val s = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                        val e = (exoBuffered.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                        if (e > s) {
                            allSpans.add(PlayerSessionCacheManager.CacheSpanRange(s, e))
                        }
                    }

                    if (allSpans.isNotEmpty()) {
                        val sorted = allSpans.sortedBy { it.startRatio }
                        val merged = mutableListOf<PlayerSessionCacheManager.CacheSpanRange>()
                        var curStart = sorted[0].startRatio
                        var curEnd = sorted[0].endRatio

                        for (i in 1 until sorted.size) {
                            val span = sorted[i]
                            if (span.startRatio <= curEnd + 0.005f) { // allow smooth adjacent bridge
                                curEnd = maxOf(curEnd, span.endRatio)
                            } else {
                                merged.add(PlayerSessionCacheManager.CacheSpanRange(curStart, curEnd))
                                curStart = span.startRatio
                                curEnd = span.endRatio
                            }
                        }
                        merged.add(PlayerSessionCacheManager.CacheSpanRange(curStart, curEnd))
                        accumulatedCachedSpans = merged
                    }
                } else {
                    contiguousDiskCacheRatio = 0f
                    cachedSpansRatios = emptyList()
                    accumulatedCachedSpans = emptyList()
                }
                
                totalDurationMs = dur
                if (dur > 0 && (pos > 0 || startPositionMs <= 0L)) {
                    onProgressUpdate(pos, dur)
                }
            }
            delay(150)
        }
    }

    // Player Event Listener & Track extraction
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            var reconnectAttempts = 0
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                    reconnectAttempts = 0
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("ExoPlayerError", "Hata kodu: ${error.errorCodeName} - ${error.message}", error)
                if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    exoPlayer.seekToDefaultPosition()
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                } else if (isLiveStream || error.cause is androidx.media3.datasource.HttpDataSource.HttpDataSourceException || error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) {
                    if (reconnectAttempts < 3) {
                        reconnectAttempts++
                        android.util.Log.w("ExoPlayerError", "Canlı yayın bağlantısı koptu, yeniden bağlanılıyor (Deneme $reconnectAttempts/3)...")
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            try {
                                exoPlayer.seekToDefaultPosition()
                                exoPlayer.prepare()
                                exoPlayer.playWhenReady = true
                            } catch (_: Exception) {}
                        }, 2500L)
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val audios = mutableListOf<TrackInfo>()
                val subtitles = mutableListOf<TrackInfo>()

                for (groupIndex in 0 until tracks.groups.size) {
                    val group = tracks.groups[groupIndex]
                    val trackGroup = group.mediaTrackGroup

                    if (group.type == C.TRACK_TYPE_AUDIO) {
                        for (trackIndex in 0 until trackGroup.length) {
                            val format = trackGroup.getFormat(trackIndex)
                            val name = format.label ?: format.language ?: "Ses ${audios.size + 1} (${format.sampleMimeType ?: ""})"
                            val selected = group.isTrackSelected(trackIndex)
                            audios.add(TrackInfo(groupIndex, trackIndex, name, selected, trackGroup))
                        }
                    } else if (group.type == C.TRACK_TYPE_TEXT) {
                        for (trackIndex in 0 until trackGroup.length) {
                            val format = trackGroup.getFormat(trackIndex)
                            val name = format.label ?: format.language ?: "Altyazı ${subtitles.size + 1}"
                            val selected = group.isTrackSelected(trackIndex)
                            subtitles.add(TrackInfo(groupIndex, trackIndex, name, selected, trackGroup))
                        }
                    }
                }
                audioTracks = audios
                subtitleTracks = subtitles
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            val curPos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration.coerceAtLeast(0L)
            if (hasAppliedInitialSeek && (curPos > 0 || startPositionMs <= 0L)) {
                onProgressUpdate(curPos, dur)
            }
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
            cacheManager.releaseAndCleanUp()
        }
    }

    // Remote Control Action Listener
    LaunchedEffect(playerActionFlow) {
        playerActionFlow?.collect { action ->
            when (action) {
                com.example.ui.viewmodel.PlayerRemoteAction.PLAY_PAUSE -> {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                        doubleTapFeedback = "Duraklatıldı"
                    } else {
                        exoPlayer.play()
                        doubleTapFeedback = "Oynatılıyor"
                    }
                    showControls = true
                    interactionTimestamp = System.currentTimeMillis()
                }
                com.example.ui.viewmodel.PlayerRemoteAction.FORWARD_10S -> {
                    if (!isLocked && totalDurationMs > 0) {
                        val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                        val targetTime = (currentPos + 10000L).coerceIn(0L, totalDurationMs)
                        val newTime = clampTargetTimeToValidCachedSpans(targetTime)
                        if (!isDownloadedVideo && !isLiveStream && effectiveFastCacheMode != 1) cacheManager.notifySeekOccurred()
                        exoPlayer.seekTo(newTime)
                        currentPositionMs = newTime
                        doubleTapFeedback = "+10s"
                        showControls = true
                        interactionTimestamp = System.currentTimeMillis()
                    }
                }
                com.example.ui.viewmodel.PlayerRemoteAction.REWIND_10S -> {
                    if (!isLocked && totalDurationMs > 0) {
                        val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                        val newTime = (currentPos - 10000L).coerceIn(0L, totalDurationMs)
                        if (!isDownloadedVideo && !isLiveStream && effectiveFastCacheMode != 1) cacheManager.notifySeekOccurred()
                        exoPlayer.seekTo(newTime)
                        currentPositionMs = newTime
                        doubleTapFeedback = "-10s"
                        showControls = true
                        interactionTimestamp = System.currentTimeMillis()
                    }
                }
                com.example.ui.viewmodel.PlayerRemoteAction.TOGGLE_FULLSCREEN -> {
                    currentAspectRatioIndex = (currentAspectRatioIndex + 1) % aspectRatios.size
                    playerViewRef?.resizeMode = aspectRatios[currentAspectRatioIndex].second
                    aspectRatioFeedback = aspectRatios[currentAspectRatioIndex].first
                    doubleTapFeedback = aspectRatios[currentAspectRatioIndex].first
                    showControls = true
                    interactionTimestamp = System.currentTimeMillis()
                }
                com.example.ui.viewmodel.PlayerRemoteAction.CYCLE_SUBTITLE -> {
                    if (subtitleTracks.isNotEmpty()) {
                        val currentIndex = subtitleTracks.indexOfFirst { it.isSelected }
                        val nextIndex = (currentIndex + 1) % (subtitleTracks.size + 1)
                        if (nextIndex < subtitleTracks.size) {
                            val track = subtitleTracks[nextIndex]
                            val override = TrackSelectionOverride(track.trackGroup, listOf(track.trackIndex))
                            val currentParams = exoPlayer.trackSelectionParameters
                            exoPlayer.trackSelectionParameters = currentParams.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).addOverride(override).build()
                            doubleTapFeedback = "Altyazı: ${track.name}"
                        } else {
                            val currentParams = exoPlayer.trackSelectionParameters
                            exoPlayer.trackSelectionParameters = currentParams.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                            doubleTapFeedback = "Altyazı: Kapalı"
                        }
                        showControls = true
                        interactionTimestamp = System.currentTimeMillis()
                    } else {
                        doubleTapFeedback = "Altyazı Bulunamadı"
                    }
                }
                com.example.ui.viewmodel.PlayerRemoteAction.CYCLE_AUDIO -> {
                    if (audioTracks.size > 1) {
                        val currentIndex = audioTracks.indexOfFirst { it.isSelected }
                        val nextIndex = (currentIndex + 1) % audioTracks.size
                        val track = audioTracks[nextIndex]
                        val override = TrackSelectionOverride(track.trackGroup, listOf(track.trackIndex))
                        val currentParams = exoPlayer.trackSelectionParameters
                        exoPlayer.trackSelectionParameters = currentParams.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).addOverride(override).build()
                        doubleTapFeedback = "Ses: ${track.name}"
                        showControls = true
                        interactionTimestamp = System.currentTimeMillis()
                    } else {
                        doubleTapFeedback = "Tek Ses Kanalı"
                    }
                }
                com.example.ui.viewmodel.PlayerRemoteAction.TOGGLE_MUTE -> {
                    val cur = exoPlayer.volume
                    if (cur > 0f) {
                        exoPlayer.volume = 0f
                        doubleTapFeedback = "🔇 Sessiz (MUTE)"
                    } else {
                        exoPlayer.volume = 1f
                        doubleTapFeedback = "🔊 Ses Açık"
                    }
                    showControls = true
                    interactionTimestamp = System.currentTimeMillis()
                }
                com.example.ui.viewmodel.PlayerRemoteAction.SHOW_CONTROLS -> {
                    showControls = !showControls
                    interactionTimestamp = System.currentTimeMillis()
                }
                else -> {}
            }
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                val isInPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    activity?.isInPictureInPictureMode == true
                } else false
                if (!isInPip && exoPlayer.isPlaying) {
                    exoPlayer.pause()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
            .focusable(true)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter -> {
                            showControls = !showControls
                            interactionTimestamp = System.currentTimeMillis()
                            true
                        }
                        Key.DirectionRight -> {
                            if (!isLocked && totalDurationMs > 0) {
                                 val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                                val targetTime = (currentPos + 10000L).coerceIn(0L, totalDurationMs)
                                val newTime = clampTargetTimeToValidCachedSpans(targetTime)
                                if (!isDownloadedVideo && !isLiveStream && effectiveFastCacheMode != 1) cacheManager.notifySeekOccurred()
                                exoPlayer.seekTo(newTime)
                                currentPositionMs = newTime
                                doubleTapFeedback = "+10s"
                                showControls = true
                                interactionTimestamp = System.currentTimeMillis()
                            }
                            true
                        }
                        Key.DirectionLeft -> {
                            if (!isLocked && totalDurationMs > 0) {
                                val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                                val newTime = (currentPos - 10000L).coerceIn(0L, totalDurationMs)
                                if (!isDownloadedVideo && !isLiveStream && effectiveFastCacheMode != 1) cacheManager.notifySeekOccurred()
                                exoPlayer.seekTo(newTime)
                                currentPositionMs = newTime
                                doubleTapFeedback = "-10s"
                                showControls = true
                                interactionTimestamp = System.currentTimeMillis()
                            }
                            true
                        }
                        else -> false
                    }
                } else if (event.type == KeyEventType.KeyDown) {
                    // Consume keydown to prevent other focus changes if we handle it in keyup
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.DirectionRight, Key.DirectionLeft -> true
                        else -> false
                    }
                } else {
                    false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                        interactionTimestamp = System.currentTimeMillis()
                    },
                    onDoubleTap = { offset ->
                        if (!isLocked && totalDurationMs > 0) {
                            val isRightSide = offset.x > size.width / 2
                            val seekOffset = if (isRightSide) 10000L else -10000L
                            val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                            
                            val rawTargetTime = (currentPos + seekOffset).coerceIn(0L, totalDurationMs)
                            val newTime = if (isRightSide) clampTargetTimeToValidCachedSpans(rawTargetTime) else rawTargetTime
                            
                            if (!isDownloadedVideo && !isLiveStream && effectiveFastCacheMode != 1) cacheManager.notifySeekOccurred()
                            exoPlayer.seekTo(newTime)
                            currentPositionMs = newTime
                            interactionTimestamp = System.currentTimeMillis()
                            doubleTapFeedback = if (isRightSide) "+10s" else "-10s"
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        if (!isLocked) {
                            // %15 üst ve %10 alt kısımları işletim sistemine (bildirim çubuğu/navigasyon) ayırıyoruz.
                            val topDeadZone = size.height * 0.15f
                            val bottomDeadZone = size.height * 0.90f
                            
                            if (offset.y > topDeadZone && offset.y < bottomDeadZone) {
                                isDragging = true
                                dragStartY = offset.y
                                dragType = if (offset.x > size.width / 2) 1 else 2

                                if (dragType == 1) {
                                    initialVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                                    volumeIndicatorValue = ((initialVolume.toFloat() / maxVolume) * 100).toInt()
                                    showVolumeIndicator = true
                                } else if (dragType == 2) {
                                    initialBrightness = currentBrightness
                                    brightnessIndicatorValue = (initialBrightness * 100).toInt()
                                    showBrightnessIndicator = true
                                }
                            } else {
                                // Hareketi yoksay (işletim sistemi kullanabilsin)
                                isDragging = false
                                dragType = 0
                            }
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        // dragType is preserved for AnimatedVisibility exit transition
                        showVolumeIndicator = false
                        showBrightnessIndicator = false
                    },
                    onDragCancel = {
                        isDragging = false
                        // dragType is preserved for AnimatedVisibility exit transition
                        showVolumeIndicator = false
                        showBrightnessIndicator = false
                    },
                    onVerticalDrag = { change, _ ->
                        if (!isLocked && isDragging) {
                            change.consume()
                            val deltaY = change.position.y - dragStartY
                            val dragFactor = -(deltaY / size.height) * 1.5f

                            if (dragType == 1) {
                                val newVolume = (initialVolume + dragFactor * maxVolume).toInt().coerceIn(0, maxVolume)
                                currentVolume = newVolume
                                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVolume, 0)
                                volumeIndicatorValue = ((newVolume.toFloat() / maxVolume) * 100).toInt()
                            } else if (dragType == 2) {
                                val newBrightness = (initialBrightness + dragFactor).coerceIn(0f, 1f)
                                currentBrightness = newBrightness
                                brightnessIndicatorValue = (newBrightness * 100).toInt()
                            }
                        }
                    }
                )
            }
    ) {
        // Media3 ExoPlayer View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = exoPlayer
                    useController = false
                    resizeMode = aspectRatios[currentAspectRatioIndex].second
                    keepScreenOn = true
                    playerViewRef = this
                }
            },
            update = { view ->
                view.resizeMode = aspectRatios[currentAspectRatioIndex].second
                view.keepScreenOn = isPlaying
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering Indicator Spinner
        if (isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = VlcOrange,
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 3.dp
                )
            }
        }

        // Double Tap Feedback indicator
        LaunchedEffect(doubleTapFeedback) {
            if (doubleTapFeedback != null) {
                delay(700)
                doubleTapFeedback = null
            }
        }
        
        // Aspect Ratio Feedback indicator
        LaunchedEffect(aspectRatioFeedback) {
            if (aspectRatioFeedback != null) {
                delay(2000)
                aspectRatioFeedback = null
            }
        }
        
        if (doubleTapFeedback != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 60.dp),
                contentAlignment = if (doubleTapFeedback == "+10s") Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = doubleTapFeedback ?: "",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
        
        if (aspectRatioFeedback != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 32.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "Ekran Oranı: ${aspectRatioFeedback}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Custom Overlay Controls (VLC Style)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!isLocked) {
                    // Top Shadow
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                                )
                            )
                    )

                    // Bottom Shadow
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                    )

                    // Top Header (VLC Style)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = { 
                                    val curPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                                    val dur = exoPlayer.duration.coerceAtLeast(0L)
                                    onClosePlayer(curPos, dur) 
                                },
                                modifier = Modifier.tvFocusHighlight()
                                    .size(36.dp).tvFocusHighlight()
                                    .testTag("player_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Geri",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                IconButton(
                                    onClick = {
                                        try {
                                            val vSize = exoPlayer.videoSize
                                            val rational = if (vSize.width > 0 && vSize.height > 0) {
                                                val w = vSize.width.coerceIn(1, 239)
                                                val h = vSize.height.coerceIn(1, 239)
                                                Rational(w, h)
                                            } else {
                                                Rational(16, 9)
                                            }
                                            val params = PictureInPictureParams.Builder()
                                                .setAspectRatio(rational)
                                                .build()
                                            activity?.enterPictureInPictureMode(params)
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier.size(36.dp).tvFocusHighlight()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureInPictureAlt,
                                        contentDescription = "Küçük Pencere (PiP)",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            if (onDownloadClick != null && !isDownloadedVideo) {
                                IconButton(
                                    onClick = {
                                        onDownloadClick()
                                        android.widget.Toast.makeText(context, "İndirme başlatıldı", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(36.dp).tvFocusHighlight()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Videoyu İndir",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            // Engine Badge
                            Surface(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = if (settings.defaultPlayerEngine.contains("LibVLC")) "ExoPlayer (AndroidX Media3)" else settings.defaultPlayerEngine,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            // Preload / Offline Badge
                            if (isDownloadedVideo) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Black.copy(alpha = 0.5f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, VlcBufferYellow.copy(alpha = 0.7f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DownloadDone,
                                            contentDescription = null,
                                            tint = VlcBufferYellow,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Çevrimdışı",
                                            color = Color.White.copy(alpha = 0.95f),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else if (effectiveFastCacheMode != 0) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Black.copy(alpha = 0.4f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, VlcOrange.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = null,
                                            tint = VlcOrange,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Hızlı Önbellek",
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom VLC Control Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        // 1. Time Row (VLC Style - Left: Current, Right: Total Duration)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLive || exoPlayer.isCurrentMediaItemLive || exoPlayer.isCurrentMediaItemDynamic) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Red))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "CANLI YAYIN",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                Text(
                                    text = formatTime(if (isSeeking) seekSliderPosition.toLong() else currentPositionMs),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Normal
                                )
                                Text(
                                    text = formatTime(totalDurationMs),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        if (!(isLive || exoPlayer.isCurrentMediaItemLive || exoPlayer.isCurrentMediaItemDynamic)) {
                            // 2. VLC Custom Pixel-Perfect Seekbar
                            val validTotalDuration = totalDurationMs.coerceAtLeast(1L).toFloat()
                            val currentProgress = if (isSeeking) seekSliderPosition else currentPositionMs.toFloat()
                            val progressRatio = if (totalDurationMs <= 0L) 0f else (currentProgress / validTotalDuration).coerceIn(0f, 1f)
                            val ramBufferRatio = if (totalDurationMs <= 0L) 0f else (bufferedPositionMs.toFloat() / validTotalDuration).coerceIn(0f, 1f)
                            val diskBufferRatio = if (totalDurationMs <= 0L) 0f else (diskBufferedPositionMs.toFloat() / validTotalDuration).coerceIn(0f, 1f)
                            // Real-time contiguous buffer ratio in fast cache modes (synced with actual minutes/seconds buffered)
                            val fastCacheContiguousRatio = maxOf(ramBufferRatio, contiguousDiskCacheRatio).coerceIn(0f, 1f)

                            val displaySpans = if (accumulatedCachedSpans.isNotEmpty()) accumulatedCachedSpans else cachedSpansRatios

                            // Helper function to clamp seek ratio to valid cached yellow spans in Mode 1
                            fun clampToValidCachedRatio(ratio: Float): Float {
                                if (effectiveFastCacheMode != 1) return ratio
                                val activeSpans = displaySpans
                                if (activeSpans.isEmpty()) {
                                    val fallbackRatio = contiguousDiskCacheRatio
                                    return ratio.coerceAtMost(fallbackRatio)
                                }
                                // Check if requested ratio lands directly inside any cached span
                                for (span in activeSpans) {
                                    if (ratio in span.startRatio..span.endRatio) {
                                        return ratio
                                    }
                                }
                                // If outside, find the latest cached span before this ratio
                                val precedingSpans = activeSpans.filter { it.startRatio <= ratio }
                                return if (precedingSpans.isNotEmpty()) {
                                    precedingSpans.maxOf { it.endRatio }.coerceAtMost(ratio)
                                } else {
                                    // Or the start of the first cached span / 0f
                                    activeSpans.minOfOrNull { it.startRatio }?.coerceAtMost(ratio) ?: 0f
                                }
                            }

                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(26.dp)
                                    .pointerInput(validTotalDuration, displaySpans) {
                                        detectTapGestures { offset ->
                                            var newRatio = (offset.x / size.width).coerceIn(0f, 1f)
                                            if (effectiveFastCacheMode == 1) {
                                                newRatio = clampToValidCachedRatio(newRatio)
                                            }
                                            val targetMs = (newRatio * validTotalDuration).toLong()
                                            if (!isDownloadedVideo && !isLiveStream && effectiveFastCacheMode != 1) cacheManager.notifySeekOccurred()
                                            exoPlayer.seekTo(targetMs)
                                            currentPositionMs = targetMs
                                            if (effectiveFastCacheMode != 0) {
                                                contiguousDiskCacheRatio = cacheManager.getContiguousCachedRatio(url)
                                                cachedSpansRatios = cacheManager.getCachedSpansRatios(url)
                                            }
                                            interactionTimestamp = System.currentTimeMillis()
                                        }
                                    }
                                    .pointerInput(validTotalDuration, displaySpans) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                isSeeking = true
                                                if (!isDownloadedVideo && !isLiveStream && effectiveFastCacheMode != 1) cacheManager.notifySeekOccurred()
                                                var newRatio = (offset.x / size.width).coerceIn(0f, 1f)
                                                if (effectiveFastCacheMode == 1) {
                                                    newRatio = clampToValidCachedRatio(newRatio)
                                                }
                                                seekSliderPosition = (newRatio * validTotalDuration)
                                                interactionTimestamp = System.currentTimeMillis()
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                var newRatio = (change.position.x / size.width).coerceIn(0f, 1f)
                                                if (effectiveFastCacheMode == 1) {
                                                    newRatio = clampToValidCachedRatio(newRatio)
                                                }
                                                seekSliderPosition = (newRatio * validTotalDuration)
                                                interactionTimestamp = System.currentTimeMillis()
                                            },
                                            onDragEnd = {
                                                if (!isDownloadedVideo && !isLiveStream && effectiveFastCacheMode != 1) cacheManager.notifySeekOccurred()
                                                exoPlayer.seekTo(seekSliderPosition.toLong())
                                                currentPositionMs = seekSliderPosition.toLong()
                                                isSeeking = false
                                                if (effectiveFastCacheMode != 0 && effectiveFastCacheMode != 3) {
                                                    contiguousDiskCacheRatio = cacheManager.getContiguousCachedRatio(url)
                                                    cachedSpansRatios = cacheManager.getCachedSpansRatios(url)
                                                }
                                                interactionTimestamp = System.currentTimeMillis()
                                            },
                                            onDragCancel = {
                                                isSeeking = false
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                val trackWidth = maxWidth

                                // 1. En alt katman: Şeffaf gri arka plan çizgisi
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(1.5.dp))
                                )

                                // 2. Disk Katmanı (SARI): Cihazın depolamasına inen tüm parçalar SARI
                                if (isDownloadedVideo) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .background(VlcBufferYellow, RoundedCornerShape(1.5.dp))
                                    )
                                } else if (effectiveFastCacheMode != 0 && effectiveFastCacheMode != 3) {
                                    if (displaySpans.isNotEmpty()) {
                                        for (span in displaySpans) {
                                            val startOffset = trackWidth * span.startRatio
                                            val spanWidth = trackWidth * (span.endRatio - span.startRatio)
                                            if (spanWidth > 0.dp) {
                                                Box(
                                                    modifier = Modifier
                                                        .offset(x = startOffset)
                                                        .width(spanWidth)
                                                        .height(3.dp)
                                                        .background(VlcBufferYellow, RoundedCornerShape(1.5.dp))
                                                )
                                            }
                                        }
                                    } else if (fastCacheContiguousRatio > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .width(trackWidth * fastCacheContiguousRatio)
                                                .height(3.dp)
                                                .background(VlcBufferYellow, RoundedCornerShape(1.5.dp))
                                        )
                                    }
                                } else {
                                    if (ramBufferRatio > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .width(trackWidth * ramBufferRatio)
                                                .height(3.dp)
                                                .background(VlcBufferOrange, RoundedCornerShape(1.5.dp))
                                        )
                                    }
                                }

                                // 3. RAM Katmanı (YEŞİL): ExoPlayer'ın anlık RAM tamponu (o an oynatılan konumdan ileriye doğru)
                                if (ramBufferRatio > progressRatio) {
                                    val activeStartOffset = trackWidth * progressRatio
                                    val activeBufferWidth = trackWidth * (ramBufferRatio - progressRatio)
                                    if (activeBufferWidth > 0.dp) {
                                        Box(
                                            modifier = Modifier
                                                .offset(x = activeStartOffset)
                                                .width(activeBufferWidth)
                                                .height(3.dp)
                                                .background(VlcBufferGreen, RoundedCornerShape(1.5.dp))
                                        )
                                    }
                                }

                                // 4. Oynatılan Katman (TURUNCU): İzlenen mevcut konum
                                Box(
                                    modifier = Modifier
                                        .width(trackWidth * progressRatio)
                                        .height(3.dp)
                                        .background(VlcOrange, RoundedCornerShape(1.5.dp))
                                )

                                // Thumb (Küçük turuncu daire)
                                Box(
                                    modifier = Modifier
                                        .offset(x = (trackWidth * progressRatio) - (if (isSeeking) 8.dp else 6.dp))
                                        .size(if (isSeeking) 16.dp else 12.dp)
                                        .background(VlcOrange, CircleShape)
                                        .border(1.5.dp, Color.White, CircleShape)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // 3. VLC Bottom Control Icons Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left Icons
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Subtitles / Audio Icon
                                IconButton(
                                    onClick = { showSubtitleDialog = true },
                                    modifier = Modifier.size(36.dp).tvFocusHighlight()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ClosedCaption,
                                        contentDescription = "Altyazı",
                                        tint = if (subtitleTracks.any { it.isSelected }) VlcOrange else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Aspect Ratio (Fit / Zoom / Fill)
                                IconButton(
                                    onClick = {
                                        currentAspectRatioIndex = (currentAspectRatioIndex + 1) % aspectRatios.size
                                        val newRatio = aspectRatios[currentAspectRatioIndex]
                                        aspectRatioFeedback = newRatio.first
                                    },
                                    modifier = Modifier.size(36.dp).tvFocusHighlight()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AspectRatio,
                                        contentDescription = "En Boy Oranı",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Lock
                                IconButton(
                                    onClick = {
                                        isLocked = true
                                        showControls = false
                                    },
                                    modifier = Modifier.size(36.dp).tvFocusHighlight()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = "Kilitle",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            // Center Play/Pause Button (Iconic Circle Outline)
                            IconButton(
                                onClick = {
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    interactionTimestamp = System.currentTimeMillis()
                                },
                                modifier = Modifier
                                    .size(46.dp).tvFocusHighlight()
                                    .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                                    .border(2.dp, Color.White, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Duraklat" else "Oynat",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Right Icons
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (availableQualities.size > 1) {
                                    Box {
                                        IconButton(
                                            onClick = { showQualityMenu = true },
                                            modifier = Modifier.size(36.dp).tvFocusHighlight()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Hd,
                                                contentDescription = "Kalite Ayarları",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = showQualityMenu,
                                            onDismissRequest = { showQualityMenu = false },
                                            modifier = Modifier
                                                .background(Color(0xEE1E1E1E), RoundedCornerShape(12.dp))
                                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                                .widthIn(min = 130.dp, max = 180.dp)
                                        ) {
                                            val sortedQualities = availableQualities.sortedBy { media ->
                                                val q = media.quality.lowercase()
                                                when {
                                                    q.contains("4k") -> 2160
                                                    q.contains("1440") -> 1440
                                                    q.contains("1080") -> 1080
                                                    q.contains("720") -> 720
                                                    q.contains("480") -> 480
                                                    q.contains("360") -> 360
                                                    q.contains("240") -> 240
                                                    else -> q.filter { it.isDigit() }.toIntOrNull() ?: 0
                                                }
                                            }
                                            sortedQualities.forEach { media ->
                                                val isCurrent = media.url == url
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = media.quality.ifBlank { media.format.ifBlank { "Alternatif" } },
                                                            color = if (isCurrent) VlcOrange else Color.White,
                                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                            fontSize = 14.sp
                                                        )
                                                    },
                                                    trailingIcon = if (isCurrent) {
                                                        {
                                                            Text(
                                                                "✓",
                                                                color = VlcOrange,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 14.sp
                                                            )
                                                        }
                                                    } else null,
                                                    onClick = {
                                                        showQualityMenu = false
                                                        if (!isCurrent) {
                                                            val position = exoPlayer.currentPosition
                                                            onQualitySelected(media.url, position)
                                                        }
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // More Options Menu (...)
                                IconButton(
                                    onClick = { showMoreMenu = true },
                                    modifier = Modifier.size(36.dp).tvFocusHighlight()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreHoriz,
                                        contentDescription = "Daha Fazla",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Locked Mode - Clean VLC Lock Button
                    IconButton(
                        onClick = {
                            isLocked = false
                            interactionTimestamp = System.currentTimeMillis()
                            showControls = true
                        },
                        modifier = Modifier.tvFocusHighlight()
                            .align(Alignment.CenterStart)
                            .padding(24.dp)
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .border(1.5.dp, VlcOrange, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Kilidi Aç",
                            tint = VlcOrange,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Volume / Brightness Floating Indicators
        AnimatedVisibility(
            visible = showVolumeIndicator || showBrightnessIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(14.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (dragType == 1) {
                            if (volumeIndicatorValue == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp
                        } else {
                            Icons.Default.Brightness6
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (dragType == 1) "$volumeIndicatorValue%" else "$brightnessIndicatorValue%",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // VLC More Options BottomSheet / Dialog
        if (showMoreMenu) {
            AlertDialog(
                onDismissRequest = { showMoreMenu = false },
                title = { Text("Seçenekler", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text("Oynatma Hızı") },
                            supportingContent = { Text("${exoPlayer.playbackParameters.speed}x") },
                            leadingContent = { Icon(Icons.Default.Speed, contentDescription = null, tint = VlcOrange) },
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures {
                                    showMoreMenu = false
                                    showSpeedDialog = true
                                }
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Ses Parçaları") },
                            supportingContent = { Text(if (audioTracks.isNotEmpty()) "${audioTracks.size} parça mevcut" else "Varsayılan") },
                            leadingContent = { Icon(Icons.Default.Audiotrack, contentDescription = null, tint = VlcOrange) },
                            modifier = Modifier.clickable {
                                showMoreMenu = false
                                showAudioDialog = true
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Altyazı Seçimi") },
                            supportingContent = { Text(if (subtitleTracks.isNotEmpty()) "${subtitleTracks.size} altyazı mevcut" else "Kapalı") },
                            leadingContent = { Icon(Icons.Default.Subtitles, contentDescription = null, tint = VlcOrange) },
                            modifier = Modifier.clickable {
                                showMoreMenu = false
                                showSubtitleDialog = true
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showMoreMenu = false }) { Text("Kapat") }
                }
            )
        }

        // Audio Tracks Selection Dialog
        if (showAudioDialog) {
            AlertDialog(
                onDismissRequest = { showAudioDialog = false },
                title = { Text("Ses Parçaları") },
                text = {
                    Column {
                        if (audioTracks.isEmpty()) {
                            Text("Ses parçası bulunamadı.")
                        } else {
                            audioTracks.forEach { track ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = track.isSelected,
                                        onClick = {
                                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                .buildUpon()
                                                .setOverrideForType(
                                                    TrackSelectionOverride(track.trackGroup, track.trackIndex)
                                                )
                                                .build()
                                            showAudioDialog = false
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = VlcOrange)
                                    )
                                    Text(text = track.name)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAudioDialog = false }) { Text("Kapat") }
                }
            )
        }

        // Subtitle Dialog
        if (showSubtitleDialog) {
            AlertDialog(
                onDismissRequest = { showSubtitleDialog = false },
                title = { Text("Altyazılar") },
                text = {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = subtitleTracks.none { it.isSelected },
                                onClick = {
                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                        .buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                        .build()
                                    showSubtitleDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = VlcOrange)
                            )
                            Text("Kapalı")
                        }

                        subtitleTracks.forEach { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = track.isSelected,
                                    onClick = {
                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                            .setOverrideForType(
                                                TrackSelectionOverride(track.trackGroup, track.trackIndex)
                                            )
                                            .build()
                                        showSubtitleDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = VlcOrange)
                                )
                                Text(text = track.name)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSubtitleDialog = false }) { Text("Kapat") }
                }
            )
        }

        // Playback Speed Dialog
        if (showSpeedDialog) {
            AlertDialog(
                onDismissRequest = { showSpeedDialog = false },
                title = { Text("Oynatma Hızı") },
                text = {
                    Column {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            val currentSpeed = exoPlayer.playbackParameters.speed
                            TextButton(
                                onClick = {
                                    exoPlayer.playbackParameters = PlaybackParameters(speed)
                                    showSpeedDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "${speed}x",
                                    color = if (currentSpeed == speed) VlcOrange else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (currentSpeed == speed) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSpeedDialog = false }) { Text("İptal") }
                }
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
