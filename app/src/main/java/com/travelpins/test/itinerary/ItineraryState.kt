package com.travelpins.test.itinerary

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stato globale dell'itinerario (in memoria, non persistente).
 * Singleton per condividere lo stato tra Builder, Order e Result screens.
 */
object ItineraryState {
    private val _places = MutableStateFlow<List<ItineraryPlace>>(emptyList())
    val places: StateFlow<List<ItineraryPlace>> = _places.asStateFlow()

    fun add(place: ItineraryPlace) {
        val current = _places.value.toMutableList()
        if (current.none { it.placeId == place.placeId }) {
            current.add(place)
            _places.value = current
        }
    }

    fun remove(placeId: Long) {
        _places.value = _places.value.filter { it.placeId != placeId }
    }

    fun reorder(fromIndex: Int, toIndex: Int) {
        val current = _places.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _places.value = current
        }
    }

    fun clear() {
        _places.value = emptyList()
    }

    fun contains(placeId: Long): Boolean = _places.value.any { it.placeId == placeId }

    fun size(): Int = _places.value.size
}
