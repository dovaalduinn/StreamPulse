package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_links")
data class SavedLinkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val category: String = "Direkt Link", // "Direkt Link", "M3U Çalma Listesi", "Canlı TV", "Film", "Dizi"
    val addedTimestamp: Long = System.currentTimeMillis(),
    val notes: String? = null
)
