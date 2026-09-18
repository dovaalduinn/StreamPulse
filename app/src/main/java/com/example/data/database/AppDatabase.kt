package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.BookmarkDao
import com.example.data.dao.BrowserHistoryDao
import com.example.data.dao.DownloadDao
import com.example.data.dao.PlayerSettingsDao
import com.example.data.dao.SavedLinkDao
import com.example.data.dao.WatchHistoryDao
import com.example.data.model.BookmarkEntity
import com.example.data.model.BrowserHistoryEntity
import com.example.data.model.DownloadItemEntity
import com.example.data.model.PlayerSettingsEntity
import com.example.data.model.SavedLinkEntity
import com.example.data.model.WatchHistoryEntity
import com.example.data.model.IptvPlaylistEntity
import com.example.data.dao.IptvDao

@Database(
    entities = [
        WatchHistoryEntity::class,
        SavedLinkEntity::class,
        PlayerSettingsEntity::class,
        BookmarkEntity::class,
        BrowserHistoryEntity::class,
        DownloadItemEntity::class,
        IptvPlaylistEntity::class
    ],
    version = 17,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun savedLinkDao(): SavedLinkDao
    abstract fun playerSettingsDao(): PlayerSettingsDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun browserHistoryDao(): BrowserHistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun iptvDao(): IptvDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN adblockEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }
        
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN fastCacheMode INTEGER NOT NULL DEFAULT 2")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `browser_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `downloads` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `url` TEXT NOT NULL,
                        `localPath` TEXT NOT NULL,
                        `posterUrl` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `progress` INTEGER NOT NULL,
                        `downloadedBytes` INTEGER NOT NULL,
                        `totalBytes` INTEGER NOT NULL,
                        `speed` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `downloadId` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `errorMessage` TEXT
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN downloadDirectoryUri TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN popupBlockerEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN useCustomDns INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN customDnsUrl TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvMouseShortcutKeyCode INTEGER NOT NULL DEFAULT 82")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvMouseShortcutKeyName TEXT NOT NULL DEFAULT 'Menü Tuşu (MENU / ≡)'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvAutoEnableMouse INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvCursorSpeed REAL NOT NULL DEFAULT 28.0")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyPlayPause INTEGER NOT NULL DEFAULT 85")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyPlayPauseName TEXT NOT NULL DEFAULT 'Oynat / Duraklat (PLAY_PAUSE)'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyForward INTEGER NOT NULL DEFAULT 90")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyForwardName TEXT NOT NULL DEFAULT '10sn İleri Sar (FAST_FORWARD / >>)'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyRewind INTEGER NOT NULL DEFAULT 89")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyRewindName TEXT NOT NULL DEFAULT '10sn Geri Sar (REWIND / <<)'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyFullscreen INTEGER NOT NULL DEFAULT 186")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyFullscreenName TEXT NOT NULL DEFAULT 'Tam Ekran (Mavi Tuş / PROG_BLUE)'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeySubtitle INTEGER NOT NULL DEFAULT 175")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeySubtitleName TEXT NOT NULL DEFAULT 'Altyazı Aç/Kapat (CAPTIONS / CC)'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyAudioTrack INTEGER NOT NULL DEFAULT 185")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyAudioTrackName TEXT NOT NULL DEFAULT 'Ses Dili Değiştir (Sarı Tuş / PROG_YELLOW)'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyMute INTEGER NOT NULL DEFAULT 164")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyMuteName TEXT NOT NULL DEFAULT 'Sesi Kapat / Aç (MUTE)'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyNextVideo INTEGER NOT NULL DEFAULT 87")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyNextVideoName TEXT NOT NULL DEFAULT 'Sonraki Video (NEXT)'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyPrevVideo INTEGER NOT NULL DEFAULT 88")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyPrevVideoName TEXT NOT NULL DEFAULT 'Önceki Video (PREV)'")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyDpadUp INTEGER NOT NULL DEFAULT 19")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyDpadUpName TEXT NOT NULL DEFAULT 'Yukarı Tuşu (DPAD_UP)'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyDpadDown INTEGER NOT NULL DEFAULT 20")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyDpadDownName TEXT NOT NULL DEFAULT 'Aşağı Tuşu (DPAD_DOWN)'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyDpadLeft INTEGER NOT NULL DEFAULT 21")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyDpadLeftName TEXT NOT NULL DEFAULT 'Sol Tuşu (DPAD_LEFT)'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyDpadRight INTEGER NOT NULL DEFAULT 22")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyDpadRightName TEXT NOT NULL DEFAULT 'Sağ Tuşu (DPAD_RIGHT)'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyDpadCenter INTEGER NOT NULL DEFAULT 23")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyDpadCenterName TEXT NOT NULL DEFAULT 'Orta / OK Tuşu (DPAD_CENTER)'")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyScrollMode INTEGER NOT NULL DEFAULT 184")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyScrollModeName TEXT NOT NULL DEFAULT 'Sürükleme Modu Tuşu (Yeşil / GREEN)'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyPageUp INTEGER NOT NULL DEFAULT 92")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyPageUpName TEXT NOT NULL DEFAULT 'Sayfa Yukarı (PAGE_UP / CH+)'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyPageDown INTEGER NOT NULL DEFAULT 93")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tvKeyPageDownName TEXT NOT NULL DEFAULT 'Sayfa Aşağı (PAGE_DOWN / CH-)'")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `iptv_playlists` ADD COLUMN `isSingleChannel` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN defaultSearchEngine TEXT NOT NULL DEFAULT 'Google'")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `iptv_playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, `dateAdded` INTEGER NOT NULL)"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "streampulse_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
