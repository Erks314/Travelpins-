package com.travelpins.test.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una singola recensione associata a un luogo.
 * Le recensioni vengono estratte dalla risposta di /maps/preview/place
 * e salvate in cache per evitare ripetuti fetch da Google Maps.
 */
@Entity(
    tableName = "place_reviews",
    foreignKeys = [
        ForeignKey(
            entity = Place::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("placeId")
    ]
)
data class PlaceReview(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val placeId: Long,

    val authorName: String? = null,
    val authorPhotoUrl: String? = null,

    // Valutazione in stelle (1-5)
    val rating: Int? = null,

    // Data relativa (es. "2 mesi fa", "un anno fa")
    val timeText: String? = null,

    // Testo della recensione
    val reviewText: String? = null,

    // Ordine di visualizzazione
    val position: Int = 0
)
