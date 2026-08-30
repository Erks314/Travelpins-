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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLDecoder

object EnrichmentManager {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    private const val WORKER_COUNT = 3
    private const val MAX_ATTEMPTS = 2
    private const val PLACE_TIMEOUT_MS = 12_000L
    private const val SUCCESS_DELAY_MS = 300L
    private const val FAIL_DELAY_MS = 1_000L

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

    private val queue = ArrayDeque<Long>()
    private val queued = mutableSetOf<Long>()
    private val inFlight = mutableSetOf<Long>()
    private val failed = mutableSetOf<Long>()
    private val attempts = mutableMapOf<Long, Int>()

    private fun log(message: String) {
        println("[EnrichmentManager] $message")
    }

    fun start(context: Context, repository: TravelPinsRepository) {
        if (started) return

        started = true
        this.repository = repository

        log("Start enrichment manager con $WORKER_COUNT worker")

        scope.launch {
            repository.places.collect { places ->
                val pendingIds = places
                    .filter { it.detailsFetchedAt == null }
                    .map { it.id }
                    .toSet()

                var added = 0

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

                if (added > 0) {
                    log("Aggiunti $added luoghi in coda. Totale coda: ${queue.size}")
                }

                ensureWorkerLoops()
            }
        }
    }

    fun prioritize(placeIds: List<Long>) {
        val repo = repository ?: return
        scope.launch {
            val places = placeIds.mapNotNull { id -> repo.getPlaceById(id) }
            val pendingIds = places.filter { it.detailsFetchedAt == null }.map { it.id }.toSet()
            
            if (pendingIds.isEmpty()) {
                log("Nessun luogo da prioritizzare")
                return@launch
            }

            val iterator = queue.iterator()
            while (iterator.hasNext()) {
                val id = iterator.next()
                if (id in pendingIds) {
                    iterator.remove()
                    queued.remove(id)
                }
            }

            pendingIds.reversed().forEach { id ->
                failed.remove(id)
                attempts.remove(id)
                
                if (id !in inFlight && queued.add(id)) {
                    queue.addFirst(id)
                }
            }

            log("Prioritizzati ${pendingIds.size} luoghi. Totale coda: ${queue.size}")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(activity: Activity) {
        if (workers.isNotEmpty()) return

        log("Attach di $WORKER_COUNT WebView invisibili")

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
                repository = repo,
                scope = scope,
                getCurrentSourceListId = { null },
                getCurrentSourceListName = { null },
                onImportFinished = { },
                onImportError = { },
                onLogMessage = { },

                getEnrichmentPlaceId = { worker.currentPlaceId },

                onDetailsFinished = { _, photos, reviews ->
                    log("[W${worker.index}] dettagli salvati: foto=$photos recensioni=$reviews")
                    worker.currentDeferred?.complete(true)
                },

                onDetailsError = {
                    log("[W${worker.index}] errore dettagli")
                    worker.currentDeferred?.complete(false)
                }
            )

            webView.addJavascriptInterface(bridge, TravelPinsJsBridge.NAME)
            webView.addJavascriptInterface(bridge, TravelPinsJsBridge.BRIDGE_NAME)

            webView.webViewClient = object : WebViewClient() {

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)

                    view.evaluateJavascript(
                        GoogleMapsScraperScript.NETWORK_HOOK_SCRIPT,
                        null
                    )

                    if (url.contains("consent.google.com") && !worker.consentAttempted) {
                        worker.consentAttempted = true
                        view.postDelayed({
                            view.evaluateJavascript(
                                GoogleMapsScraperScript.ACCEPT_CONSENT_SCRIPT,
                                null
                            )
                        }, 700)
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url.toString()

                    if (url.startsWith("intent://")) {
                        extractFallbackUrl(url)?.let { fallback ->
                            view.loadUrl(fallback)
                        }
                        return true
                    }

                    return false
                }
            }

            val parent = activity.window.decorView as? ViewGroup
            parent?.addView(
                webView,
                FrameLayout.LayoutParams(1, 1)
            )

            worker.webView = webView
            worker.attached.complete(webView)
            workers.add(worker)
        }

        ensureWorkerLoops()
    }

    private fun ensureWorkerLoops() {
        if (loopsStarted) return
        if (workers.isEmpty()) return

        loopsStarted = true

        workers.forEach { worker ->
            scope.launch {
                workerLoop(worker)
            }
        }
    }

    private suspend fun workerLoop(worker: Worker) {
        val webView = withTimeoutOrNull(15_000L) {
            worker.attached.await()
        }

        if (webView == null) {
            log("[W${worker.index}] WebView non disponibile")
            return
        }

        while (true) {
            val placeId = queue.firstOrNull()

            if (placeId == null) {
                delay(700L)
                continue
            }

            queue.removeFirst()
            queued.remove(placeId)

            val repo = repository
            if (repo == null) {
                delay(1_000L)
                continue
            }

            val place = repo.getPlaceById(placeId)

            if (place == null || place.detailsFetchedAt != null) {
                continue
            }

            inFlight.add(placeId)

            worker.currentPlaceId = placeId
            worker.currentDeferred = CompletableDeferred()

            val url = place.mapsUrl ?: buildFallbackMapsUrl(
                latitude = place.latitude,
                longitude = place.longitude
            )

            log("[W${worker.index}] start: ${place.name}")

            withContext(Dispatchers.Main) {
                webView.loadUrl(url)
            }

            val ok = withTimeoutOrNull(PLACE_TIMEOUT_MS) {
                worker.currentDeferred?.await()
            } ?: false

            inFlight.remove(placeId)

            if (ok) {
                attempts.remove(placeId)
                log("[W${worker.index}] OK: ${place.name}")
                delay(SUCCESS_DELAY_MS)
            } else {
                val nextAttempt = (attempts[placeId] ?: 0) + 1
                attempts[placeId] = nextAttempt

                if (nextAttempt < MAX_ATTEMPTS) {
                    if (queued.add(placeId)) {
                        queue.addLast(placeId)
                    }
                    log("[W${worker.index}] retry ${nextAttempt + 1}/$MAX_ATTEMPTS: ${place.name}")
                } else {
                    attempts.remove(placeId)
                    failed.add(placeId)
                    log("[W${worker.index}] skip dopo $MAX_ATTEMPTS tentativi: ${place.name}")
                }

                delay(FAIL_DELAY_MS)
            }

            worker.currentPlaceId = null
            worker.currentDeferred = null
        }
    }

    private fun buildFallbackMapsUrl(latitude: Double, longitude: Double): String {
        return "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
    }

    private fun extractFallbackUrl(intentUrl: String): String? {
        return try {
            val marker = "S.browser_fallback_url="
            val start = intentUrl.indexOf(marker)

            if (start == -1) return null

            var value = intentUrl.substring(start + marker.length)

            val end = value.indexOf("#Intent")
            if (end != -1) {
                value = value.substring(0, end)
            }

            URLDecoder.decode(value, "UTF-8")
        } catch (_: Exception) {
            null
        }
    }
}
