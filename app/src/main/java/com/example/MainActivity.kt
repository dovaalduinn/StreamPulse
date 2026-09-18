package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.ui.MainScreen
import com.example.ui.theme.StreamPulseTheme
import com.example.ui.viewmodel.MediaPlayerViewModel
import com.example.ui.viewmodel.PlayerRemoteAction
import com.example.util.GlobalKeyCaptureHolder

class MainActivity : ComponentActivity() {

    private val viewModel: MediaPlayerViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Purge any stale session temporary caches from prior ungraceful exits
        com.example.ui.player.PlayerSessionCacheManager.cleanAllOldSessions(this)
        
        enableEdgeToEdge()
        setContent {
            StreamPulseTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    private var lastOkPressTime = 0L

    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val settings = viewModel.settings.value
        val isCursorMode = viewModel.isTvCursorActive.value
        val keyCode = event.keyCode
        val action = event.action
        val isDown = action == KeyEvent.ACTION_DOWN
        val isUp = action == KeyEvent.ACTION_UP

        // 1. If currently capturing a key in Settings screen
        if (isDown && GlobalKeyCaptureHolder.isCapturing) {
            // Ignore back & escape so user can cancel
            if (keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_ESCAPE) {
                GlobalKeyCaptureHolder.onKeyCaptured?.invoke(keyCode)
                return true
            }
        }

        // 2. Virtual Mouse Toggle Key (Custom assigned shortcut key or default Menu / Red key)
        if (isDown && (keyCode == settings.tvMouseShortcutKeyCode || (settings.tvMouseShortcutKeyCode == 0 && (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_PROG_RED)))) {
            viewModel.toggleVirtualMouse()
            return true
        }

        // 3. Dedicated Drag & Scroll Mode Shortcut Key (Green key by default or custom assigned)
        if (isDown && (keyCode == settings.tvKeyScrollMode || keyCode == KeyEvent.KEYCODE_PROG_GREEN)) {
            viewModel.toggleDragScrollMode()
            return true
        }

        // 4. Page Up & Page Down Quick Scroll
        if (isDown && (keyCode == settings.tvKeyPageUp || keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_CHANNEL_UP)) {
            viewModel.scrollPage(window.decorView, true)
            return true
        }
        if (isDown && (keyCode == settings.tvKeyPageDown || keyCode == KeyEvent.KEYCODE_PAGE_DOWN || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN)) {
            viewModel.scrollPage(window.decorView, false)
            return true
        }

        // 5. If Virtual Mouse is Active: Intercept D-pad & Center/OK clicks to move, drag and click anywhere
        if (isCursorMode) {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels.toFloat()
            val screenHeight = displayMetrics.heightPixels.toFloat()
            val speed = settings.tvCursorSpeed.coerceIn(10f, 120f)

            if (keyCode == settings.tvKeyDpadUp || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (isDown) viewModel.moveCursor(0f, -speed, screenWidth, screenHeight, window.decorView)
                return true
            }
            if (keyCode == settings.tvKeyDpadDown || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (isDown) viewModel.moveCursor(0f, speed, screenWidth, screenHeight, window.decorView)
                return true
            }
            if (keyCode == settings.tvKeyDpadLeft || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (isDown) viewModel.moveCursor(-speed, 0f, screenWidth, screenHeight, window.decorView)
                return true
            }
            if (keyCode == settings.tvKeyDpadRight || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (isDown) viewModel.moveCursor(speed, 0f, screenWidth, screenHeight, window.decorView)
                return true
            }
            if (keyCode == settings.tvKeyDpadCenter || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (isDown) {
                    val now = System.currentTimeMillis()
                    if (now - lastOkPressTime < 380) {
                        // Double-tap OK toggles Drag / Scroll mode!
                        viewModel.toggleDragScrollMode()
                    }
                    lastOkPressTime = now
                    viewModel.performSimulatedTouch(window.decorView, MotionEvent.ACTION_DOWN)
                } else if (isUp) {
                    viewModel.performSimulatedTouch(window.decorView, MotionEvent.ACTION_UP)
                }
                return true
            }
        }

        // 4. Video Player Remote Shortcuts (when video is actively playing)
        val isVideoActive = viewModel.activeVideo.value != null
        if (isVideoActive && isDown) {
            when (keyCode) {
                settings.tvKeyPlayPause, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    viewModel.dispatchPlayerAction(PlayerRemoteAction.PLAY_PAUSE)
                    return true
                }
                settings.tvKeyForward, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    viewModel.dispatchPlayerAction(PlayerRemoteAction.FORWARD_10S)
                    return true
                }
                settings.tvKeyRewind, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    viewModel.dispatchPlayerAction(PlayerRemoteAction.REWIND_10S)
                    return true
                }
                settings.tvKeyFullscreen, KeyEvent.KEYCODE_PROG_BLUE -> {
                    viewModel.dispatchPlayerAction(PlayerRemoteAction.TOGGLE_FULLSCREEN)
                    return true
                }
                settings.tvKeySubtitle, KeyEvent.KEYCODE_CAPTIONS -> {
                    viewModel.dispatchPlayerAction(PlayerRemoteAction.CYCLE_SUBTITLE)
                    return true
                }
                settings.tvKeyAudioTrack, KeyEvent.KEYCODE_PROG_YELLOW -> {
                    viewModel.dispatchPlayerAction(PlayerRemoteAction.CYCLE_AUDIO)
                    return true
                }
                settings.tvKeyMute, KeyEvent.KEYCODE_VOLUME_MUTE -> {
                    viewModel.dispatchPlayerAction(PlayerRemoteAction.TOGGLE_MUTE)
                    return true
                }
                settings.tvKeyNextVideo, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    viewModel.dispatchPlayerAction(PlayerRemoteAction.NEXT_VIDEO)
                    return true
                }
                settings.tvKeyPrevVideo, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    viewModel.dispatchPlayerAction(PlayerRemoteAction.PREV_VIDEO)
                    return true
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }
}
