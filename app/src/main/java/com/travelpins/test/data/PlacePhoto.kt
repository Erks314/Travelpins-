package com.travelpins.test.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una singola foto associata a un luogo.
 * Le foto vengono estratte dalla risposta di /maps/preview/place
 * e salvate in cache per evitare ripetuti fetch da Google Maps.
 */
@Entity(
    tableName = "place_photos",
    foreignKeys = [
        ForeignKey(
            entity = Place::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("placeId"),
        Index(value = ["placeId", "photoKey"], unique = true)
    ]
)
data class PlacePhoto(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val placeId: Long,

    // ID univoco Google della foto (es. "CIHM0ogKEICAgIDx9LnxfQ")
    val photoKey: String,

    // URL diretto dell'immagine (lh3.googleusercontent.com)
    // Può essere modificato cambiando il suffisso dimensione
    val imageUrl: String,

    val width: Int? = null,
    val height: Int? = null,

    // Ordine di visualizzazione
    val position: Int = 0
)
