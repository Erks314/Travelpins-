package com.travelpins.test.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un singolo luogo importato da una lista di Google Maps.
 */
@Entity(
    tableName = "places",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("categoryId"),
        Index("sourceListId"),
        Index(
            value = ["sourceListId", "name", "latitude", "longitude"],
            unique = true
        )
    ]
)
data class Place(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,
    val address: String? = null,
    val latitude: Double,
    val longitude: Double,

    val placeId: String? = null,

    // Link diretto alla scheda Google Maps del luogo (con foto, recensioni,
    // orari). Costruito a partire dal "cid" trovato nella risposta di
    // Google. Se assente, si ripiega su una ricerca per coordinate.
    val mapsUrl: String? = null,

    val note: String? = null,

    val sourceListId: String? = null,
    val sourceListName: String? = null,

    val categoryId: Long? = null,

    val importedAt: Long = System.currentTimeMillis()
)
