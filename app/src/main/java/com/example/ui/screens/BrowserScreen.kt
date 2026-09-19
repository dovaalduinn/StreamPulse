package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.BookmarkEntity
import com.example.ui.viewmodel.MediaPlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun Modifier.tvFocusHighlight(
    shape: Shape = RoundedCornerShape(8.dp),
    focusBorderColor: Color = MaterialTheme.colorScheme.primary
): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { isFocused = it.isFocused }
        .border(
            width = if (isFocused) 2.5.dp else 0.dp,
            color = if (isFocused) focusBorderColor else Color.Transparent,
            shape = shape
        )
        .focusable()
}

private fun dispatchVirtualClick(webView: WebView?, x: Float, y: Float) {
    if (webView == null) return
    try {
        val now = SystemClock.uptimeMillis()
        val downEvent = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        webView.dispatchTouchEvent(downEvent)
        downEvent.recycle()

        val upEvent = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0)
        webView.dispatchTouchEvent(upEvent)
        upEvent.recycle()
    } catch (_: Exception) {}
}

class WebAppInterface(
    private val expectedToken: String,
    private val onMediaFound: (String, String, String, String) -> Unit,
    private val onSubtitleFoundCallback: ((String, String, String) -> Unit)? = null,
    private val popupBlockerEnabled: Boolean = true,
    private val onRawMediaEventJsonCallback: ((String) -> Unit)? = null,
    private val onRawMediaEventCallback: ((String, String, String, String, String, String, String) -> Unit)? = null
) {
    private var lastCallTimestamp = 0L
    private var callCountInSecond = 0

    // Güvenlik İyileştirmesi: Yalnızca uygulamanın enjekte ettiği SNIFFER_JS betiğinden gelen
    // geçerli nonce/token'a sahip çağrılar kabul edilir, sayfadaki yabancı JS çağrıları engellenir.
    private fun validateToken(token: String?): Boolean {
        return !token.isNullOrEmpty() && token == expectedToken
    }

    private fun checkRateLimit(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastCallTimestamp > 1000) {
            lastCallTimestamp = now
            callCountInSecond = 1
            return true
        }
        callCountInSecond++
        return callCountInSecond <= 50
    }

    @JavascriptInterface
    fun onRawMediaEventJson(jsonPayload: String, token: String? = null) {
        if (!validateToken(token)) return
        if (!checkRateLimit()) return
        if (jsonPayload.length > 10 * 1024) return // Max 10KB
        onRawMediaEventJsonCallback?.invoke(jsonPayload)
    }

    @JavascriptInterface
    fun onRawMediaEvent(sourceType: String, url: String, type: String, title: String, duration: String, quality: String, mimeType: String, token: String? = null) {
        if (!validateToken(token)) return
        if (!checkRateLimit()) return
        if (url.length > 2048 || title.length > 1024) return
        onRawMediaEventCallback?.invoke(sourceType, url, type, title, duration, quality, mimeType)
    }

    @JavascriptInterface
    fun isPopupBlockerEnabled(token: String? = null): Boolean {
        if (!validateToken(token)) return true
        return popupBlockerEnabled
    }

    @JavascriptInterface
    fun onMediaUrlFound(url: String, title: String, token: String? = null) {
        if (!validateToken(token)) return
        onMediaFound(url, title, "", "")
    }

    @JavascriptInterface
    fun onMediaUrlFoundWithDuration(url: String, title: String, duration: String, token: String? = null) {
        if (!validateToken(token)) return
        onMediaFound(url, title, duration, "")
    }

    @JavascriptInterface
    fun onMediaFoundAdvanced(url: String, title: String, duration: String, quality: String, token: String? = null) {
        if (!validateToken(token)) return
        onMediaFound(url, title, duration, quality)
    }

    @JavascriptInterface
    fun onSubtitleFound(url: String, language: String, label: String, token: String? = null) {
        if (!validateToken(token)) return
        onSubtitleFoundCallback?.invoke(url, language, label)
    }
}

enum class SearchEngine(
    val id: String,
    val title: String,
    val searchUrl: String,
    val homeUrl: String,
    val initial: String,
    val color: Color
) {
    GOOGLE("Google", "Google", "https://www.google.com/search?q=", "https://www.google.com", "G", Color(0xFF4285F4)),
    YANDEX("Yandex", "Yandex", "https://yandex.com.tr/search/?text=", "https://yandex.com.tr", "Y", Color(0xFFFC3F1D)),
    DUCKDUCKGO("DuckDuckGo", "DuckDuckGo", "https://duckduckgo.com/?q=", "https://duckduckgo.com", "D", Color(0xFFDE5833)),
    BING("Bing", "Microsoft Bing", "https://www.bing.com/search?q=", "https://www.bing.com", "B", Color(0xFF008373)),
    BRAVE("Brave", "Brave Search", "https://search.brave.com/search?q=", "https://search.brave.com", "B", Color(0xFFFB542B));

    companion object {
        fun fromId(id: String): SearchEngine {
            return entries.find { it.id.equals(id, ignoreCase = true) || it.title.equals(id, ignoreCase = true) } ?: GOOGLE
        }
    }
}

class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    url: String = "about:blank",
    title: String = "Yeni Sekme",
    val isIncognito: Boolean = false,
    var webView: WebView? = null
) {
    var url: String by mutableStateOf(url)
    var title: String by mutableStateOf(title)
    var isDesktopMode: Boolean by mutableStateOf(false)
}

data class ShortcutItem(
    val name: String,
    val url: String,
    val initial: String,
    val badgeColor: Color
)

@SuppressLint("SetJavaScriptEnabled")
enum class StreamFilter(val label: String) {
    ALL("Tümü"),
    VOD("Süresi Belli"),
    LIVE("Canlı Yayın"),
    MP4("Sadece MP4"),
    HLS("Sadece HLS")
}

private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

