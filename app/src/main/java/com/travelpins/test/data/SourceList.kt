package com.travelpins.test.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "source_lists")
data class SourceList(
    @PrimaryKey val id: String,
    val name: String? = null,
    val sourceUrl: String? = null,
    val coverUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val placeCount: Int = 0
)
