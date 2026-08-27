package com.travelpins.test.importer

import android.util.Log
import android.webkit.JavascriptInterface
import com.travelpins.test.data.PlacePhoto
import com.travelpins.test.data.PlaceReview
import com.travelpins.test.data.TravelPinsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
    private val onDetailsError: (Throwable) -> Unit = {}
) {

    private var extractedListName: String? = null

    @JavascriptInterface
    fun log(message: String) {
        Log.d("TravelPins", message)
        onLogMessage(message)
    }

    @JavascriptInterface
    fun network(
        type: String,
        method: String,
        url: String,
        body: String
    ) {
        Log.d(
            "TravelPinsNetwork",
            "$type $method $url"
        )

        onLogMessage(
            "$type $method $url"
        )
    }

    @JavascriptInterface
    fun onListTitleExtracted(title: String) {

        val cleanTitle =
            title
                .trim()
                .replace(Regex("\\s+"), " ")

        if (cleanTitle.isNotBlank()) {

            extractedListName = cleanTitle

            Log.d(
                "TravelPins",
                "NOME LISTA RICEVUTO: $cleanTitle"
            )

            onLogMessage(
                "NOME LISTA: $cleanTitle"
            )
        }
    }

    @JavascriptInterface
    fun onPlacesExtracted(rawJson: String) {

        scope.launch {

            try {

                val sourceListId =
                    getCurrentSourceListId()

                val sourceListName =
                    extractedListName
                        ?: getCurrentSourceListName()

                val places =
                    PlaceJsonParser.parse(
                        json = rawJson,
                        sourceListId = sourceListId,
                        sourceListName = sourceListName
                    )

                onLogMessage(
                    "LUOGHI PARSATI: ${places.size}"
                )

                onLogMessage(
                    "ELENCO: ${sourceListName ?: "Senza nome"}"
                )

                val saved =
                    repository.saveImportedPlaces(
                        places
                    )

                onImportFinished(saved)

            } catch (t: Throwable) {

                Log.e(
                    "TravelPins",
                    "Errore importazione",
                    t
                )

                onImportError(t)
            }
        }
    }

    @JavascriptInterface
    fun onPlaceDetailsExtracted(rawJson: String) {

        scope.launch {

            try {

                val placeId = getEnrichmentPlaceId()

                if (placeId == null) {
                    onLogMessage(
                        "DETAILS RICEVUTI MA NESSUN ARRICCHIMENTO ATTIVO"
                    )
                    return@launch
                }

                val details =
                    PlaceDetailsParser.parse(rawJson)

                if (details == null) {
                    onLogMessage("DETAILS PARSE FALLITO")
                    return@launch
                }

                onLogMessage(
                    "DETAILS: foto=${details.photos.size} " +
                        "recensioni=${details.reviews.size} " +
                        "rating=${details.rating}"
                )

                repository.savePlaceDetails(
                    placeId = placeId,
                    rating = details.rating,
                    reviewCount = details.reviewCount,
                    description = details.description,
                    websiteUrl = details.websiteUrl,
                    types = details.types.joinToString(","),
                    mapsPlaceRef = details.ref
                )

                val photos = details.photos.mapIndexed { index, p ->
                    PlacePhoto(
                        placeId = placeId,
                        photoKey = p.key,
                        imageUrl = p.url,
                        width = p.width,
                        height = p.height,
                        position = index
                    )
                }

                val photosSaved =
                    repository.savePhotos(placeId, photos)

                val reviews = details.reviews.mapIndexed { index, r ->
                    PlaceReview(
                        placeId = placeId,
                        authorName = r.authorName,
                        authorPhotoUrl = r.authorPhotoUrl,
                        rating = r.rating,
                        timeText = r.timeText,
                        reviewText = r.reviewText,
                        position = index
                    )
                }

                val reviewsSaved =
                    repository.saveReviews(placeId, reviews)

                onDetailsFinished(
                    placeId,
                    photosSaved,
                    reviewsSaved
                )

            } catch (t: Throwable) {

                Log.e(
                    "TravelPins",
                    "Errore dettagli luogo",
                    t
                )

                onDetailsError(t)
            }
        }
    }

    companion object {

        const val NAME = "TravelPins"

        const val BRIDGE_NAME =
            "TravelPinsBridge"
    }
}