private fun getMobileUserAgent(context: android.content.Context): String {
    return try {
        WebSettings.getDefaultUserAgent(context)
            .replace("; wv", "")
            .replace(Regex("Version/[0-9.]+\\s*"), "")
    } catch (_: Exception) {
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}

private fun applyDesktopMode(webView: WebView, isDesktop: Boolean, context: android.content.Context) {
    webView.settings.apply {
        userAgentString = if (isDesktop) DESKTOP_USER_AGENT else getMobileUserAgent(context)
        useWideViewPort = true
        loadWithOverviewMode = true
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
    }
    if (isDesktop) {
        val desktopJs = """
            (function() {
                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'viewport';
                    document.head.appendChild(meta);
                }
                meta.setAttribute('content', 'width=1280, initial-scale=0.35, minimum-scale=0.25, maximum-scale=5.0, user-scalable=yes');
            })();
        """.trimIndent()
        webView.evaluateJavascript(desktopJs, null)
    } else {
        val mobileJs = """
            (function() {
                var meta = document.querySelector('meta[name="viewport"]');
                if (meta) {
                    meta.setAttribute('content', 'width=device-width, initial-scale=1.0');
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(mobileJs, null)
    }
}

/**
 * Pauses and mutes all HTML5 video/audio elements and iframes inside the WebView
 * so that background audio does not continue playing when the native player is opened.
 */
fun pauseAllMediaInWebView(webView: WebView?) {
    if (webView == null) return
    try {
        val pauseJs = """
            (function() {
                try {
                    var mediaElements = document.querySelectorAll('video, audio');
                    for (var i = 0; i < mediaElements.length; i++) {
                        var el = mediaElements[i];
                        el.pause();
                        el.muted = true;
                    }
                    var iframes = document.querySelectorAll('iframe');
                    for (var j = 0; j < iframes.length; j++) {
                        try {
                            iframes[j].contentWindow.postMessage('{"event":"command","func":"pauseVideo","args":""}', '*');
                            iframes[j].contentWindow.postMessage('{"event":"command","func":"mute","args":""}', '*');
                        } catch(e) {}
                    }
                } catch(e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(pauseJs, null)
        webView.onPause()
    } catch (_: Exception) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: MediaPlayerViewModel,
    onBackToHome: () -> Unit,
    startUrl: String? = null,
    onStartUrlConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    val appSettings by viewModel.settings.collectAsState()
    var selectedEngine by remember(appSettings.defaultSearchEngine) {
        mutableStateOf(SearchEngine.fromId(appSettings.defaultSearchEngine))
    }
    var showEngineMenu by remember { mutableStateOf(false) }

    var tabs by remember { mutableStateOf(listOf(BrowserTab(isIncognito = false, url = "about:blank"))) }
    var currentTabId by remember { mutableStateOf(tabs.first().id) }
    val currentTab = tabs.find { it.id == currentTabId } ?: tabs.first()
    var activeTabMode by remember { mutableIntStateOf(0) } // 0: Normal, 1: Incognito

    var urlInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var webProgress by remember { mutableFloatStateOf(0f) }
    var showSniffedBottomSheet by remember { mutableStateOf(false) }
    var showTabsSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showSslInfoDialog by remember { mutableStateOf(false) }
    var showTvControlsSheet by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    val historySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bookmarksSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sniffedSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tvControlsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tabsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Find in Page state
    var isFindInPageVisible by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findCurrentMatch by remember { mutableIntStateOf(0) }
    var findTotalMatches by remember { mutableIntStateOf(0) }

    val bookmarks by viewModel.bookmarks.collectAsState()

    // TV Box Controls State
    var isTvCursorMode by remember(appSettings.tvAutoEnableMouse) { mutableStateOf(appSettings.tvAutoEnableMouse) }
    var cursorX by remember { mutableFloatStateOf(400f) }
    var cursorY by remember { mutableFloatStateOf(300f) }
    var isClicking by remember { mutableStateOf(false) }
    var cursorSpeedPx by remember(appSettings.tvCursorSpeed) { mutableFloatStateOf(appSettings.tvCursorSpeed) }
    var cursorToggleToastMessage by remember { mutableStateOf<String?>(null) }
    var textZoomPercent by remember { mutableIntStateOf(115) }
    var isDesktopUserAgent by remember { mutableStateOf(false) }
    var webViewWidthPx by remember { mutableFloatStateOf(1080f) }
    var webViewHeightPx by remember { mutableFloatStateOf(1920f) }

    val coroutineScope = rememberCoroutineScope()
    val sniffedLinks by viewModel.sniffedLinks.collectAsState()
    var selectedFilter by remember { mutableStateOf(StreamFilter.ALL) }

    val validSniffedLinks = remember(sniffedLinks, selectedFilter) {
        sniffedLinks.filter { link ->
            when (selectedFilter) {
                StreamFilter.ALL -> true
                StreamFilter.VOD -> link.duration.isNotBlank() && link.duration != "Canlı Yayın"
                StreamFilter.LIVE -> link.duration == "Canlı Yayın"
                StreamFilter.MP4 -> link.format.contains("MP4", ignoreCase = true) || link.url.contains(".mp4", ignoreCase = true)
                StreamFilter.HLS -> link.format.contains("HLS", ignoreCase = true) || link.url.contains(".m3u8", ignoreCase = true)
            }
        }
    }
    val browserHistory by viewModel.browserHistory.collectAsState()
    val activeVideo by viewModel.activeVideo.collectAsState()

    // When the native player is opened or closed, pause/resume web media automatically
    LaunchedEffect(activeVideo) {
        if (activeVideo != null) {
            tabs.forEach { tab ->
                pauseAllMediaInWebView(tab.webView)
            }
        } else {
            // Resume only the currently active visible tab's webView
            currentTab.webView?.onResume()
        }
    }

    val quickBookmarks = listOf(
        "Google" to "https://www.google.com",
        "Örnek Video Sunucu" to "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/",
        "HLS M3U8 Test" to "https://demo.unified-streaming.com/",
        "HTML5 Video Test" to "https://html5demos.com/video/"
    )

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    LaunchedEffect(isClicking) {
        if (isClicking) {
            delay(150)
            isClicking = false
        }
    }

    LaunchedEffect(cursorToggleToastMessage) {
        if (cursorToggleToastMessage != null) {
            delay(2500)
            cursorToggleToastMessage = null
        }
    }

    LaunchedEffect(currentTabId) {
        urlInput = currentTab.url
        canGoBack = currentTab.webView?.canGoBack() == true
        canGoForward = currentTab.webView?.canGoForward() == true
    }

    LaunchedEffect(startUrl) {
        if (startUrl != null) {
            val newTab = BrowserTab(url = startUrl)
            tabs = tabs + newTab
            currentTabId = newTab.id
            urlInput = startUrl
            onStartUrlConsumed()
        }
    }

    BackHandler {
        if (currentTab.webView?.canGoBack() == true) {
            currentTab.webView?.goBack()
        } else {
            onBackToHome()
        }
    }

    fun processSearch(query: String): String {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return selectedEngine.homeUrl
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("about:") || trimmed.startsWith("file://")) {
            return trimmed
        }
        val isDomain = !trimmed.contains(" ") && (trimmed.contains(".") || trimmed.contains(":"))
        return if (isDomain) {
            "https://$trimmed"
        } else {
            selectedEngine.searchUrl + URLEncoder.encode(trimmed, "UTF-8")
        }
    }

    Scaffold(
        topBar = {
            val isIncognito = currentTab.isIncognito
            val topBarBg = if (isIncognito) Color(0xFF1F1F1F) else MaterialTheme.colorScheme.surface
            val omniboxBg = if (isIncognito) Color(0xFF2D2E30) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            val omniboxTextColor = if (isIncognito) Color.White else MaterialTheme.colorScheme.onSurface
            val omniboxHintColor = if (isIncognito) Color(0xFF9AA0A6) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

            Surface(
                color = topBarBg,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Google Chrome Modern Omnibox Capsule
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = omniboxBg,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left Icon / Indicator
                                if (isIncognito) {
                                    Icon(
                                        imageVector = Icons.Default.VisibilityOff,
                                        contentDescription = "Gizli Sekme",
                                        modifier = Modifier
                                            .size(20.dp)
                                            .padding(end = 2.dp),
                                        tint = Color(0xFF9AA0A6)
                                    )
                                } else if (urlInput.startsWith("https://")) {
                                    IconButton(
                                        onClick = { showSslInfoDialog = true },
                                        modifier = Modifier.size(28.dp).tvFocusHighlight(CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Bağlantı Güvenli (SSL)",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color(0xFF1E8E3E)
                                        )
                                    }
                                } else {
                                    // Search Engine circular badge
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clickable { showEngineMenu = true }
                                            .background(selectedEngine.color, CircleShape)
                                            .tvFocusHighlight(CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = selectedEngine.initial,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                BasicTextField(
                                    value = urlInput,
                                    onValueChange = { urlInput = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = omniboxTextColor,
                                        fontSize = 13.5.sp
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Uri,
                                        imeAction = ImeAction.Go
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onGo = {
                                            val target = processSearch(urlInput)
                                            urlInput = target
                                            currentTab.url = target
                                            currentTab.webView?.loadUrl(target)
                                        }
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .tvFocusHighlight(RoundedCornerShape(6.dp))
                                        .testTag("browser_url_input"),
                                    decorationBox = { innerTextField ->
                                        if (urlInput.isEmpty()) {
                                            Text(
                                                text = if (isIncognito) "Gizli modda ara veya URL yazın" else "${selectedEngine.title}'da arayın veya adres yazın",
                                                color = omniboxHintColor,
                                                fontSize = 12.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        innerTextField()
                                    }
                                )

                                // Right Omnibox Actions
                                if (urlInput.isNotEmpty() && urlInput != currentTab.url) {
                                    IconButton(
                                        onClick = { urlInput = "" },
                                        modifier = Modifier.size(26.dp).tvFocusHighlight(CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Temizle",
                                            modifier = Modifier.size(16.dp),
                                            tint = omniboxHintColor
                                        )
                                    }
                                }

                                if (isLoading) {
                                    IconButton(
                                        onClick = {
                                            currentTab.webView?.stopLoading()
                                            isLoading = false
                                        },
                                        modifier = Modifier.size(26.dp).tvFocusHighlight(CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Durdur",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                } else if (urlInput.isNotBlank() && urlInput != currentTab.url) {
                                    IconButton(
                                        onClick = {
                                            val target = processSearch(urlInput)
                                            urlInput = target
                                            currentTab.url = target
                                            currentTab.webView?.loadUrl(target)
                                        },
                                        modifier = Modifier.size(26.dp).tvFocusHighlight(CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Git",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color(0xFF1A73E8)
                                        )
                                    }
                                } else if (currentTab.url != "about:blank" && currentTab.url.isNotEmpty()) {
                                    IconButton(
                                        onClick = { currentTab.webView?.reload() },
                                        modifier = Modifier.size(26.dp).tvFocusHighlight(CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Yenile",
                                            modifier = Modifier.size(16.dp),
                                            tint = omniboxHintColor
                                        )
                                    }
                                }
                            }
                        }



                        // TV Remote & Mouse Controls Shortcut
                        IconButton(
                            onClick = { showTvControlsSheet = true },
                            modifier = Modifier.size(38.dp).tvFocusHighlight(CircleShape)
                        ) {
                            BadgedBox(
                                badge = {
                                    if (isTvCursorMode) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                            Text("TV", fontSize = 8.sp)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isTvCursorMode) Icons.Default.Mouse else Icons.Default.Tv,
                                    contentDescription = "TV Araçları & Sanal Fare",
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isTvCursorMode) MaterialTheme.colorScheme.primary else if (isIncognito) Color(0xFF9AA0A6) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Shield / Adblock status badge
                        IconButton(
                            onClick = {
                                val nextState = !appSettings.adblockEnabled
                                viewModel.updateSettings(appSettings.copy(adblockEnabled = nextState, popupBlockerEnabled = nextState))
                            },
                            modifier = Modifier.size(38.dp).tvFocusHighlight(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Kalkan Durumu",
                                modifier = Modifier.size(18.dp),
                                tint = if (appSettings.adblockEnabled && appSettings.popupBlockerEnabled) Color(0xFF1E8E3E) else MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    if (isLoading && webProgress < 1f) {
                        LinearProgressIndicator(
                            progress = { webProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp),
                            color = Color(0xFF1A73E8)
                        )
                    }
                }
            }
        },
        bottomBar = {
            Column {
                // Sleek, minimal floating notification when video streams are detected
                AnimatedVisibility(
                    visible = sniffedLinks.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showHistorySheet = false
                                    showBookmarksSheet = false
                                    showTvControlsSheet = false
                                    showTabsSheet = false
                                    showSniffedBottomSheet = true
                                    coroutineScope.launch {
                                        sniffedSheetState.show()
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Badge(containerColor = MaterialTheme.colorScheme.error) {
                                    Text("${sniffedLinks.size}", fontSize = 10.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Video Akışı Bulundu (TV'de Oynat)",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    showHistorySheet = false
                                    showBookmarksSheet = false
                                    showTvControlsSheet = false
                                    showTabsSheet = false
                                    showSniffedBottomSheet = true
                                    coroutineScope.launch {
                                        sniffedSheetState.show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp).tvFocusHighlight(RoundedCornerShape(14.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Oynat", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Chrome-Style Compact Bottom Toolbar (Height: 48dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    color = if (currentTab.isIncognito) Color(0xFF1F1F1F) else MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    val bottomIconColor = if (currentTab.isIncognito) Color(0xFFE8EAED) else MaterialTheme.colorScheme.onSurfaceVariant
                    Column {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = if (currentTab.isIncognito) Color(0xFF3C4043) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Home (Returns to Google Chrome New Tab Page)
                            IconButton(
                                onClick = {
                                    urlInput = ""
                                    currentTab.url = "about:blank"
                                    currentTab.webView?.loadUrl("about:blank")
                                },
                                modifier = Modifier.size(40.dp).tvFocusHighlight(CircleShape)
                            ) {
                                Icon(
                                    Icons.Outlined.Home,
                                    contentDescription = "Ana Sayfa",
                                    modifier = Modifier.size(20.dp),
                                    tint = bottomIconColor
                                )
                            }

                            // History Icon next to Home
                            IconButton(
                                onClick = {
                                    currentTab.url = "chrome://history"
                                },
                                modifier = Modifier.size(40.dp).tvFocusHighlight(CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "Geçmiş",
                                    modifier = Modifier.size(20.dp),
                                    tint = bottomIconColor
                                )
                            }

                            // 2. Back
                            IconButton(
                                onClick = { if (currentTab.webView?.canGoBack() == true) currentTab.webView?.goBack() },
                                enabled = canGoBack,
                                modifier = Modifier.size(40.dp).tvFocusHighlight(CircleShape)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBackIos,
                                    contentDescription = "Geri",
                                    modifier = Modifier
                                        .size(17.dp)
                                        .alpha(if (canGoBack) 1f else 0.35f),
                                    tint = bottomIconColor
                                )
                            }

                            // 3. Forward
                            IconButton(
                                onClick = { if (currentTab.webView?.canGoForward() == true) currentTab.webView?.goForward() },
                                enabled = canGoForward,
                                modifier = Modifier.size(40.dp).tvFocusHighlight(CircleShape)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = "İleri",
                                    modifier = Modifier
                                        .size(17.dp)
                                        .alpha(if (canGoForward) 1f else 0.35f),
                                    tint = bottomIconColor
                                )
                            }

                            // 4. Sniffer / Video Play button
                            IconButton(
                                onClick = {
                                    if (sniffedLinks.isNotEmpty()) {
                                        showHistorySheet = false
                                        showBookmarksSheet = false
                                        showTvControlsSheet = false
                                        showTabsSheet = false
                                        showSniffedBottomSheet = true
                                        coroutineScope.launch {
                                            sniffedSheetState.show()
                                        }
                                    } else {
                                        currentTab.webView?.reload()
                                    }
                                },
                                modifier = Modifier.size(40.dp).tvFocusHighlight(CircleShape)
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (sniffedLinks.isNotEmpty()) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.offset(x = 4.dp, y = (-2).dp)
                                            ) {
                                                Text("${sniffedLinks.size}", fontSize = 9.sp)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (sniffedLinks.isNotEmpty()) Icons.Filled.PlayCircle else Icons.Outlined.PlayCircleOutline,
                                        contentDescription = "Videolar",
                                        tint = if (sniffedLinks.isNotEmpty()) MaterialTheme.colorScheme.primary else bottomIconColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // 5. Chrome Tab Switcher ([ N ])
                            IconButton(
                                onClick = { showTabsSheet = true },
                                modifier = Modifier.size(40.dp).tvFocusHighlight(CircleShape)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .border(
                                            width = 1.6.dp,
                                            color = bottomIconColor,
                                            shape = RoundedCornerShape(5.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${tabs.size}",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = bottomIconColor
                                    )
                                }
                            }

                            // 6. Chrome 3-Dot More Menu
                            Box {
                                IconButton(
                                    onClick = { showMoreMenu = true },
                                    modifier = Modifier.size(40.dp).tvFocusHighlight(CircleShape)
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "Menü",
                                        modifier = Modifier.size(20.dp),
                                        tint = bottomIconColor
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Yeni sekme") },
                                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                        onClick = {
                                            val newTab = BrowserTab(isIncognito = false)
                                            tabs = tabs + newTab
                                            currentTabId = newTab.id
                                            urlInput = ""
                                            showMoreMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Yeni gizli sekme") },
                                        leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                                        onClick = {
                                            val newTab = BrowserTab(isIncognito = true)
                                            tabs = tabs + newTab
                                            currentTabId = newTab.id
                                            urlInput = ""
                                            showMoreMenu = false
                                        }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Yer İşaretleri") },
                                        leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color(0xFFFBBC04)) },
                                        onClick = {
                                            showMoreMenu = false
                                            showSniffedBottomSheet = false
                                            showHistorySheet = false
                                            showTvControlsSheet = false
                                            showTabsSheet = false
                                            showBookmarksSheet = true
                                            coroutineScope.launch {
                                                bookmarksSheetState.show()
                                            }
                                        }
                                    )
                                    if (currentTab.url.isNotEmpty() && currentTab.url != "about:blank") {
                                        DropdownMenuItem(
                                            text = { Text("Bu sayfayı yer işaretlerine ekle") },
                                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                            onClick = {
                                                viewModel.addBookmark(
                                                    title = currentTab.title.ifBlank { currentTab.url },
                                                    url = currentTab.url,
                                                    imageUrl = ""
                                                )
                                                showMoreMenu = false
                                            }
                                        )
                                    }

                                    DropdownMenuItem(
                                        text = { Text("Sayfada bul") },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                        onClick = {
                                            isFindInPageVisible = true
                                            showMoreMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Masaüstü sitesi")
                                                Checkbox(
                                                    checked = currentTab.isDesktopMode,
                                                    onCheckedChange = null
                                                )
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Default.Computer, contentDescription = null) },
                                        onClick = {
                                            val newMode = !currentTab.isDesktopMode
                                            currentTab.isDesktopMode = newMode
                                            isDesktopUserAgent = newMode
                                            currentTab.webView?.let { wv ->
                                                applyDesktopMode(wv, newMode, context)
                                                wv.reload()
                                            }
                                            showMoreMenu = false
                                        }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Arama motoru: ${selectedEngine.title}") },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .background(selectedEngine.color, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(selectedEngine.initial, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        onClick = {
                                            showEngineMenu = true
                                            showMoreMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Pop-up Engelle: " + if (appSettings.popupBlockerEnabled) "Açık" else "Kapalı") },
                                        leadingIcon = { Icon(if (appSettings.popupBlockerEnabled) Icons.Default.Block else Icons.Default.CheckCircleOutline, contentDescription = null) },
                                        onClick = {
                                            viewModel.updateSettings(appSettings.copy(popupBlockerEnabled = !appSettings.popupBlockerEnabled))
                                            showMoreMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("DNS Reklam Engelleyici: " + if (appSettings.adblockEnabled) "Açık" else "Kapalı") },
                                        leadingIcon = { Icon(if (appSettings.adblockEnabled) Icons.Default.Shield else Icons.Default.ShieldMoon, contentDescription = null) },
                                        onClick = {
                                            viewModel.updateSettings(appSettings.copy(adblockEnabled = !appSettings.adblockEnabled))
                                            showMoreMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("TV Kontrol & Sanal Fare") },
                                        leadingIcon = { Icon(Icons.Default.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = {
                                            showTvControlsSheet = true
                                            showMoreMenu = false
                                        }
                                    )
                                    if (sniffedLinks.isNotEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("Videoları Listele (${sniffedLinks.size})") },
                                            leadingIcon = { Icon(Icons.Default.PlayCircle, contentDescription = null) },
                                            onClick = {
                                                showSniffedBottomSheet = true
                                                showMoreMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Yakalananları Temizle") },
                                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                                            onClick = {
                                                viewModel.clearSniffedLinks()
                                                showMoreMenu = false
                                            }
                                        )
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Tarayıcıyı Kapat", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showMoreMenu = false
                                            onBackToHome()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN) {
                    val keyCode = keyEvent.nativeKeyEvent.keyCode
                    val shortcutKeyCode = appSettings.tvMouseShortcutKeyCode

                    // Dynamic Assigned Remote Shortcut Key to toggle Virtual Mouse
                    if (keyCode == shortcutKeyCode) {
                        isTvCursorMode = !isTvCursorMode
                        cursorToggleToastMessage = if (isTvCursorMode) "🎯 Sanal Fare: AÇIK (OK ile Tıkla)" else "❌ Sanal Fare: KAPALI"
                        return@onPreviewKeyEvent true
                    }

                    when (keyCode) {
                        AndroidKeyEvent.KEYCODE_PROG_RED -> {
                            isTvCursorMode = !isTvCursorMode
                            cursorToggleToastMessage = if (isTvCursorMode) "🎯 Sanal Fare: AÇIK (OK ile Tıkla)" else "❌ Sanal Fare: KAPALI"
                            true
                        }
                        AndroidKeyEvent.KEYCODE_INFO -> {
                            showTvControlsSheet = !showTvControlsSheet
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            if (isTvCursorMode) {
                                cursorY = (cursorY - cursorSpeedPx).coerceAtLeast(10f)
                                if (cursorY < 120f) {
                                    currentTab.webView?.scrollBy(0, -260)
                                }
                                true
                            } else {
                                currentTab.webView?.scrollBy(0, -260)
                                true
                            }
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (isTvCursorMode) {
                                cursorY = (cursorY + cursorSpeedPx).coerceAtMost(webViewHeightPx - 10f)
                                if (cursorY > webViewHeightPx - 140f) {
                                    currentTab.webView?.scrollBy(0, 260)
                                }
                                true
                            } else {
                                currentTab.webView?.scrollBy(0, 260)
                                true
                            }
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (isTvCursorMode) {
                                cursorX = (cursorX - cursorSpeedPx).coerceAtLeast(10f)
                                true
                            } else {
                                currentTab.webView?.scrollBy(-160, 0)
                                true
                            }
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (isTvCursorMode) {
                                cursorX = (cursorX + cursorSpeedPx).coerceAtMost(webViewWidthPx - 10f)
                                true
                            } else {
                                currentTab.webView?.scrollBy(160, 0)
                                true
                            }
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                            if (isTvCursorMode) {
                                dispatchVirtualClick(currentTab.webView, cursorX, cursorY)
                                isClicking = true
                                true
                            } else {
                                false
                            }
                        }
                        AndroidKeyEvent.KEYCODE_PAGE_UP -> {
                            currentTab.webView?.pageUp(false)
                            true
                        }
                        AndroidKeyEvent.KEYCODE_PAGE_DOWN -> {
                            currentTab.webView?.pageDown(false)
                            true
                        }
                        AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
                        AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (sniffedLinks.isNotEmpty()) {
                                showSniffedBottomSheet = true
                                true
                            } else false
                        }
                        AndroidKeyEvent.KEYCODE_MENU -> {
                            showTvControlsSheet = !showTvControlsSheet
                            true
                        }
                        else -> false
                    }
                } else false
            },
        floatingActionButton = {
            if (sniffedLinks.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showSniffedBottomSheet = true },
                    icon = { Icon(Icons.Default.PlayCircle, contentDescription = null) },
                    text = { Text("Videoyu Oynat (${sniffedLinks.size})") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.tvFocusHighlight(RoundedCornerShape(16.dp))
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Sayfada Bul (Chrome Find In Page Floating Bar)
            AnimatedVisibility(visible = isFindInPageVisible) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = findQuery,
                            onValueChange = { q ->
                                findQuery = q
                                currentTab.webView?.findAllAsync(q)
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (findQuery.isEmpty()) {
                                    Text("Sayfada bul...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                }
                                inner()
                            }
                        )
                        if (findQuery.isNotEmpty()) {
                            Text(
                                text = if (findTotalMatches > 0) "$findCurrentMatch / $findTotalMatches" else "0 / 0",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                        }
                        IconButton(
                            onClick = { currentTab.webView?.findNext(false) },
                            enabled = findTotalMatches > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Önceki", modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { currentTab.webView?.findNext(true) },
                            enabled = findTotalMatches > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Sonraki", modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = {
                                currentTab.webView?.clearMatches()
                                isFindInPageVisible = false
                                findQuery = ""
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Ultra-compact Quick Bookmark Bar (Visible when browsing active page)
            if (currentTab.url.isNotEmpty() && currentTab.url != "about:blank") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickBookmarks.forEach { (name, link) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .tvFocusHighlight(RoundedCornerShape(10.dp))
                                .clickable {
                                    urlInput = link
                                    currentTab.url = link
                                    currentTab.webView?.loadUrl(link)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Bookmark,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = name,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Embedded Browser WebViews & TV Virtual Mouse Overlay Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onSizeChanged { size ->
                        webViewWidthPx = size.width.toFloat()
                        webViewHeightPx = size.height.toFloat()
                    }
            ) {
                tabs.forEach { tab ->
                    key(tab.id) {
                        val isCurrent = tab.id == currentTabId
                        AndroidView(
                            factory = { ctx ->
                                try {
                                    val webCacheDir = java.io.File(ctx.cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
                                    if (!webCacheDir.exists()) webCacheDir.mkdirs()
                                    val wasmCacheDir = java.io.File(ctx.cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
                                    if (!wasmCacheDir.exists()) wasmCacheDir.mkdirs()
                                } catch (_: Exception) {}

                                WebView(ctx).apply {
                                    tab.webView = this
                                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                                        WebViewCompat.addDocumentStartJavaScript(this, com.example.sniffer.BrowserSnifferScriptV2.SNIFFER_JS, setOf("*"))
                                    }
                                    layoutParams = android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    setBackgroundColor(android.graphics.Color.WHITE)
                                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                                    isFocusable = true
                                    isFocusableInTouchMode = true
                                    android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    
                                    try {
                                        android.webkit.ServiceWorkerController.getInstance().setServiceWorkerClient(object : android.webkit.ServiceWorkerClient() {
                                            override fun shouldInterceptRequest(request: android.webkit.WebResourceRequest): android.webkit.WebResourceResponse? {
                                                return null
                                            }
                                        })
                                        android.webkit.ServiceWorkerController.getInstance().serviceWorkerWebSettings.allowContentAccess = false
                                        android.webkit.ServiceWorkerController.getInstance().serviceWorkerWebSettings.allowFileAccess = false
                                        android.webkit.ServiceWorkerController.getInstance().serviceWorkerWebSettings.blockNetworkLoads = true
                                    } catch(e: Exception) {}

                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        cacheMode = WebSettings.LOAD_NO_CACHE // Disable cache to force network hits
                                        useWideViewPort = true
                                        loadWithOverviewMode = true
                                        mediaPlaybackRequiresUserGesture = false
                                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                        builtInZoomControls = true
                                        displayZoomControls = false
                                        setSupportZoom(true)
                                        textZoom = textZoomPercent
                                        // Güvenlik İyileştirmesi: Yerel dosya sistemi saldırılarını (file:// ve content://) önlemek için
                                        // WebView dosya ve içerik erişimleri kapatılmıştır.
                                        allowFileAccess = false
                                        allowContentAccess = false
                                        setSupportMultipleWindows(appSettings.popupBlockerEnabled) // Bloklama kapalıysa mevcut sekmede açsın
                                        javaScriptCanOpenWindowsAutomatically = !appSettings.popupBlockerEnabled
                                        offscreenPreRaster = true
                                    }

                                    val isDesktop = tab.isDesktopMode || isDesktopUserAgent
                                    applyDesktopMode(this, isDesktop, ctx)

                                    addJavascriptInterface(WebAppInterface(
                                        expectedToken = com.example.sniffer.BrowserSnifferScriptV2.SNIFFER_SECRET_TOKEN,
                                        onRawMediaEventJsonCallback = { jsonStr ->
                                            coroutineScope.launch {
                                                viewModel.addSniffedLinkJson(jsonStr)
                                            }
                                        },
                                        onMediaFound = { url, title, duration, quality ->
                                            if (!AdblockDns.isAdUrl(url)) {
                                                coroutineScope.launch {
                                                    viewModel.addSniffedLink(url, title.ifBlank { "Yakalanan Akış" }, duration, quality, tab.webView?.url ?: tab.url, tab.webView?.settings?.userAgentString)
                                                }
                                            }
                                        },
                                        onSubtitleFoundCallback = { url, language, label ->
                                            if (!AdblockDns.isAdUrl(url)) {
                                                coroutineScope.launch {
                                                    viewModel.addSniffedSubtitle(url, language, label)
                                                }
                                            }
                                        },
                                        popupBlockerEnabled = appSettings.popupBlockerEnabled,
                                        onRawMediaEventCallback = { sourceType, url, type, title, duration, quality, mimeType ->
                                            coroutineScope.launch {
                                                viewModel.streamSnifferManager.parseRawEvent(sourceType, url, type, title, duration, quality, mimeType)
                                            }
                                        }
                                    ), "AndroidSniffer")

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                                            return false // Popup engelleme açıksa (multiple windows true ise) çalışır ve popupları öldürür
                                        }

                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            if (tab.id == currentTabId) webProgress = newProgress / 100f
                                            if (newProgress > 60) {
                                                view?.postInvalidate()
                                            }
                                        }

                                        override fun onReceivedTitle(view: WebView?, title: String?) {
                                            title?.let { titleText ->
                                                tab.title = titleText
                                                tab.webView = view
                                                val currentUrl = view?.url ?: tab.url
                                                if (!tab.isIncognito && currentUrl.isNotBlank() && currentUrl != "about:blank" && !currentUrl.startsWith("data:")) {
                                                    viewModel.addBrowserHistory(titleText, currentUrl)
                                                }
                                            }
                                        }
                                    }

                                    webViewClient = object : WebViewClient() {
                                        @SuppressLint("WebViewClientOnReceivedSslError")
                                        override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                                            handler?.cancel() // Güvenlik açığını kapatıyoruz
                                        }

                                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                            super.onReceivedError(view, request, error)
                                            if (request?.isForMainFrame == true && tab.id == currentTabId) {
                                                isLoading = false
                                            }
                                        }

                                        override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                                            // WebView Renderer crash yakalayıcısı. Uygulamanın toptan çökmesini engeller.
                                            view?.destroy()
                                            return true // True döndürmek sistemin uygulamayı öldürmesini durdurur.
                                        }

                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            val reqUri = request?.url ?: return false
                                            val reqUrl = reqUri.toString()
                                            val scheme = reqUri.scheme ?: ""
                                            val host = reqUri.host ?: ""

                                             // 0. Block ad redirects in the main frame
                                            if (request?.isForMainFrame == true && (AdblockDns.isAdUrl(reqUrl) || (host.isNotEmpty() && AdblockDns.isAdHost(host)))) {
                                                return true
                                            }

                                            // 1. Handle non-http custom schemes safely
                                            if (scheme != "http" && scheme != "https" && scheme != "about") {
                                                try {
                                                    if (scheme.startsWith("intent")) {
                                                        val intent = Intent.parseUri(reqUrl, Intent.URI_INTENT_SCHEME)
                                                        val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                                                        if (!fallbackUrl.isNullOrEmpty() && !AdblockDns.isAdUrl(fallbackUrl)) {
                                                            view?.loadUrl(fallbackUrl)
                                                        }
                                                    } else if (scheme == "market") {
                                                        return true // Block auto opening Play Store ad apps
                                                    } else {
                                                        val intent = Intent(Intent.ACTION_VIEW, reqUri)
                                                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                        ctx.startActivity(intent)
                                                    }
                                                } catch (_: Exception) {}
                                                return true
                                            }

                                            // Allow normal web page navigation
                                            return false
                                        }

                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            if (tab.id == currentTabId) {
                                                isLoading = true
                                                url?.let { urlInput = it }
                                                viewModel.clearSniffedLinks()
                                                canGoBack = view?.canGoBack() == true
                                                canGoForward = view?.canGoForward() == true
                                            }
                                            url?.let { urlText ->
                                                tab.url = urlText
                                            }
                                            if (tab.isDesktopMode || isDesktopUserAgent) {
                                                val desktopJs = """
                                                    (function() {
                                                        var meta = document.querySelector('meta[name="viewport"]');
                                                        if (!meta) {
                                                            meta = document.createElement('meta');
                                                            meta.name = 'viewport';
                                                            document.head.appendChild(meta);
                                                        }
                                                        meta.setAttribute('content', 'width=1280, initial-scale=0.35, minimum-scale=0.25, maximum-scale=5.0, user-scalable=yes');
                                                    })();
                                                """.trimIndent()
                                                view?.evaluateJavascript(desktopJs, null)
                                            }
                                        }

                                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                            super.doUpdateVisitedHistory(view, url, isReload)
                                            if (tab.id == currentTabId) {
                                                canGoBack = view?.canGoBack() == true
                                                canGoForward = view?.canGoForward() == true
                                                url?.let {
                                                    if (it != "about:blank") urlInput = it
                                                }
                                            }
                                            url?.let {
                                                tab.url = it
                                                if (!tab.isIncognito && it.isNotBlank() && it != "about:blank" && !it.startsWith("data:")) {
                                                    val pageTitle = view?.title?.takeIf { t -> t.isNotBlank() } ?: tab.title.takeIf { t -> t != "Yeni Sekme" } ?: it
                                                    viewModel.addBrowserHistory(pageTitle, it)
                                                }
                                            }
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            view?.evaluateJavascript(com.example.sniffer.BrowserSnifferScriptV2.SNIFFER_JS, null)
                                            if (tab.id == currentTabId) {
                                                isLoading = false
                                                canGoBack = view?.canGoBack() == true
                                                canGoForward = view?.canGoForward() == true
                                            }

                                            if (tab.isDesktopMode || isDesktopUserAgent) {
                                                val desktopJs = """
                                                    (function() {
                                                        var meta = document.querySelector('meta[name="viewport"]');
                                                        if (!meta) {
                                                            meta = document.createElement('meta');
                                                            meta.name = 'viewport';
                                                            document.head.appendChild(meta);
                                                        }
                                                        meta.setAttribute('content', 'width=1280, initial-scale=0.35, minimum-scale=0.25, maximum-scale=5.0, user-scalable=yes');
                                                    })();
                                                """.trimIndent()
                                                view?.evaluateJavascript(desktopJs, null)
                                            }

                                            // Add to browser history
                                            url?.let {
                                                tab.url = it
                                                if (!tab.isIncognito && it.isNotBlank() && it != "about:blank" && !it.startsWith("data:")) {
                                                    val pageTitle = view?.title?.takeIf { t -> t.isNotBlank() } ?: tab.title.takeIf { t -> t != "Yeni Sekme" } ?: it
                                                    viewModel.addBrowserHistory(pageTitle, it)
                                                }
                                            }

                                            // Complete Sniffer and Anti-Adblock injection (only on video/content sites)
                                            val host = try { Uri.parse(url ?: "").host?.lowercase() ?: "" } catch (_: Exception) { "" }
                                            if (!host.contains("google.") && !host.contains("yandex.") && !host.contains("bing.")) {
                                                view?.evaluateJavascript(com.example.sniffer.BrowserSnifferScriptV2.SNIFFER_JS, null)
                                            }

                                            // Force immediate frame buffer render
                                            view?.postInvalidate()
                                            view?.requestLayout()
                                        }

                                        override fun onLoadResource(view: WebView?, url: String?) {
                                            super.onLoadResource(view, url)
                                            url?.let {
                                                if (isMediaStreamUrl(it) && !AdblockDns.isAdUrl(it)) {
                                                    coroutineScope.launch {
                                                        viewModel.addSniffedLink(it, view?.title ?: "Yakalanan Web Akışı")
                                                    }
                                                }
                                            }
                                        }

                                        override fun shouldInterceptRequest(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): WebResourceResponse? {
                                            val reqUri = request?.url ?: return null
                                            val reqUrl = reqUri.toString()
                                            val host = reqUri.host ?: ""

                                            // Never intercept main document frame requests
                                            if (request.isForMainFrame) {
                                                return super.shouldInterceptRequest(view, request)
                                            }

                                            // Ad & Tracker subresource blocking (whitelisted sites like Google, Yandex are preserved)
                                            if (appSettings.adblockEnabled && !AdblockDns.isWhitelisted(host)) {
                                                if (AdblockDns.isAdUrl(reqUrl) || (host.isNotEmpty() && AdblockDns.isAdHost(host))) {
                                                    return WebResourceResponse(
                                                        "text/plain",
                                                        "UTF-8",
                                                        403,
                                                        "Forbidden",
                                                        mapOf("Access-Control-Allow-Origin" to "*"),
                                                        ByteArrayInputStream(ByteArray(0))
                                                    )
                                                }
                                            }

                                            // Media Stream Sniffing
                                            if (isMediaStreamUrl(reqUrl, request) && !AdblockDns.isAdUrl(reqUrl)) {
                                                val safeHeaders = mutableMapOf<String, String>()
                                                request?.requestHeaders?.forEach { (k, v) ->
                                                    val kl = k.lowercase()
                                                    if (kl != "cookie" && kl != "authorization") {
                                                        safeHeaders[k] = v
                                                    }
                                                }
                                                coroutineScope.launch {
                                                    viewModel.addSniffedLink(reqUrl, view?.title ?: "Yakalanan Web Akışı", "", "", view?.url ?: tab.url, view?.settings?.userAgentString, safeHeaders)
                                                }
                                            }

                                            // Subtitle Sniffing
                                            if (reqUrl.endsWith(".vtt") || reqUrl.endsWith(".srt") || reqUrl.endsWith(".ass") ||
                                                ((reqUrl.contains("subtitle") || reqUrl.contains("altyazi")) && (reqUrl.contains(".vtt") || reqUrl.contains(".srt")))) {
                                                if (!AdblockDns.isAdUrl(reqUrl)) {
                                                    coroutineScope.launch {
                                                        viewModel.addSniffedSubtitle(reqUrl, "Harici", "Yakalanan Altyazı")
                                                    }
                                                }
                                            }

                                            return super.shouldInterceptRequest(view, request)
                                        }
                                    }

                                    loadUrl(tab.url)
                                }
                            },
                            update = { view ->
                                tab.webView = view
                                view.settings.textZoom = textZoomPercent
                                val targetDesktop = tab.isDesktopMode || isDesktopUserAgent
                                val isCurrentlyDesktop = view.settings.userAgentString == DESKTOP_USER_AGENT
                                if (targetDesktop != isCurrentlyDesktop) {
                                    applyDesktopMode(view, targetDesktop, context)
                                }
                                val isBlank = tab.url.isEmpty() || tab.url == "about:blank"
                                val shouldBeVisible = tab.id == currentTabId && !isBlank && (activeVideo == null)
                                view.visibility = if (shouldBeVisible) android.view.View.VISIBLE else android.view.View.GONE
                                if (shouldBeVisible) {
                                    view.onResume()
                                    view.postInvalidate()
                                } else {
                                    if (tab.id != currentTabId || activeVideo != null) {
                                        pauseAllMediaInWebView(view)
                                    }
                                }
                            },
                            onRelease = { view ->
                                try {
                                    view.removeJavascriptInterface("AndroidSniffer")
                                    view.stopLoading()
                                    view.webChromeClient = android.webkit.WebChromeClient()
                                    view.webViewClient = android.webkit.WebViewClient()
                                    view.loadUrl("about:blank")
                                    view.clearHistory()
                                    view.destroy()
                                } catch (e: Exception) {}
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Chrome History Screen (chrome://history)
                if (currentTab.url == "chrome://history") {
                    ChromeHistoryScreen(
                        browserHistory = browserHistory,
                        onClearHistory = { viewModel.clearBrowserHistory() },
                        onDeleteHistoryItem = { item -> viewModel.deleteBrowserHistory(item) },
                        onItemClick = { url ->
                            urlInput = url
                            currentTab.url = url
                            currentTab.webView?.loadUrl(url)
                        },
                        onBack = {
                            currentTab.url = "about:blank"
                        }
                    )
                }

                // Google Chrome New Tab Screen (Home Page)
                if (currentTab.url.isEmpty() || currentTab.url == "about:blank") {
                    ChromeNewTabPage(
                        isIncognito = currentTab.isIncognito,
                        selectedEngine = selectedEngine,
                        onEngineSelect = { engine ->
                            selectedEngine = engine
                            viewModel.updateSettings(appSettings.copy(defaultSearchEngine = engine.id))
                        },
                        onSearch = { query ->
                            val target = processSearch(query)
                            urlInput = target
                            currentTab.url = target
                            currentTab.webView?.loadUrl(target)
                        },
                        onQuickLinkClick = { url ->
                            val target = processSearch(url)
                            urlInput = target
                            currentTab.url = target
                            currentTab.webView?.loadUrl(target)
                        },
                        bookmarks = bookmarks,
                        onOpenBookmarks = { showBookmarksSheet = true }
                    )
                }

                // TV Virtual Mouse Pointer (High Contrast Dual Halo Cursor for TV remote)
                if (isTvCursorMode) {
                    val cursorScale by animateFloatAsState(if (isClicking) 0.8f else 1.0f, label = "cursor_scale")
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((cursorX - 16).roundToInt(), (cursorY - 16).roundToInt()) }
                            .size(32.dp)
                            .scale(cursorScale)
                    ) {
                        // High visibility outer target ring
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(
                                    width = if (isClicking) 3.5.dp else 2.5.dp,
                                    color = if (isClicking) Color(0xFFFF5252) else MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                                .background(
                                    color = (if (isClicking) Color(0xFFFF5252) else MaterialTheme.colorScheme.primary).copy(alpha = 0.28f),
                                    shape = CircleShape
                                )
                        )
                        // Precision center dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .align(Alignment.Center)
                                .background(
                                    color = if (isClicking) Color(0xFFFF5252) else MaterialTheme.colorScheme.onPrimaryContainer,
                                    shape = CircleShape
                                )
                        )
                    }

                    // Floating TV Mode Status Indicator
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clickable { showTvControlsSheet = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Mouse, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Sanal Fare Aktif (OK ile Tıkla)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // High-visibility Animated Toast HUD (when toggling mouse on TV)
                androidx.compose.animation.AnimatedVisibility(
                    visible = cursorToggleToastMessage != null,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -50 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -50 }),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.94f),
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isTvCursorMode) Icons.Default.Mouse else Icons.Default.Tv,
                                contentDescription = null,
                                tint = if (isTvCursorMode) MaterialTheme.colorScheme.primary else Color(0xFFFF5252),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = cursorToggleToastMessage.orEmpty(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // TV Box Advanced Controls Bottom Sheet
    if (showTvControlsSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                coroutineScope.launch {
                    tvControlsSheetState.hide()
                    showTvControlsSheet = false
                }
            },
            sheetState = tvControlsSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("TV Box & Kumanda Kontrolleri", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { showTvControlsSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 1. Sanal Fare (Virtual Mouse) Toggle
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sanal Fare Modu (Virtual Cursor)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Kumanda yön tuşlarıyla imleci hareket ettirin, OK ile tıklayın.", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isTvCursorMode,
                                onCheckedChange = {
                                    isTvCursorMode = it
                                    cursorToggleToastMessage = if (it) "🎯 Sanal Fare: AÇIK" else "❌ Sanal Fare: KAPALI"
                                }
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SettingsRemote, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Kısayol Tuşu: ", fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                Text(appSettings.tvMouseShortcutKeyName, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Text("(Ayarlar'dan değiştirilebilir)", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. İmleç Hızı
                if (isTvCursorMode) {
                    Text("İmleç Hızı:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Yavaş" to 16f, "Normal" to 28f, "Hızlı" to 42f, "Çok Hızlı" to 58f).forEach { (label, speed) ->
                            FilterChip(
                                selected = cursorSpeedPx == speed,
                                onClick = { cursorSpeedPx = speed },
                                label = { Text(label, fontSize = 11.5.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // 3. TV Metin / Sayfa Yakınlaştırma (Zoom)
                Text("TV Metin & Yakınlaştırma Boyutu:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(100 to "%100", 115 to "%115", 130 to "%130", 150 to "%150", 175 to "%175").forEach { (zoom, label) ->
                        FilterChip(
                            selected = textZoomPercent == zoom,
                            onClick = {
                                textZoomPercent = zoom
                                currentTab.webView?.settings?.textZoom = zoom
                            },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Hızlı Sayfa Kaydırma & Navigasyon
                Text("Hızlı Sayfa Kaydırma:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { currentTab.webView?.scrollTo(0, 0) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.VerticalAlignTop, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("En Üst", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { currentTab.webView?.pageUp(false) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Yukarı", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { currentTab.webView?.pageDown(false) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Aşağı", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { currentTab.webView?.pageDown(true) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.VerticalAlignBottom, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("En Alt", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 5. Masaüstü Görünümü Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Masaüstü Sitesi Olarak Aç", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Switch(
                        checked = currentTab.isDesktopMode || isDesktopUserAgent,
                        onCheckedChange = { isDesktop ->
                            isDesktopUserAgent = isDesktop
                            currentTab.isDesktopMode = isDesktop
                            currentTab.webView?.let { wv ->
                                applyDesktopMode(wv, isDesktop, context)
                                wv.reload()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // Chrome-Style Tab Switcher Overlay (Grid Layout + Incognito Switcher)
    AnimatedVisibility(
        visible = showTabsSheet,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        val isIncognitoView = activeTabMode == 1
        val currentBgColor = if (isIncognitoView) Color(0xFF202124) else MaterialTheme.colorScheme.background
        val currentContentColor = if (isIncognitoView) Color.White else MaterialTheme.colorScheme.onBackground
        val filteredTabs = tabs.filter { if (isIncognitoView) it.isIncognito else !it.isIncognito }
        var showTabOverflowMenu by remember { mutableStateOf(false) }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = currentBgColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            ) {
                // Top Header: Tab Mode Segmented Control & Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Segmented Selector: Normal vs Incognito
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isIncognitoView) Color(0xFF303134) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val normalCount = tabs.count { !it.isIncognito }
                            val incognitoCount = tabs.count { it.isIncognito }

                            // Normal tab toggle
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (!isIncognitoView) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier
                                    .clickable { activeTabMode = 0 }
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Language,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (!isIncognitoView) MaterialTheme.colorScheme.onPrimary else currentContentColor
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$normalCount",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!isIncognitoView) MaterialTheme.colorScheme.onPrimary else currentContentColor
                                    )
                                }
                            }

                            // Incognito tab toggle
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isIncognitoView) Color(0xFFE8EAED) else Color.Transparent,
                                modifier = Modifier
                                    .clickable { activeTabMode = 1 }
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (isIncognitoView) Color.Black else currentContentColor
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$incognitoCount",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isIncognitoView) Color.Black else currentContentColor
                                    )
                                }
                            }
                        }
                    }

                    // Action buttons: New Tab, More, Close Tab Switcher
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val newTab = BrowserTab(isIncognito = isIncognitoView, url = "about:blank")
                                tabs = tabs + newTab
                                currentTabId = newTab.id
                                urlInput = ""
                                showTabsSheet = false
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Yeni Sekme", tint = currentContentColor)
                        }

                        Box {
                            IconButton(onClick = { showTabOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Daha Fazla", tint = currentContentColor)
                            }
                            DropdownMenu(
                                expanded = showTabOverflowMenu,
                                onDismissRequest = { showTabOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Yeni sekme") },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                    onClick = {
                                        val newTab = BrowserTab(isIncognito = false, url = "about:blank")
                                        tabs = tabs + newTab
                                        currentTabId = newTab.id
                                        activeTabMode = 0
                                        urlInput = ""
                                        showTabOverflowMenu = false
                                        showTabsSheet = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Yeni gizli sekme") },
                                    leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                                    onClick = {
                                        val newTab = BrowserTab(isIncognito = true, url = "about:blank")
                                        tabs = tabs + newTab
                                        currentTabId = newTab.id
                                        activeTabMode = 1
                                        urlInput = ""
                                        showTabOverflowMenu = false
                                        showTabsSheet = false
                                    }
                                )
                                if (filteredTabs.isNotEmpty()) {
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (isIncognitoView) "Tüm gizli sekmeleri kapat" else "Tüm sekmeleri kapat",
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            filteredTabs.forEach { it.webView?.destroy() }
                                            val remaining = tabs.filter { if (isIncognitoView) !it.isIncognito else it.isIncognito }
                                            if (remaining.isEmpty()) {
                                                val freshTab = BrowserTab(isIncognito = isIncognitoView, url = "about:blank")
                                                tabs = listOf(freshTab)
                                                currentTabId = freshTab.id
                                            } else {
                                                tabs = remaining
                                                currentTabId = remaining.last().id
                                            }
                                            showTabOverflowMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(onClick = { showTabsSheet = false }) {
                            Icon(Icons.Default.Check, contentDescription = "Bitti", tint = currentContentColor)
                        }
                    }
                }

                // Grid View of Tabs
                if (filteredTabs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isIncognitoView) Icons.Default.VisibilityOff else Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = if (isIncognitoView) Color(0xFF9AA0A6) else MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = if (isIncognitoView) "Açık gizli sekme yok" else "Açık sekme yok",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isIncognitoView) Color(0xFFE8EAED) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    val newTab = BrowserTab(isIncognito = isIncognitoView, url = "about:blank")
                                    tabs = tabs + newTab
                                    currentTabId = newTab.id
                                    urlInput = ""
                                    showTabsSheet = false
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isIncognitoView) "Yeni Gizli Sekme" else "Yeni Sekme")
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(filteredTabs, key = { it.id }) { tab ->
                            val isSelected = tab.id == currentTabId
                            val cardBg = if (tab.isIncognito) Color(0xFF2D2E30) else MaterialTheme.colorScheme.surfaceVariant
                            val borderColor = if (isSelected) {
                                if (tab.isIncognito) Color(0xFF8AB4F8) else MaterialTheme.colorScheme.primary
                            } else Color.Transparent

                            Card(
                                onClick = {
                                    currentTabId = tab.id
                                    urlInput = if (tab.url == "about:blank") "" else tab.url
                                    showTabsSheet = false
                                },
                                shape = RoundedCornerShape(16.dp),
                                border = if (isSelected) BorderStroke(2.5.dp, borderColor) else null,
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.72f)
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Card Title Bar
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (tab.isIncognito) Color(0xFF35363A) else MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .background(
                                                        if (tab.isIncognito) Color(0xFF8AB4F8) else MaterialTheme.colorScheme.primary,
                                                        CircleShape
                                                    )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (tab.url == "about:blank" || tab.url.isEmpty()) "Yeni sekme" else tab.title.ifBlank { "Web Sayfası" },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (tab.isIncognito) Color(0xFFE8EAED) else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                tab.webView?.destroy()
                                                val newTabs = tabs.filter { it.id != tab.id }
                                                if (newTabs.isEmpty()) {
                                                    val fresh = BrowserTab(isIncognito = isIncognitoView, url = "about:blank")
                                                    tabs = listOf(fresh)
                                                    currentTabId = fresh.id
                                                } else {
                                                    if (currentTabId == tab.id) {
                                                        currentTabId = newTabs.last().id
                                                    }
                                                    tabs = newTabs
                                                }
                                            },
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Kapat",
                                                modifier = Modifier.size(15.dp),
                                                tint = if (tab.isIncognito) Color(0xFF9AA0A6) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Card Body Preview
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (tab.isIncognito) Icons.Default.VisibilityOff else Icons.Default.Language,
                                                contentDescription = null,
                                                modifier = Modifier.size(28.dp),
                                                tint = if (tab.isIncognito) Color(0xFF8AB4F8) else MaterialTheme.colorScheme.primary
                                            )
                                            val previewDomain = try {
                                                if (tab.url.startsWith("http")) Uri.parse(tab.url).host ?: tab.url else tab.url
                                            } catch (_: Exception) { tab.url }
                                            Text(
                                                text = if (tab.url == "about:blank" || tab.url.isEmpty()) "Yeni Sekme" else previewDomain,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (tab.isIncognito) Color(0xFFBDC1C6) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom bar in Tab Switcher: Add new tab button
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isIncognitoView) Color(0xFF2D2E30) else MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = {
                                val newTab = BrowserTab(isIncognito = isIncognitoView, url = "about:blank")
                                tabs = tabs + newTab
                                currentTabId = newTab.id
                                urlInput = ""
                                showTabsSheet = false
                            },
                            shape = RoundedCornerShape(24.dp),
                            colors = if (isIncognitoView) ButtonDefaults.buttonColors(containerColor = Color(0xFF8AB4F8), contentColor = Color.Black) else ButtonDefaults.buttonColors()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isIncognitoView) "Yeni Gizli Sekme" else "Yeni Sekme", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Chrome Bookmarks Modal Bottom Sheet
    if (showBookmarksSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                coroutineScope.launch {
                    bookmarksSheetState.hide()
                    showBookmarksSheet = false
                }
            },
            sheetState = bookmarksSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color(0xFFFBBC04))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Yer İşaretleri", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    if (currentTab.url.isNotEmpty() && currentTab.url != "about:blank") {
                        FilledTonalButton(
                            onClick = {
                                viewModel.addBookmark(
                                    title = currentTab.title.ifBlank { currentTab.url },
                                    url = currentTab.url,
                                    imageUrl = ""
                                )
                                android.widget.Toast.makeText(context, "Yer işareti eklendi", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bu Sayfayı Ekle", fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (bookmarks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Kayıtlı yer işareti bulunmuyor.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(bookmarks) { bookmark ->
                            ListItem(
                                headlineContent = { Text(bookmark.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium) },
                                supportingContent = { Text(bookmark.url, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp) },
                                leadingContent = {
                                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color(0xFFFBBC04))
                                },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.deleteBookmark(bookmark) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Sil", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                                    }
                                },
                                modifier = Modifier.clickable {
                                    val target = bookmark.url
                                    urlInput = target
                                    currentTab.url = target
                                    currentTab.webView?.loadUrl(target)
                                    showBookmarksSheet = false
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    // Chrome SSL / Connection Security Dialog
    if (showSslInfoDialog) {
        val host = try { Uri.parse(currentTab.url).host ?: currentTab.url } catch (_: Exception) { currentTab.url }
        AlertDialog(
            onDismissRequest = { showSslInfoDialog = false },
            icon = {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF1E8E3E), modifier = Modifier.size(32.dp))
            },
            title = {
                Text("Bağlantı Güvenli", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(host, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Bilgileriniz (şifreler, kart bilgileri vb.) bu siteye gönderildiğinde uçtan uca TLS şifrelemesi ile gizli tutulur.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF1E8E3E), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sertifika: Geçerli (TLS/HTTPS Şifreli)", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Zararlı Reklam Koruması: Etkin", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSslInfoDialog = false }) {
                    Text("Kapat")
                }
            }
        )
    }

    // Search Engine Selection Dialog
    if (showEngineMenu) {
        AlertDialog(
            onDismissRequest = { showEngineMenu = false },
            title = {
                Text("Varsayılan Arama Motoru", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SearchEngine.entries.forEach { engine ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (engine == selectedEngine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedEngine = engine
                                    viewModel.updateSettings(appSettings.copy(defaultSearchEngine = engine.id))
                                    showEngineMenu = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(engine.color, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(engine.initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(engine.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(engine.homeUrl, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (engine == selectedEngine) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEngineMenu = false }) {
                    Text("Vazgeç")
                }
            }
        )
    }

    // Sniffed Links Modal Sheet
    if (showSniffedBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                coroutineScope.launch {
                    sniffedSheetState.hide()
                    showSniffedBottomSheet = false
                }
            },
            sheetState = sniffedSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Yakalanan Medya Akışları (${validSniffedLinks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = {
                        viewModel.clearSniffedLinks()
                        showSniffedBottomSheet = false
                    }) {
                        Text("Temizle")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StreamFilter.values().forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (validSniffedLinks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Bu filtreye uygun video akışı bulunamadı.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 350.dp)
                    ) {
                        items(validSniffedLinks) { link ->
                            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = link.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (link.isMerged) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer
                                        ) {
                                            Text(
                                                text = "Birleşik (Ses+Görüntü)",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = link.format,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = link.quality,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (link.duration.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer
                                        ) {
                                            Text(
                                                text = "⏱ ${link.duration}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = link.url,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { 
                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(link.url))
                                            android.widget.Toast.makeText(context, "Bağlantı kopyalandı", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Kopyala", modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.End,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val type = when { link.format.contains("HLS") || link.url.contains(".m3u8") -> "HLS_DOWNLOAD" ; link.format.contains("DASH") || link.url.contains(".mpd") -> "DASH_DOWNLOAD" ; else -> "DIRECT_DOWNLOAD" }
                                            viewModel.startDownload(
                                                type = type, 
                                                title = link.title,
                                                url = link.url,
                                                referer = link.referer,
                                                userAgent = link.userAgent,
                                                headers = link.headers
                                            )
                                            showSniffedBottomSheet = false
                                        }
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("İndir", fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.saveLink(
                                                title = link.title,
                                                url = link.url,
                                                category = if (link.url.contains("m3u8")) "M3U Çalma Listesi" else "Direkt Link"
                                            )
                                        }
                                    ) {
                                        Text("Kaydet", fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(
                                        onClick = {
                                            showSniffedBottomSheet = false
                                            // Immediately mute and pause active tab's web player
                                            tabs.forEach { t -> pauseAllMediaInWebView(t.webView) }
                                            viewModel.playVideo(
                                                title = link.title,
                                                url = link.url
                                            )
                                        }
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Oynat", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // History Modal Sheet
    if (showHistorySheet) {
        var historySearchQuery by remember { mutableStateOf("") }
        val filteredHistory = remember(browserHistory, historySearchQuery) {
            if (historySearchQuery.isBlank()) browserHistory
            else browserHistory.filter {
                it.title.contains(historySearchQuery, ignoreCase = true) ||
                it.url.contains(historySearchQuery, ignoreCase = true)
            }
        }
        var showConfirmClearDialog by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = {
                showHistorySheet = false
            },
            sheetState = historySheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tarayıcı Geçmişi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        if (browserHistory.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                Text("${browserHistory.size}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                    if (browserHistory.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                viewModel.clearBrowserHistory()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Tümünü Temizle",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tümünü Temizle", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (browserHistory.isNotEmpty()) {
                    OutlinedTextField(
                        value = historySearchQuery,
                        onValueChange = { historySearchQuery = it },
                        placeholder = { Text("Geçmişte ara...", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (historySearchQuery.isNotEmpty()) {
                                IconButton(onClick = { historySearchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Temizle", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (browserHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.HistoryToggleOff,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Henüz tarama geçmişi yok.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else if (filteredHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "\"$historySearchQuery\" için sonuç bulunamadı.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(filteredHistory, key = { it.id }) { item ->
                            val formatter = remember { java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault()) }
                            val dateString = formatter.format(java.util.Date(item.timestamp))
                            ListItem(
                                leadingContent = {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Language,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                },
                                headlineContent = {
                                    Text(
                                        text = item.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = item.url,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = dateString,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { viewModel.deleteBrowserHistory(item) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Sil",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .clickable {
                                        val targetUrl = item.url
                                        urlInput = targetUrl
                                        currentTab.url = targetUrl
                                        currentTab.webView?.let { wv ->
                                            wv.visibility = android.view.View.VISIBLE
                                            wv.loadUrl(targetUrl)
                                        }
                                        showHistorySheet = false
                                    }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }

                if (showConfirmClearDialog) {
                    AlertDialog(
                        onDismissRequest = { showConfirmClearDialog = false },
                        icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        title = { Text("Geçmişi Temizle") },
                        text = { Text("Tüm web tarama geçmişiniz silinecektir. Emin misiniz?") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.clearBrowserHistory()
                                    showConfirmClearDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Evet, Temizle")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showConfirmClearDialog = false }) {
                                Text("İptal")
                            }
                        }
                    )
                }
            }
        }
    }
}
}

@Composable
private fun ChromeNewTabPage(
    isIncognito: Boolean,
    selectedEngine: SearchEngine,
    onEngineSelect: (SearchEngine) -> Unit,
    onSearch: (String) -> Unit,
    onQuickLinkClick: (String) -> Unit,
    bookmarks: List<BookmarkEntity>,
    onOpenBookmarks: () -> Unit
) {
    var homeSearchQuery by remember { mutableStateOf("") }

    if (isIncognito) {
        // Incognito New Tab Page
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF202124))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFF303134), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = Color(0xFFE8EAED),
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Gizli moda geçtiniz",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Artık gizli olarak gezinebilirsiniz; bu cihazı kullanan diğer kişiler etkinliğinizi görmez. İndirilenler ve yer işaretleri kaydedilir.",
                fontSize = 13.sp,
                color = Color(0xFFBDC1C6),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Incognito Search Input
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF303134),
                border = BorderStroke(1.dp, Color(0xFF5F6368)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF9AA0A6))
                    Spacer(modifier = Modifier.width(10.dp))
                    BasicTextField(
                        value = homeSearchQuery,
                        onValueChange = { homeSearchQuery = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (homeSearchQuery.isNotBlank()) onSearch(homeSearchQuery)
                        }),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (homeSearchQuery.isEmpty()) {
                                Text("Gizli ara veya URL yazın...", color = Color(0xFF9AA0A6), fontSize = 14.sp)
                            }
                            inner()
                        }
                    )
                    if (homeSearchQuery.isNotBlank()) {
                        IconButton(
                            onClick = { onSearch(homeSearchQuery) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Ara", tint = Color(0xFF8AB4F8), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Information Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF282A2D),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Chrome şu bilgileri kaydetmez:", fontWeight = FontWeight.Bold, color = Color(0xFFE8EAED), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Tarama geçmişiniz\n• Çerezler ve site verileri\n• Formlara girilen bilgiler", fontSize = 12.sp, color = Color(0xFF9AA0A6), lineHeight = 18.sp)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFF3C4043))
                    Text("Şunlar yine de görülebilir:", fontWeight = FontWeight.Bold, color = Color(0xFFE8EAED), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Ziyaret ettiğiniz web siteleri\n• İşvereniniz veya okulunuz\n• İnternet servis sağlayıcınız", fontSize = 12.sp, color = Color(0xFF9AA0A6), lineHeight = 18.sp)
                }
            }
        }
    } else {
        // Standard Chrome New Tab Page
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Chrome / Engine Logo
            if (selectedEngine == SearchEngine.GOOGLE) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("G", fontSize = 46.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
                    Text("o", fontSize = 46.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEA4335))
                    Text("o", fontSize = 46.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBC05))
                    Text("g", fontSize = 46.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
                    Text("l", fontSize = 46.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34A853))
                    Text("e", fontSize = 46.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEA4335))
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(selectedEngine.color, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(selectedEngine.initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = selectedEngine.title,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Chrome Omnibox Input Field
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                shadowElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Ara",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    BasicTextField(
                        value = homeSearchQuery,
                        onValueChange = { homeSearchQuery = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (homeSearchQuery.isNotBlank()) onSearch(homeSearchQuery)
                        }),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (homeSearchQuery.isEmpty()) {
                                Text(
                                    "${selectedEngine.title}'da arayın veya URL yazın",
                                    fontSize = 14.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            inner()
                        }
                    )
                    if (homeSearchQuery.isNotBlank()) {
                        IconButton(
                            onClick = { onSearch(homeSearchQuery) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Git",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search Engine Quick Selector Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchEngine.entries.forEach { engine ->
                    val isSelected = engine == selectedEngine
                    FilterChip(
                        selected = isSelected,
                        onClick = { onEngineSelect(engine) },
                        label = { Text(engine.title, fontSize = 12.sp) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(engine.color, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(engine.initial, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Shortcuts Grid (Top Sites)
            val shortcuts = listOf(
                Pair("YouTube", "https://www.youtube.com"),
                Pair("Google", "https://www.google.com"),
                Pair("Wikipedia", "https://tr.wikipedia.org"),
                Pair("Ekşi Sözlük", "https://eksisozluk.com"),
                Pair("Twitch", "https://www.twitch.tv"),
                Pair("Haberler", "https://news.google.com"),
                Pair("Akış Testi", "https://test-streams.mux.dev"),
                Pair("Yer İşaretleri", "bookmarks://local")
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                for (rowIndex in 0..1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        for (colIndex in 0..3) {
                            val itemIndex = rowIndex * 4 + colIndex
                            if (itemIndex < shortcuts.size) {
                                val (name, url) = shortcuts[itemIndex]
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable {
                                            if (url == "bookmarks://local") {
                                                onOpenBookmarks()
                                            } else {
                                                onQuickLinkClick(url)
                                            }
                                        }
                                        .padding(4.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        tonalElevation = 2.dp,
                                        modifier = Modifier.size(50.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            when (name) {
                                                "YouTube" -> Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFFFF0000), modifier = Modifier.size(24.dp))
                                                "Google" -> Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                                "Wikipedia" -> Text("W", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                                "Ekşi Sözlük" -> Text("e$", color = Color(0xFF81C784), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                                "Twitch" -> Text("T", color = Color(0xFF9146FF), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                                "Haberler" -> Icon(Icons.Default.Newspaper, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                                "Akış Testi" -> Icon(Icons.Default.Movie, contentDescription = null, tint = Color(0xFFFB8C00), modifier = Modifier.size(22.dp))
                                                else -> Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color(0xFFFBBC04), modifier = Modifier.size(22.dp))
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (bookmarks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Kaydedilen Yer İşaretleri", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    TextButton(onClick = onOpenBookmarks) {
                        Text("Tümünü Gör (${bookmarks.size})", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    bookmarks.take(4).forEach { bookmark ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onQuickLinkClick(bookmark.url) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color(0xFFFBBC04), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(bookmark.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(bookmark.url, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

private fun isMediaStreamUrl(url: String, request: WebResourceRequest? = null): Boolean {
    val lower = url.lowercase()

    if (request != null) {
        val acceptHeader = request.requestHeaders?.get("Accept")?.lowercase() ?: ""
        val secFetchDest = request.requestHeaders?.get("Sec-Fetch-Dest")?.lowercase() ?: ""
        val rangeHeader = request.requestHeaders?.get("Range")?.lowercase() ?: ""
        
        // If the browser explicitly asks for an HTML document or it's navigating an iframe, it's not a raw media stream
        if (acceptHeader.contains("text/html") || secFetchDest == "document" || secFetchDest == "iframe" || secFetchDest == "subdocument") {
            return false
        }
        
        if (acceptHeader.contains("video/") || acceptHeader.contains("audio/") ||
            secFetchDest == "video" || secFetchDest == "audio") {
            return true
        }

        // Strong media signal
        if (rangeHeader.startsWith("bytes=")) {
            // Check if it's not a typical web resource
            if (!lower.endsWith(".png") && !lower.endsWith(".jpg") && !lower.endsWith(".css") && !lower.endsWith(".js") && !lower.endsWith(".woff2")) {
                 return true
            }
        }
    }

    // Ignore common non-media extensions and HTML pages disguised as media URLs
    if (lower.endsWith(".html") || lower.endsWith(".htm") || lower.contains("/embed/") || lower.contains("/iframe/") || lower.endsWith(".php") ||
        lower.endsWith(".js") || lower.endsWith(".css") || lower.endsWith(".json") ||
        lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
        lower.endsWith(".gif") || lower.endsWith(".svg") || lower.endsWith(".webp") ||
        lower.endsWith(".ico") || lower.contains(".js?") || lower.contains(".css?") ||
        lower.endsWith(".woff") || lower.endsWith(".woff2") || lower.endsWith(".ttf") ||
        lower.endsWith(".txt") || lower.endsWith(".xml")) {
        return false
    }

    val urlWithoutQuery = lower.substringBefore("?")

    if (urlWithoutQuery.endsWith(".mp4") || lower.contains(".mp4?") || lower.contains(".mp4&") || lower.contains("/mp4/")) return true
    if (urlWithoutQuery.endsWith(".m3u8") || lower.contains(".m3u8?") || lower.contains("/m3u8") || lower.contains("m3u8")) return true
    if (urlWithoutQuery.endsWith(".mkv") || lower.contains(".mkv?")) return true
    if (urlWithoutQuery.endsWith(".webm") || lower.contains(".webm?")) return true
    if (urlWithoutQuery.endsWith(".avi") || lower.contains(".avi?")) return true
    if (urlWithoutQuery.endsWith(".mpd") || lower.contains(".mpd?") || lower.contains("/mpd/")) return true
    if (urlWithoutQuery.endsWith(".flv") || lower.contains(".flv?")) return true
    if (urlWithoutQuery.endsWith(".m4v") || lower.contains(".m4v?")) return true
    if (urlWithoutQuery.endsWith(".3gp") || lower.contains(".3gp?")) return true
    
    // Check if it's a known progressive stream pattern without extension
    if (lower.contains("/getvideo") || lower.contains("/stream") || lower.contains("/chunk") || lower.contains("/video")) {
         return true
    }
    return false
}

@Composable
private fun ChromeHistoryScreen(
    browserHistory: List<com.example.data.model.BrowserHistoryEntity>,
    onClearHistory: () -> Unit,
    onDeleteHistoryItem: (com.example.data.model.BrowserHistoryEntity) -> Unit,
    onItemClick: (String) -> Unit,
    onBack: () -> Unit
) {
    var historySearchQuery by remember { mutableStateOf("") }
    val filteredHistory = remember(browserHistory, historySearchQuery) {
        if (historySearchQuery.isBlank()) browserHistory
        else browserHistory.filter {
            it.title.contains(historySearchQuery, ignoreCase = true) ||
            it.url.contains(historySearchQuery, ignoreCase = true)
        }
    }
    var showConfirmClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Geçmiş",
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (browserHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                        Text("${browserHistory.size}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            if (browserHistory.isNotEmpty()) {
                TextButton(
                    onClick = { showConfirmClearDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Tümünü Temizle",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tümünü Temizle", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar
        if (browserHistory.isNotEmpty()) {
            OutlinedTextField(
                value = historySearchQuery,
                onValueChange = { historySearchQuery = it },
                placeholder = { Text("Geçmişte ara...", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (historySearchQuery.isNotEmpty()) {
                        IconButton(onClick = { historySearchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Temizle", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Content List / Empty state
        if (browserHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.HistoryToggleOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Henüz tarama geçmişi yok.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else if (filteredHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\"$historySearchQuery\" için sonuç bulunamadı.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredHistory, key = { it.id }) { item ->
                    val formatter = remember { java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()) }
                    val dateString = formatter.format(java.util.Date(item.timestamp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 1.dp
                    ) {
                        ListItem(
                            leadingContent = {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            },
                            headlineContent = {
                                Text(
                                    text = item.title.ifBlank { item.url },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            },
                            supportingContent = {
                                Column {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.url,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = dateString,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = { onDeleteHistoryItem(item) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Sil",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onItemClick(item.url) }
                        )
                    }
                }
            }
        }

        if (showConfirmClearDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmClearDialog = false },
                icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Geçmişi Temizle") },
                text = { Text("Tüm web tarama geçmişiniz silinecektir. Emin misiniz?") },
                confirmButton = {
                    Button(
                        onClick = {
                            onClearHistory()
                            showConfirmClearDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Evet, Temizle")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmClearDialog = false }) {
                        Text("İptal")
                    }
                }
            )
        }
    }
}
