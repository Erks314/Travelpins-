package com.travelpins.test.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,
    val colorArgb: Int,
    val iconKey: String = "place",
    val sortOrder: Int = 0
)
