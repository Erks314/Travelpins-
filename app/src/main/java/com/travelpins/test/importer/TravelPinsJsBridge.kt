package com.travelpins.test.importer

import android.util.Log
import android.webkit.JavascriptInterface
import androidx.lifecycle.LifecycleCoroutineScope
import com.travelpins.test.data.TravelPinsRepository
import kotlinx.coroutines.launch

class TravelPinsJsBridge(
    private val repository: TravelPinsRepository,
    private val scope: LifecycleCoroutineScope,
    private val getCurrentSourceListId: () -> String?,
    private val getCurrentSourceListName: () -> String?,
    private val onImportFinished: (savedCount: Int) -> Unit,
    private val onImportError: (Throwable) -> Unit,
    private val onLogMessage: (String) -> Unit = {}
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
                        rawJson = rawJson,
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

    companion object {

        const val NAME = "TravelPins"

        const val BRIDGE_NAME =
            "TravelPinsBridge"
    }
}
