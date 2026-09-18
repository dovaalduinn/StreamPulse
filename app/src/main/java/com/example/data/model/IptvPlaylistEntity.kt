package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "iptv_playlists")
data class IptvPlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val url: String,
    val dateAdded: Long = System.currentTimeMillis(),
    val isSingleChannel: Boolean = false
)
