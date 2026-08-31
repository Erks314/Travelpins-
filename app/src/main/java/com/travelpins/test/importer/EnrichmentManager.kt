package com.travelpins.test.importer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.travelpins.test.data.TravelPinsRepository
import com.travelpins.test.scraper.GoogleMapsScraperScript
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLDecoder

object EnrichmentManager {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    private const val WORKER_COUNT = 3
    private const val MAX_ATTEMPTS = 2
    private const val PLACE_TIMEOUT_MS = 15_000L
    private const val SUCCESS_DELAY_MS = 200L
    private const val FAIL_DELAY_MS = 500L

    private class Worker(val index: Int) {
        var webView: WebView? = null
        var currentPlaceId: Long? = null
        var currentDeferred: CompletableDeferred<Boolean>? = null
        var consentAttempted = false
        val attached = CompletableDeferred<WebView>()
    }

    private var started = false
    private var loopsStarted = false
    private var repository: TravelPinsRepository? = null

    private val scope = MainScope()
    private val workers = mutableListOf<Worker>()

    private val queueMutex = Mutex()
    private val queue = ArrayDeque<Long>()
    private val queued = mutableSetOf<Long>()
    private val inFlight = mutableSetOf<Long>()
    private val failed = mutableSetOf<Long>()
    private val attempts = mutableMapOf<Long, Int>()

    private var logCallback: ((String) -> Unit)? = null

    private fun log(message: String) {
        println("[EnrichmentManager] $message")
        logCallback?.invoke("[EM] $message")
    }

    fun setLogCallback(callback: (String) -> Unit) {
        logCallback = callback
    }

    suspend fun reset() {
        log("RESET: Pulizia stato per nuova importazione")
        queueMutex.withLock {
            queue.clear()
            queued.clear()
            inFlight.clear()
            failed.clear()
            attempts.clear()
        }

        // FIX: complete(false) invece di cancel() per non uccidere il worker loop
        workers.forEach { worker ->
            worker.currentPlaceId = null
            worker.currentDeferred?.complete(false)
            worker.currentDeferred = null
            worker.consentAttempted = false
        }
        log("RESET: Completato")
    }

    fun start(context: Context, repository: TravelPinsRepository) {
        if (started) return
        started = true
        this.repository = repository
        log("START: Avvio osservazione luoghi")

        scope.launch {
            repository.places.collect { places ->
                val pendingIds = places
                    .filter { it.detailsFetchedAt == null }
                    .map { it.id }
                    .toSet()

                var added = 0

                queueMutex.withLock {
                    for (id in pendingIds) {
                        if (id !in failed && id !in inFlight && queued.add(id)) {
                            queue.addLast(id)
                            added++
                        }
                    }
                    val iterator = queue.iterator()
                    while (iterator.hasNext()) {
                        val id = iterator.next()
                        if (id !in pendingIds && id !in inFlight) {
                            iterator.remove()
                            queued.remove(id)
                        }
                    }
                }
                if (added > 0) log("COLLECT: Aggiunti $added luoghi in coda. Totale: ${queue.size}")
                ensureWorkerLoops()
            }
        }
    }

