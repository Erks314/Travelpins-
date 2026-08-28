package com.travelpins.test.importer

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.travelpins.test.data.Place
import com.travelpins.test.data.TravelPinsRepository
import com.travelpins.test.scraper.GoogleMapsScraperScript
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLDecoder
import kotlin.coroutines.resume

object EnrichmentManager {
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    private val HEX_PAIR_REGEX = Regex("^0x[0-9a-f]+:0x[0-9a-f]+$", RegexOption.IGNORE_CASE)

    data class EnrichStatus(val done: Int = 0, val total: Int = 0, val running: Boolean = false)
    val debugLog: SnapshotStateList<String> = mutableStateListOf()
    private fun addDebug(msg: String) { if (debugLog.size > 99) debugLog.removeAt(0); debugLog.add(msg) }

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
    private var sessionDeferred: CompletableDeferred<Boolean>? = null
    private var throttleMs = 800L
    private var consentAttempted = false
    private val _status = MutableStateFlow(EnrichStatus())
    val status: StateFlow<EnrichStatus> = _status

    @SuppressLint("SetJavaScriptEnabled")
    fun start(context: Context, repository: TravelPinsRepository) {
        if (started) return
        started = true
        appContext = context.applicationContext
        this.repository = repository
        addDebug("Manager avviato")

        val prefs = appContext!!.getSharedPreferences("travelpins", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("cleanup_v9_done", false)) {
            scope.launch {
                addDebug("Reset v9: ri-arricchimento completo")
                repository.resetAllDetailsFetched()
                prefs.edit().putBoolean("cleanup_v9_done", true).apply()
            }
        }

        scope.launch {
            repository.places.collect { places ->
                val pendingIds = places.filter { it.detailsFetchedAt == null }.map { it.id }.toSet()
                var added = 0
                for (id in pendingIds) { if (queued.add(id)) { queue.addLast(id); added++ } }
                val it = queue.iterator()
                while (it.hasNext()) {
                    val id = it.next()
                    if (id !in pendingIds && id != currentId) { it.remove(); queued.remove(id) }
                }
                if (added > 0) addDebug("Coda: +$added nuovi, totale ${queue.size}")
                _status.value = EnrichStatus(done = places.size - pendingIds.size, total = places.size, running = processing)
                ensureProcessing()
            }
        }
    }

    fun prioritize(placeId: Long, force: Boolean = false) {
        scope.launch {
            val repo = repository ?: return@launch
            if (force) { repo.clearDetailsFetched(placeId); addDebug("Forzato re-arricchimento placeId=$placeId") }
            queue.remove(placeId); queue.addFirst(placeId); queued.add(placeId)
            addDebug("Priorità a placeId=$placeId (coda=${queue.size})")
            ensureProcessing()
        }
    }

    private fun ensureProcessing() {
        if (processing) return
        processing = true
        scope.launch { loop() }
    }

    private suspend fun loop() {
        try {
            while (true) {
                val id = queue.firstOrNull() ?: break
                val repo = repository ?: break
                val place = repo.getPlaceById(id)
                if (place == null || place.detailsFetchedAt != null) { queue.removeFirst(); queued.remove(id); continue }

                currentId = id
                val deferred = CompletableDeferred<Boolean>()
                currentDeferred = deferred
                ensureWebView()
                val wv = webView ?: break
                waitSessionReady(wv)

                val ref = place.mapsPlaceRef ?: place.placeId?.takeIf { HEX_PAIR_REGEX.matches(it) }
                addDebug("→ ${place.name} (ref=${ref != null})")

                var ok = false
                if (!ref.isNullOrBlank()) {
                    val query = place.name + (place.address?.let { ", $it" } ?: "")
                    val script = GoogleMapsScraperScript.detailsFetchScript(query = query, ref = ref, lat = place.latitude, lng = place.longitude)
                    val result = withContext(Dispatchers.Main) {
                        suspendCancellableCoroutine<String> { cont -> wv.evaluateJavascript(script) { r -> cont.resume(r ?: "") } }
                    }
                    val clean = result.trim().trim('"')
                    addDebug("  fetch diretto: $clean")
                    if (clean.startsWith("OK")) ok = withTimeoutOrNull(8000) { deferred.await() } ?: false
                }

                if (!ok) {
                    addDebug("  fallback pagina: ${place.name}")
                    val url = place.mapsUrl ?: "https://www.google.com/maps/search/?api=1&query=${place.latitude},${place.longitude}"
                    withContext(Dispatchers.Main) { wv.loadUrl(url) }
                    
                    // Aspetta che la pagina sia caricata, poi estrai dal DOM dopo 5 secondi
                    withContext(Dispatchers.Main) {
                        wv.postDelayed({
                            addDebug("  Estrazione DOM per ${place.name}")
                            wv.evaluateJavascript(GoogleMapsScraperScript.EXTRACT_FROM_DOM_SCRIPT) { res ->
                                addDebug("  DOM result: $res")
                            }
                        }, 5000)
                    }
                    
                    ok = withTimeoutOrNull(15000) { deferred.await() } ?: false
                    if (!ok) addDebug("  fallback timeout: ${place.name}")
                }

                if (ok) addDebug("✓ ${place.name} salvato") else addDebug("✗ ${place.name} fallito")

                queue.removeFirst(); queued.remove(id); currentId = null; currentDeferred = null
                throttleMs = if (ok) maxOf(800, throttleMs - 200) else minOf(5000, throttleMs + 800)
                delay(throttleMs)
            }
            addDebug("Coda vuota, pausa")
        } finally { processing = false; _status.value = _status.value.copy(running = false) }
    }

    private suspend fun waitSessionReady(wv: WebView) {
        val sd = sessionDeferred
        if (sd != null) { withTimeoutOrNull(10000) { sd.await() }; return }
        val d = CompletableDeferred<Boolean>()
        sessionDeferred = d
        withContext(Dispatchers.Main) { wv.loadUrl("https://www.google.com/maps") }
        withTimeoutOrNull(10000) { d.await() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView() {
        if (webView != null) return
        val ctx = appContext ?: return
        addDebug("Creazione WebView dedicato")
        val wv = WebView(ctx)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.userAgentString = USER_AGENT

        val bridge = TravelPinsJsBridge(
            repository = repository!!, scope = scope, getCurrentSourceListId = { null }, getCurrentSourceListName = { null },
            onImportFinished = { }, onImportError = { }, onLogMessage = { msg -> addDebug(msg) },
            getEnrichmentPlaceId = { currentId },
            onDetailsFinished = { _, photos, reviews ->
                addDebug("  salvati foto=$photos recensioni=$reviews")
                currentDeferred?.complete(true)
            },
            onDetailsError = {
                addDebug("  errore bridge durante il parsing")
                currentDeferred?.complete(false)
            }
        )

        wv.addJavascriptInterface(bridge, TravelPinsJsBridge.NAME)
        wv.addJavascriptInterface(bridge, TravelPinsJsBridge.BRIDGE_NAME)

        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                sessionDeferred?.complete(true)
                view.evaluateJavascript(GoogleMapsScraperScript.NETWORK_HOOK_SCRIPT, null)
                if (url.contains("consent.google.com") && !consentAttempted) {
                    consentAttempted = true
                    addDebug("Consenso Google rilevato, accetto")
                    view.postDelayed({ view.evaluateJavascript(GoogleMapsScraperScript.ACCEPT_CONSENT_SCRIPT, null) }, 700)
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
        } catch (e: Exception) { null }
    }
}
