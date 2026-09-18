package com.example.data.dao

import androidx.room.*
import com.example.data.model.IptvPlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IptvDao {
    @Query("SELECT * FROM iptv_playlists ORDER BY dateAdded DESC")
    fun getAllPlaylists(): Flow<List<IptvPlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: IptvPlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: IptvPlaylistEntity)
}