    suspend fun prioritize(placeIds: List<Long>) {
        if (placeIds.isEmpty()) {
            log("PRIORITÀ: Lista vuota")
            return
        }
        log("PRIORITÀ: Richiesta per ${placeIds.size} luoghi")

        queueMutex.withLock {
            val iterator = queue.iterator()
            var removed = 0
            while (iterator.hasNext()) {
                val id = iterator.next()
                if (id in placeIds) {
                    iterator.remove()
                    queued.remove(id)
                    removed++
                }
            }
            if (removed > 0) log("PRIORITÀ: Rimossi $removed luoghi dalla coda esistente")

            placeIds.reversed().forEach { id ->
                failed.remove(id)
                attempts.remove(id)
                inFlight.remove(id)
                queued.remove(id)
                queued.add(id)
                queue.addFirst(id)
            }
            log("PRIORITÀ: ${placeIds.size} luoghi messi in cima alla coda. Totale coda: ${queue.size}")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(activity: Activity) {
        if (workers.isNotEmpty()) {
            log("ATTACH: Worker già presenti (${workers.size})")
            return
        }
        log("ATTACH: Creazione $WORKER_COUNT WebView")
        val repo = repository ?: TravelPinsRepository(activity.applicationContext)
        repository = repo

        repeat(WORKER_COUNT) { index ->
            val worker = Worker(index)
            val webView = WebView(activity).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = USER_AGENT
                alpha = 0f
            }

            val bridge = TravelPinsJsBridge(
                repository = repo, scope = scope,
                getCurrentSourceListId = { null }, getCurrentSourceListName = { null },
                onImportFinished = { }, onImportError = { },
                onLogMessage = { message -> log("[W${worker.index}] JS: $message") },
                getEnrichmentPlaceId = { worker.currentPlaceId },
                onDetailsFinished = { _, photos, reviews ->
                    log("[W${worker.index}] Dettagli salvati: foto=$photos recensioni=$reviews")
                    worker.currentDeferred?.complete(true)
                },
                onDetailsError = {
                    log("[W${worker.index}] Errore dettagli")
                    worker.currentDeferred?.complete(false)
                }
            )

            webView.addJavascriptInterface(bridge, TravelPinsJsBridge.NAME)
            webView.addJavascriptInterface(bridge, TravelPinsJsBridge.BRIDGE_NAME)

            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    log("[W${worker.index}] Pagina start: ${url.take(80)}")
                    view.evaluateJavascript(GoogleMapsScraperScript.NETWORK_HOOK_SCRIPT) { result ->
                        log("[W${worker.index}] Network hook installato: $result")
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    log("[W${worker.index}] Pagina finish: ${url.take(80)}")
                    if (url.contains("consent.google.com") && !worker.consentAttempted) {
                        worker.consentAttempted = true
                        view.postDelayed({
                            view.evaluateJavascript(GoogleMapsScraperScript.ACCEPT_CONSENT_SCRIPT) { result ->
                                log("[W${worker.index}] Consenso: $result")
                            }
                        }, 700)
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    if (url.startsWith("intent://")) {
                        extractFallbackUrl(url)?.let { fallback ->
                            log("[W${worker.index}] Intent fallback: ${fallback.take(80)}")
                            view.loadUrl(fallback)
                        }
                        return true
                    }
                    return false
                }
            }

            val parent = activity.window.decorView as? ViewGroup
            parent?.addView(webView, FrameLayout.LayoutParams(1, 1))
            worker.webView = webView
            worker.attached.complete(webView)
            workers.add(worker)
        }
        ensureWorkerLoops()
    }

    private fun ensureWorkerLoops() {
        if (loopsStarted || workers.isEmpty()) return
        loopsStarted = true
        log("WORKER_LOOPS: Avvio ${workers.size} loop")
        workers.forEach { worker -> scope.launch { workerLoop(worker) } }
    }

    private suspend fun workerLoop(worker: Worker) {
        log("[W${worker.index}] Loop avviato, attendo WebView...")
        val webView = withTimeoutOrNull(15_000L) { worker.attached.await() } ?: run {
            log("[W${worker.index}] WebView non disponibile dopo 15s")
            return
        }
        log("[W${worker.index}] WebView pronto, inizio polling coda")

        while (true) {
            val placeId = queueMutex.withLock { queue.firstOrNull() }
            if (placeId == null) {
                delay(100L)
                continue
            }
            queueMutex.withLock {
                queue.removeFirst()
                queued.remove(placeId)
            }

            val repo = repository ?: continue
            val place = repo.getPlaceById(placeId)
            if (place == null) {
                log("[W${worker.index}] Luogo $placeId non trovato nel DB")
                continue
            }
            if (place.detailsFetchedAt != null) {
                log("[W${worker.index}] Luogo ${place.name} già arricchito, skip")
                continue
            }

            queueMutex.withLock { inFlight.add(placeId) }
            worker.currentPlaceId = placeId
            worker.currentDeferred = CompletableDeferred()

            val url = place.mapsUrl ?: buildFallbackMapsUrl(place.latitude, place.longitude)
            log("[W${worker.index}] Start: ${place.name}")
            withContext(Dispatchers.Main) { webView.loadUrl(url) }

            val ok = withTimeoutOrNull(PLACE_TIMEOUT_MS) { worker.currentDeferred?.await() } ?: false
            queueMutex.withLock { inFlight.remove(placeId) }

            if (ok) {
                attempts.remove(placeId)
                log("[W${worker.index}] OK: ${place.name}")
                delay(SUCCESS_DELAY_MS)
            } else {
                val nextAttempt = (attempts[placeId] ?: 0) + 1
                attempts[placeId] = nextAttempt
                if (nextAttempt < MAX_ATTEMPTS) {
                    queueMutex.withLock {
                        if (queued.add(placeId)) queue.addLast(placeId)
                    }
                    log("[W${worker.index}] Timeout/Retry ${nextAttempt + 1}/$MAX_ATTEMPTS: ${place.name}")
                } else {
                    attempts.remove(placeId)
                    queueMutex.withLock { failed.add(placeId) }
                    log("[W${worker.index}] Skip dopo $MAX_ATTEMPTS tentativi: ${place.name}")
                }
                delay(FAIL_DELAY_MS)
            }
            worker.currentPlaceId = null
            worker.currentDeferred = null
        }
    }

    private fun buildFallbackMapsUrl(latitude: Double, longitude: Double): String =
        "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"

    private fun extractFallbackUrl(intentUrl: String): String? {
        return try {
            val marker = "S.browser_fallback_url="
            val start = intentUrl.indexOf(marker)
            if (start == -1) return null
            var value = intentUrl.substring(start + marker.length)
            val end = value.indexOf("#Intent")
            if (end != -1) value = value.substring(0, end)
            URLDecoder.decode(value, "UTF-8")
        } catch (_: Exception) { null }
    }
}
