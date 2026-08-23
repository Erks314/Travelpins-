package com.travelpins.test.importer

import android.util.Log
import android.webkit.JavascriptInterface
import androidx.lifecycle.LifecycleCoroutineScope
import com.travelpins.test.data.TravelPinsRepository
import kotlinx.coroutines.launch

/**
 * Bridge JS -> Kotlin, registrato con webView.addJavascriptInterface(bridge, "TravelPins").
 *
 * log(String) e network(String, String, String, String) esistevano già
 * nell'app funzionante (usate per il monitor/diagnostica testuale).
 * onPlacesExtracted(String) è l'unica aggiunta: riceve il JSON dei luoghi
 * da GoogleMapsScraperScript.GETLIST_SCRIPT e li salva nel database.
 */
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
                val sourceListId = getCurrentSourceListId() ?: "unknown"
                val sourceListName = getCurrentSourceListName()
                val places = PlaceJsonParser.parse(rawJson, sourceListId, sourceListName)
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
