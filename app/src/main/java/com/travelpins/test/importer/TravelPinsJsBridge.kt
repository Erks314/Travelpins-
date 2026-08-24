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

    @JavascriptInterface
    fun log(message: String) {
        Log.d("TravelPins", message)
        onLogMessage(message)
    }

    @JavascriptInterface
    fun network(type: String, method: String, url: String, body: String) {
        Log.d("TravelPinsNetwork", "$type $method $url")
    }

    @JavascriptInterface
    fun onPlacesExtracted(rawJson: String) {
        scope.launch {
            try {
                val sourceListId = getCurrentSourceListId()
                val sourceListName = getCurrentSourceListName()

                val places = PlaceJsonParser.parse(
                    rawJson,
                    sourceListId,
                    sourceListName
                )

                val saved = repository.saveImportedPlaces(places)

                onImportFinished(saved)
            } catch (t: Throwable) {
                onImportError(t)
            }
        }
    }

    companion object {
        const val NAME = "TravelPins"
        const val BRIDGE_NAME = "TravelPinsBridge"
    }
}
