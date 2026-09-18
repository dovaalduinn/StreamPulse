package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history WHERE streamType != 'M3U_STREAM' AND streamType != 'LIVE' ORDER BY lastWatchedTimestamp DESC")
    fun getAllHistory(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE streamType != 'M3U_STREAM' AND streamType != 'LIVE'")
    suspend fun getAllHistoryList(): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE url = :url LIMIT 1")
    suspend fun getHistoryByUrl(url: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM watch_history WHERE streamType = 'M3U_STREAM' OR streamType = 'LIVE'")
    suspend fun deleteLiveStreams()

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()
}
