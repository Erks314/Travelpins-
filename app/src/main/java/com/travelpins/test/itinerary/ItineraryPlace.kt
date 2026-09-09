package com.travelpins.test.itinerary

import com.travelpins.test.data.Category
import com.travelpins.test.data.Place

/**
 * Rappresentazione leggera di un luogo per l'itinerario.
 * Derivata da Place, contiene solo i campi necessari per il routing.
 */
data class ItineraryPlace(
    val placeId: Long,
    val name: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val category: Category? = null,
    val photoUrl: String? = null
) {
    companion object {
        fun fromPlace(place: Place, category: Category? = null, photoUrl: String? = null): ItineraryPlace {
            return ItineraryPlace(
                placeId = place.id,
                name = place.name,
                address = place.address,
                latitude = place.latitude,
                longitude = place.longitude,
                category = category,
                photoUrl = photoUrl
            )
        }
    }
}
