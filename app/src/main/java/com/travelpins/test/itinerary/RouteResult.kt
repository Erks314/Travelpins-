package com.travelpins.test.itinerary

/**
 * Risultato del calcolo del percorso da Google Routes API.
 */
data class ItineraryLeg(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val startPlace: ItineraryPlace,
    val endPlace: ItineraryPlace
) {
    val distanceKm: Double get() = distanceMeters / 1000.0
    val durationMinutes: Int get() = durationSeconds / 60
}

data class ItineraryRoute(
    val totalDistanceMeters: Int,
    val totalDurationSeconds: Int,
    val legs: List<ItineraryLeg>,
    val polylineEncoded: String? = null
) {
    val totalDistanceKm: Double get() = totalDistanceMeters / 1000.0
    val totalDurationMinutes: Int get() = totalDurationSeconds / 60
}
