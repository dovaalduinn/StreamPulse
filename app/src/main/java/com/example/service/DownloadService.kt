package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.data.database.AppDatabase
import com.example.data.repository.MediaPlayerRepository
import com.example.util.AppDownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DownloadService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var downloadManager: AppDownloadManager
    private lateinit var repository: MediaPlayerRepository
    
    private val NOTIFICATION_CHANNEL_ID = "download_channel"
    private val NOTIFICATION_ID = 1001

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getDatabase(this)
        repository = MediaPlayerRepository(
            database.watchHistoryDao(),
            database.savedLinkDao(),
            database.playerSettingsDao(),
            database.bookmarkDao(),
            database.browserHistoryDao(),
            database.downloadDao()
        )
        downloadManager = AppDownloadManager(this, repository)
        
        createNotificationChannel()
        
        var wasDownloading = false
        var isFirstCollect = true
        serviceScope.launch {
            repository.downloadsList.collect { downloads ->
                val activeDownloads = downloads.filter { it.status == "DOWNLOADING" }
                
                if (isFirstCollect) {
                    isFirstCollect = false
                    activeDownloads.forEach { item ->
                        startForegroundNotification()
                        downloadManager.startOrResumeDownload("DIRECT_DOWNLOAD", item.title, item.url, item.posterUrl, item.id)
                    }
                }
                
                if (activeDownloads.isNotEmpty()) {
                    wasDownloading = true
                    updateNotification(activeDownloads)
                } else if (wasDownloading) {
                    wasDownloading = false
                    if (downloads.any { it.status == "PAUSED" || it.status == "COMPLETED" || it.status == "FAILED" }) {
                        // Let's provide a summary notification of the last state
                        val completed = downloads.count { it.status == "COMPLETED" }
                        val paused = downloads.count { it.status == "PAUSED" }
                        val failed = downloads.count { it.status == "FAILED" }
                        
                        val intent = Intent(this@DownloadService, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            this@DownloadService, 0, intent, PendingIntent.FLAG_IMMUTABLE
                        )
                
                        val notification = NotificationCompat.Builder(this@DownloadService, NOTIFICATION_CHANNEL_ID)
                            .setContentTitle("İndirmeler Durdu")
                            .setContentText("Tamamlanan: $completed, Duraklatılan: $paused, Hata: $failed")
                            .setSmallIcon(android.R.drawable.stat_sys_download_done)
                            .setContentIntent(pendingIntent)
                            .setOngoing(false)
                            .build()
                
                        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        manager.notify(NOTIFICATION_ID, notification)
                        
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_DETACH)
                    } else {
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    }
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_STICKY
        
        val action = intent.action
        when (action) {
            "START" -> {
                val title = intent.getStringExtra("title") ?: ""
                val url = intent.getStringExtra("url") ?: ""
                val posterUrl = intent.getStringExtra("posterUrl") ?: ""
                val id = intent.getLongExtra("id", -1L).takeIf { it != -1L }
                val referer = intent.getStringExtra("referer")
                val userAgent = intent.getStringExtra("userAgent")
                val headersBundle = intent.getBundleExtra("headers")
                val headers = mutableMapOf<String, String>()
                headersBundle?.keySet()?.forEach { key ->
                    headersBundle.getString(key)?.let { v -> headers[key] = v }
                }
                startForegroundNotification()
                val type = intent.getStringExtra("type") ?: "DIRECT_DOWNLOAD"
                downloadManager.startOrResumeDownload(
                    type = type,
                    title = title,
                    url = url,
                    posterUrl = posterUrl,
                    existingId = id,
                    referer = referer,
                    userAgent = userAgent,
                    headers = headers
                )
            }
            "PAUSE" -> {
                val id = intent.getLongExtra("id", -1L)
                if (id != -1L) downloadManager.pauseDownload(id)
            }
            "RESUME" -> {
                val id = intent.getLongExtra("id", -1L)
                startForegroundNotification()
                if (id != -1L) downloadManager.resumeDownload(id)
            }
            "CANCEL" -> {
                val id = intent.getLongExtra("id", -1L)
                if (id != -1L) downloadManager.cancelOrDeleteDownload(id)
            }
            "CLEAR_ALL" -> {
                downloadManager.cancelAllDownloads()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "İndirmeler",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Video indirme durumu"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("İndirme Başlatılıyor...")
            .setContentText("Lütfen bekleyin")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )
    }

    private fun updateNotification(activeDownloads: List<com.example.data.model.DownloadItemEntity>) {
        if (activeDownloads.isEmpty()) return
        
        val totalProgress = activeDownloads.map { it.progress }.average().toInt()
        val totalSpeed = activeDownloads.joinToString(", ") { it.speed }.takeIf { it.isNotBlank() } ?: "İndiriliyor..."
        val titles = activeDownloads.joinToString(", ") { it.title }.take(40) + "..."
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("İndiriliyor: ${activeDownloads.size} video")
            .setContentText("$titles ($totalSpeed)")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, totalProgress, false)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        downloadManager.cleanup()
        serviceScope.cancel()
    }
}
