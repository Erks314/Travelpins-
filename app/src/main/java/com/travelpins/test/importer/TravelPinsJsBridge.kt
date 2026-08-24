package com.travelpins.test.importer

import android.util.Log
import android.webkit.JavascriptInterface
import androidx.lifecycle.LifecycleCoroutineScope
import com.travelpins.test.data.TravelPinsRepository
import kotlinx.coroutines.launch
import org.json.JSONArray

class TravelPinsJsBridge(
    private val repository: TravelPinsRepository,
    private val scope: LifecycleCoroutineScope,
    private val getCurrentSourceListId: () -> String?,
    private val getCurrentSourceListName: () -> String?,
    private val onImportFinished: (savedCount: Int) -> Unit,
    private val onImportError: (Throwable) -> Unit,
    private val onLogMessage: (String) -> Unit = {}
) {

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
    }

    @JavascriptInterface
    fun onPlacesExtracted(
        rawJson: String
    ) {

        scope.launch {

            try {

                // ====================================================
                // DEBUG: controlliamo i dati ricevuti da Google Maps
                // ====================================================

                try {

                    val array =
                        JSONArray(rawJson)

                    var withPlaceId = 0
                    var withoutPlaceId = 0

                    appendDebugLog(
                        "JSON RICEVUTO: ${array.length()} LUOGHI"
                    )

                    val examples =
                        minOf(array.length(), 5)

                    for (i in 0 until array.length()) {

                        val obj =
                            array.getJSONObject(i)

                        val name =
                            obj.optString("name")

                        val placeId =
                            obj.optString("placeId")

                        val lat =
                            obj.optString("lat")

                        val lng =
                            obj.optString("lng")

                        if (placeId.isBlank()) {
                            withoutPlaceId++
                        } else {
                            withPlaceId++
                        }

                        if (i < examples) {

                            appendDebugLog(
                                "DEBUG LUOGO ${i + 1}: " +
                                        "name=$name | " +
                                        "placeId=$placeId | " +
                                        "lat=$lat | " +
                                        "lng=$lng"
                            )
                        }
                    }

                    appendDebugLog(
                        "PLACE ID PRESENTI: $withPlaceId"
                    )

                    appendDebugLog(
                        "PLACE ID ASSENTI: $withoutPlaceId"
                    )

                } catch (debugError: Throwable) {

                    appendDebugLog(
                        "ERRORE DEBUG JSON: " +
                                "${debugError.message}"
                    )
                }

                // ====================================================
                // IMPORTAZIONE NORMALE
                // ====================================================

                val sourceListId =
                    getCurrentSourceListId()

                val sourceListName =
                    getCurrentSourceListName()

                val places =
                    PlaceJsonParser.parse(
                        rawJson,
                        sourceListId,
                        sourceListName
                    )

                val saved =
                    repository.saveImportedPlaces(
                        places
                    )

                onImportFinished(saved)

            } catch (t: Throwable) {

                onImportError(t)
            }
        }
    }

    private fun appendDebugLog(
        message: String
    ) {

        Log.d(
            "TravelPinsDebug",
            message
        )

        onLogMessage(
            message
        )
    }

    companion object {

        const val NAME =
            "TravelPins"

        const val BRIDGE_NAME =
            "TravelPinsBridge"
    }
}
