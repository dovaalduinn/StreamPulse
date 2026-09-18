package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.SavedLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedLinkDao {
    @Query("SELECT * FROM saved_links ORDER BY addedTimestamp DESC")
    fun getAllLinks(): Flow<List<SavedLinkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: SavedLinkEntity)

    @Query("DELETE FROM saved_links WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM saved_links")
    suspend fun clearAll()
}
