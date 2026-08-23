package com.travelpins.test.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "places")
data class Place(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val googleMapsUrl: String?,
    val categoryId: Long? = null
)
