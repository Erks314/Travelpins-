package com.travelpins.test.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.lifecycleScope
import com.travelpins.test.data.Place
import com.travelpins.test.data.TravelPinsRepository
import com.travelpins.test.importer.TravelPinsJsBridge
import com.travelpins.test.scraper.GoogleMapsScraperScript
import kotlinx.coroutines.launch
import java.net.URLDecoder

class PlaceDetailActivity : ComponentActivity() {
    
    enum class EnrichmentState { Idle, Loading, Done, Failed }

    companion object {
        const val EXTRA_PLACE_ID = "extra_place_id"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        
        fun newIntent(context: Context, placeId: Long): Intent = 
            Intent(context, PlaceDetailActivity::class.java).putExtra(EXTRA_PLACE_ID, placeId)
    }

    private lateinit var repository: TravelPinsRepository
    private val webViewState = mutableStateOf<WebView?>(null)
    private val enrichmentState = mutableStateOf(EnrichmentState.Idle)
    val debugMessages: SnapshotStateList<String> = mutableStateListOf()
    
    private var consentAttempted = false
    private var enrichmentStarted = false
    private var currentPlaceId: Long = -1L
    private var warmupDone = false

    private fun addDebug(msg: String) {
        if (debugMessages.size > 50) debugMessages.removeAt(0)
        debugMessages.add(msg)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = TravelPinsRepository(applicationContext)
        
        val placeId = intent.getLongExtra(EXTRA_PLACE_ID, -1L)
        if (placeId == -1L) { 
            finish()
            return 
        }
        currentPlaceId = placeId

        setContent {
            TravelPinsDarkTheme {
                PlaceDetailRoot(
                    repository = repository, 
                    placeId = placeId, 
                    webViewState = webViewState, 
                    enrichmentState = enrichmentState.value,
                    debugMessages = debugMessages,
                    onBack = { finish() },
                    onStartEnrichmentIfNeeded = { place -> startEnrichmentIfNeeded(place) },
                    onForceRefresh = { place -> forceRefresh(place) },
                    onShare = { place -> sharePlace(place) },
                    onOpenGoogleMaps = { place -> openInGoogleMaps(place) },
                    onDelete = { place -> deletePlace(place) },
                    onAssignCategory = { pid, cid -> 
                        lifecycleScope.launch { repository.assignPlaceToCategory(pid, cid) } 
                    },
                    onCreateCategory = { name, color, icon -> 
                        lifecycleScope.launch { repository.createCategory(name, color, icon) } 
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        webViewState.value?.stopLoading()
        webViewState.value?.destroy()
        webViewState.value = null
        super.onDestroy()
    }

    private fun startEnrichmentIfNeeded(place: Place) {
        if (enrichmentStarted) return
        enrichmentStarted = true
        enrichmentState.value = EnrichmentState.Loading
        ensureWebView(place, reload = false)
    }

    private fun forceRefresh(place: Place) {
        enrichmentStarted = true
        enrichmentState.value = EnrichmentState.Loading
        ensureWebView(place, reload = true)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(place: Place, reload: Boolean) {
        val url = place.mapsUrl ?: "https://www.google.com/maps/search/?api=1&query=${place.latitude},${place.longitude}"
        
        val existing = webViewState.value
        if (existing != null) {
            if (reload) { 
                consentAttempted = false
                addDebug("Forzo ricarica: $url")
                existing.loadUrl(url)
                scheduleTimeout(existing) 
            }
            return
        }

        addDebug("Creo WebView per arricchimento")
        val wv = WebView(this)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.userAgentString = USER_AGENT
        wv.alpha = 0f

        val bridge = TravelPinsJsBridge(
            repository = repository, 
            scope = lifecycleScope, 
            getCurrentSourceListId = { null }, 
            getCurrentSourceListName = { null },
            onImportFinished = { }, 
            onImportError = { }, 
            onLogMessage = { msg -> addDebug(msg) },
            getEnrichmentPlaceId = { currentPlaceId },
            onDetailsFinished = { _, photos, reviews ->
                runOnUiThread {
                    addDebug("✓ Dettagli salvati: $photos foto, $reviews recensioni")
                    enrichmentState.value = EnrichmentState.Done
                    webViewState.value?.stopLoading()
                    Toast.makeText(this, "Dettagli aggiornati: $photos foto, $reviews recensioni", Toast.LENGTH_SHORT).show()
                }
            },
            onDetailsError = { 
                runOnUiThread {
                    addDebug("✗ Errore durante il parsing dei dettagli")
                    enrichmentState.value = EnrichmentState.Failed 
                }
            }
        )

        wv.addJavascriptInterface(bridge, TravelPinsJsBridge.NAME)
        wv.addJavascriptInterface(bridge, TravelPinsJsBridge.BRIDGE_NAME)

        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, pageUrl: String) {
                super.onPageFinished(view, pageUrl)
                addDebug("Pagina caricata: $pageUrl")
                
                // INSTALLA L'HOOK IMMEDIATAMENTE (senza delay)
                view.evaluateJavascript(GoogleMapsScraperScript.NETWORK_HOOK_SCRIPT, null)
                
                if (pageUrl.contains("consent.google.com") && !consentAttempted) {
                    consentAttempted = true
                    addDebug("Consenso Google rilevato, accetto")
                    view.postDelayed({ 
                        view.evaluateJavascript(GoogleMapsScraperScript.ACCEPT_CONSENT_SCRIPT, null) 
                    }, 700)
                }
                // NIENTE ESTRATTAZIONE DOM - lasciamo che l'hook faccia il suo lavoro
            }
            
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val reqUrl = request.url.toString()
                if (reqUrl.startsWith("intent://")) { 
                    extractFallbackUrl(reqUrl)?.let { 
                        addDebug("Intent intercettato, fallback: $it")
                        view.loadUrl(it) 
                    }
                    return true 
                }
                return false
            }
        }

        webViewState.value = wv
        
        if (!warmupDone) {
            addDebug("Warm-up: carico Google Maps per ottenere i cookie")
            warmupDone = true
            wv.loadUrl("https://www.google.com/maps")
            wv.postDelayed({
                addDebug("Warm-up completato, carico pagina luogo: $url")
                wv.loadUrl(url)
                scheduleTimeout(wv)
            }, 2000)
        } else {
            addDebug("Carico direttamente: $url")
            wv.loadUrl(url)
            scheduleTimeout(wv)
        }
    }

    private fun scheduleTimeout(wv: WebView) {
        wv.postDelayed({ 
            if (enrichmentState.value == EnrichmentState.Loading) { 
                addDebug("⏱️ Timeout 30s raggiunto")
                enrichmentState.value = EnrichmentState.Failed
                wv.stopLoading() 
            } 
        }, 30000)
    }

    private fun extractFallbackUrl(intentUrl: String): String? {
        return try {
            val marker = "S.browser_fallback_url="
            val start = intentUrl.indexOf(marker)
            if (start == -1) return null
            var value = intentUrl.substring(start + marker.length)
            val end = value.indexOf("#Intent")
            if (end != -1) value = value.substring(0, end)
            URLDecoder.decode(value, "UTF-8")
        } catch (e: Exception) { 
            null 
        }
    }

    private fun sharePlace(place: Place) {
        val link = place.mapsUrl ?: "https://www.google.com/maps/search/?api=1&query=${place.latitude},${place.longitude}"
        val sendIntent = Intent(Intent.ACTION_SEND).apply { 
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${place.name}\n$link") 
        }
        startActivity(Intent.createChooser(sendIntent, "Condividi luogo"))
    }

    private fun openInGoogleMaps(place: Place) {
        val uri = if (!place.mapsUrl.isNullOrBlank()) {
            Uri.parse(place.mapsUrl)
        } else {
            Uri.parse("https://www.google.com/maps/search/?api=1&query=${place.latitude},${place.longitude}")
        }
        try { 
            startActivity(Intent(Intent.ACTION_VIEW, uri)) 
        } catch (e: Exception) { 
            Toast.makeText(this, "Impossibile aprire Google Maps.", Toast.LENGTH_SHORT).show() 
        }
    }

    private fun deletePlace(place: Place) {
        lifecycleScope.launch { 
            repository.deletePlace(place)
            runOnUiThread { finish() } 
        }
    }
}
