package com.travelpins.test.importer

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.travelpins.test.data.TravelPinsRepository
import com.travelpins.test.scraper.GoogleMapsScraperScript
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLDecoder

/**
 * Gestisce il prefetch automatico in background di tutti i luoghi.
 * Quando importi un elenco, questo manager arricchisce automaticamente
 * tutti i luoghi uno alla volta, così quando l'utente li apre i dati
 * sono già pronti (0 secondi di attesa).
 */
object EnrichmentManager {
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    data class EnrichStatus(val done: Int = 0, val total: Int = 0, val running: Boolean = false)
    
    private var started = false
    private var repository: TravelPinsRepository? = null
    private var appContext: Context? = null
    private var webView: WebView? = null
    private val scope = MainScope()
    private val queue = ArrayDeque<Long>()
    private val queued = mutableSetOf<Long>()
    private var processing = false
    private var currentId: Long? = null
    private var currentDeferred: CompletableDeferred<Boolean>? = null
    private var sessionReadyDeferred: CompletableDeferred<Boolean>? = null
    private var throttleMs = 1500L
    private var consentAttempted = false
    private var warmupDone = false
    private val _status = MutableStateFlow(EnrichStatus())
    val status: StateFlow<EnrichStatus> = _status
    
    private fun log(msg: String) {
        println("[EnrichmentManager] $msg")
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun start(context: Context, repository: TravelPinsRepository) {
        if (started) return
        started = true
        appContext = context.applicationContext
        this.repository = repository
        log("Avviato")

        scope.launch {
            repository.places.collect { places ->
                val pendingIds = places
                    .filter { it.detailsFetchedAt == null }
                    .map { it.id }
                    .toSet()
                
                var added = 0
                for (id in pendingIds) {
                    if (queued.add(id)) {
                        queue.addLast(id)
                        added++
                    }
                }
                
                // Rimuovi dalla coda quelli non più pending
                val it = queue.iterator()
                while (it.hasNext()) {
                    val id = it.next()
                    if (id !in pendingIds && id != currentId) {
                        it.remove()
                        queued.remove(id)
                    }
                }
                
                if (added > 0) {
                    log("+$added nuovi in coda, totale ${queue.size}")
                }
                
                _status.value = EnrichStatus(
                    done = places.size - pendingIds.size,
                    total = places.size,
                    running = processing
                )
                
                ensureProcessing()
            }
        }
    }

    private fun ensureProcessing() {
        if (processing) return
        processing = true
        scope.launch { processLoop() }
    }

    private suspend fun processLoop() {
        try {
            ensureWebView()
            val wv = webView ?: return
            
            // Warm-up iniziale (solo una volta)
            if (!warmupDone) {
                log("Warm-up iniziale (cookie Google)")
                warmupDone = true
                val deferred = CompletableDeferred<Boolean>()
                sessionReadyDeferred = deferred
                withContext(Dispatchers.Main) {
                    wv.loadUrl("https://www.google.com/maps")
                }
                withTimeoutOrNull(15000) { deferred.await() }
                log("Warm-up completato")
            }
            
            while (true) {
                val id = queue.firstOrNull() ?: break
                val repo = repository ?: break
                val place = repo.getPlaceById(id)
                
                // Skip se già arricchito o non trovato
                if (place == null || place.detailsFetchedAt != null) {
                    queue.removeFirst()
                    queued.remove(id)
                    continue
                }
                
                currentId = id
                val deferred = CompletableDeferred<Boolean>()
                currentDeferred = deferred
                
                val url = place.mapsUrl ?: "https://www.google.com/maps/search/?api=1&query=${place.latitude},${place.longitude}"
                log("→ ${place.name}")
                
                withContext(Dispatchers.Main) {
                    wv.loadUrl(url)
                }
                
                // Aspetta che l'hook catturi la risposta XHR
                val ok = withTimeoutOrNull(30000) { deferred.await() } ?: false
                
                if (ok) {
                    log("✓ ${place.name} salvato")
                } else {
                    log("✗ ${place.name} timeout/fallito")
                }
                
                queue.removeFirst()
                queued.remove(id)
                currentId = null
                currentDeferred = null
                
                // Throttle tra un place e l'altro (più lungo se fallito)
                throttleMs = if (ok) {
                    maxOf(1000, throttleMs - 200)
                } else {
                    minOf(5000, throttleMs + 1000)
                }
                delay(throttleMs)
            }
            
            log("Coda vuota, prefetch completato")
        } catch (e: Exception) {
            log("Errore loop: ${e.message}")
        } finally {
            processing = false
            _status.value = _status.value.copy(running = false)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView() {
        if (webView != null) return
        val ctx = appContext ?: return
        log("Creazione WebView dedicato per prefetch")
        
        val wv = WebView(ctx)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.userAgentString = USER_AGENT

        val bridge = TravelPinsJsBridge(
            repository = repository!!,
            scope = scope,
            getCurrentSourceListId = { null },
            getCurrentSourceListName = { null },
            onImportFinished = { },
            onImportError = { },
            onLogMessage = { /* silenzio per non spammare */ },
            getEnrichmentPlaceId = { currentId },
            onDetailsFinished = { _, photos, reviews ->
                log("  salvati: $photos foto, $reviews recensioni")
                currentDeferred?.complete(true)
            },
            onDetailsError = {
                log("  errore bridge parsing")
                currentDeferred?.complete(false)
            }
        )

        wv.addJavascriptInterface(bridge, TravelPinsJsBridge.NAME)
        wv.addJavascriptInterface(bridge, TravelPinsJsBridge.BRIDGE_NAME)

        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                
                // Segnala che il warm-up è completato
                if (url.contains("google.com/maps") && !url.contains("/place/") && !url.contains("cid=")) {
                    sessionReadyDeferred?.complete(true)
                }
                
                // Installa hook su ogni pagina
                view.evaluateJavascript(GoogleMapsScraperScript.NETWORK_HOOK_SCRIPT, null)
                
                // Accetta consenso se necessario
                if (url.contains("consent.google.com") && !consentAttempted) {
                    consentAttempted = true
                    log("Consenso Google rilevato")
                    view.postDelayed({
                        view.evaluateJavascript(GoogleMapsScraperScript.ACCEPT_CONSENT_SCRIPT, null)
                    }, 700)
                }
            }
            
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("intent://")) {
                    extractFallbackUrl(url)?.let { view.loadUrl(it) }
                    return true
                }
                return false
            }
        }
        
        webView = wv
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
}
