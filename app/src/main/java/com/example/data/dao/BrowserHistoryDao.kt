package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.BrowserHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserHistoryDao {
    @Query("SELECT * FROM browser_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<BrowserHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: BrowserHistoryEntity)

    @Delete
    suspend fun deleteHistory(history: BrowserHistoryEntity)

    @Query("DELETE FROM browser_history WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM browser_history")
    suspend fun clearHistory()
}
