package com.travelpins.test.importer

import android.util.Log
import android.webkit.JavascriptInterface
import com.travelpins.test.data.PlacePhoto
import com.travelpins.test.data.PlaceReview
import com.travelpins.test.data.TravelPinsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

class TravelPinsJsBridge(
    private val repository: TravelPinsRepository,
    private val scope: CoroutineScope,
    private val getCurrentSourceListId: () -> String?,
    private val getCurrentSourceListName: () -> String?,
    private val onImportFinished: (savedCount: Int) -> Unit,
    private val onImportError: (Throwable) -> Unit,
    private val onLogMessage: (String) -> Unit = {},
    private val getEnrichmentPlaceId: () -> Long? = { null },
    private val onDetailsFinished: (placeId: Long, photosSaved: Int, reviewsSaved: Int) -> Unit = { _, _, _ -> },
    private val onDetailsError: () -> Unit = {}
) {
    private var extractedListName: String? = null

    @JavascriptInterface
    fun log(message: String) {
        Log.d("TravelPins", message)
        onLogMessage(message)
    }

    @JavascriptInterface
    fun network(type: String, method: String, url: String, body: String) {
        Log.d("TravelPinsNetwork", "$type $method $url")
        onLogMessage("$type $method $url")
    }

    @JavascriptInterface
    fun onListTitleExtracted(title: String) {
        val cleanTitle = title.trim().replace(Regex("\\s+"), " ")
        if (cleanTitle.isNotBlank()) {
            extractedListName = cleanTitle
            onLogMessage("NOME LISTA: $cleanTitle")
        }
    }

    @JavascriptInterface
    fun onPlacesExtracted(rawJson: String) {
        scope.launch {
            try {
                val sourceListId = getCurrentSourceListId()
                val sourceListName = extractedListName ?: getCurrentSourceListName()
                val places = PlaceJsonParser.parse(json = rawJson, sourceListId = sourceListId, sourceListName = sourceListName)
                onLogMessage("LUOGHI PARSATI: ${places.size}")
                val saved = repository.saveImportedPlaces(places)
                onImportFinished(saved)
            } catch (t: Throwable) {
                Log.e("TravelPins", "Errore importazione", t)
                onImportError(t)
            }
        }
    }

    @JavascriptInterface
    fun onPlaceDetailsExtracted(rawJson: String) {
        scope.launch {
            try {
                val placeId = getEnrichmentPlaceId() ?: return@launch
                
                var cleanJson = rawJson
                if (cleanJson.startsWith(")]}'")) {
                    cleanJson = cleanJson.substring(4)
                    if (cleanJson.startsWith("\n")) cleanJson = cleanJson.substring(1)
                }
                
                val details = PlaceDetailsParser.parse(cleanJson)
                if (details == null) {
                    onLogMessage("⚠️ Parser ha restituito null")
                    onDetailsError()
                    return@launch
                }

                // FIX: per i luoghi "locality" (paesi/città) il JSON di Google ha una
                // struttura diversa e il parser può leggere come rating una coordinata
                // (longitudine/latitudine) e come recensioni un contatore sbagliato.
                val place = repository.getPlaceById(placeId)
                val safeRating = sanitizeRating(details.rating, place?.latitude, place?.longitude)
                val safeReviewCount = if (safeRating != null) details.reviewCount else null
                if (details.rating != null && safeRating == null) {
                    onLogMessage("⚠️ Rating sospetto scartato (${details.rating}): non è un voto Google valido")
                }

                onLogMessage("📋 DATI TROVATI DAL PARSER:")
                onLogMessage("  Nome: ${details.name}")
                onLogMessage("  Rating: $safeRating")
                onLogMessage("  Recensioni: $safeReviewCount")
                onLogMessage("  Sito: ${details.websiteUrl}")
                onLogMessage("  Tipi: ${details.types.joinToString(", ")}")
                onLogMessage("  Descrizione: ${details.description?.take(100)}...")
                onLogMessage("  Foto trovate: ${details.photos.size}")
                details.photos.take(3).forEachIndexed { index, photo ->
                    onLogMessage("    Foto $index: ${photo.url.take(80)}...")
                }
                onLogMessage("  Recensioni trovate: ${details.reviews.size}")
                details.reviews.take(2).forEachIndexed { index, review ->
                    onLogMessage("    Recensione $index: ${review.authorName} - ${review.rating}★ - ${review.reviewText?.take(50)}...")
                }

                var photosSaved = 0
                var reviewsSaved = 0

                if (details.photos.isNotEmpty()) {
                    val placePhotos = details.photos.mapIndexed { index, photoDto ->
                        PlacePhoto(placeId = placeId, photoKey = photoDto.key, imageUrl = photoDto.url, width = photoDto.width, height = photoDto.height, position = index)
                    }
                    photosSaved = repository.insertPhotos(placePhotos)
                }

                if (details.reviews.isNotEmpty()) {
                    val placeReviews = details.reviews.mapIndexed { index, reviewDto ->
                        PlaceReview(placeId = placeId, authorName = reviewDto.authorName, authorPhotoUrl = reviewDto.authorPhotoUrl, rating = reviewDto.rating, timeText = reviewDto.timeText, reviewText = reviewDto.reviewText, position = index)
                    }
                    reviewsSaved = repository.insertReviews(placeReviews)
                }

                repository.updatePlaceDetails(
                    placeId = placeId,
                    rating = safeRating,
                    reviewCount = safeReviewCount,
                    description = details.description,
                    websiteUrl = details.websiteUrl,
                    types = details.types.joinToString(","),
                    detailsFetchedAt = System.currentTimeMillis()
                )

                onLogMessage("✓ Dettagli salvati: $photosSaved foto, $reviewsSaved recensioni")
                onDetailsFinished(placeId, photosSaved, reviewsSaved)
            } catch (t: Throwable) {
                Log.e("TravelPins", "Errore parsing dettagli", t)
                onLogMessage("✗ Errore parsing: ${t.message}")
                onDetailsError()
            }
        }
    }

    // Un voto Google valido è tra 1 e 5, con al massimo 2 decimali,
    // e non deve coincidere con le coordinate del luogo.
    private fun sanitizeRating(rating: Double?, lat: Double?, lng: Double?): Double? {
        if (rating == null) return null
        if (rating < 1.0 || rating > 5.0) return null
        if (lng != null && abs(rating - lng) < 0.0001) return null
        if (lat != null && abs(rating - lat) < 0.0001) return null
        if (rating.toString().substringAfter('.', "").length > 2) return null
        return rating
    }

    companion object {
        const val NAME = "TravelPins"
        const val BRIDGE_NAME = "TravelPinsBridge"
    }
}
