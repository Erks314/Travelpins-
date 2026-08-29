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
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    private const val MAX_ATTEMPTS = 2
    private const val WORKER_COUNT = 2

    private class Worker(val index: Int) {
        var webView: WebView? = null
        var currentId: Long? = null
        var currentDeferred: CompletableDeferred<Boolean>? = null
        var consentAttempted = false
        val attached = CompletableDeferred<WebView>()
        val pageLoaded = CompletableDeferred<Boolean>()
    }

    private var started = false
    private var repository: TravelPinsRepository? = null
    private val workers = mutableListOf<Worker>()
    private val scope = MainScope()
    private val queue = ArrayDeque<Long>()
    private val queued = mutableSetOf<Long>()
    private val failed = mutableSetOf<Long>()
    private val inFlight = mutableSetOf<Long>()
    private val attempts = mutableMapOf<Long, Int>()
    private var loopsStarted = false

    private fun log(msg: String) {
        println("[EnrichmentManager] $msg")
    }

    fun start(context: Context, repository: TravelPinsRepository) {
        if (started) return
        started = true
        this.repository = repository
        log("Avviato con $WORKER_COUNT worker")

        scope.launch {
            repository.places.collect { places ->
                val pending = places.filter { it.detailsFetchedAt == null }.map { it.id }.toSet()

                var added = 0
                for (id in pending) {
                    if (id !in failed && id !in inFlight && queued.add(id)) {
                        queue.addLast(id)
                        added++
                    }
                }

                val it = queue.iterator()
                while (it.hasNext()) {
                    val id = it.next()
                    if (id !in pending && id !in inFlight) {
                        it.remove()
                        queued.remove(id)
                    }
                }

                if (added > 0) log("+$added in coda, totale ${queue.size}")
                ensureLoops()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(activity: Activity) {
        if (workers.isNotEmpty()) return
        log("Attach di $WORKER_COUNT WebView alla finestra")

        val repo = repository ?: TravelPinsRepository(activity.applicationContext)
        if (repository == null) repository = repo

        repeat(WORKER_COUNT) { i ->
            val worker = Worker(i)
            val wv = WebView(activity)
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.userAgentString = USER_AGENT
            wv.alpha = 0f

            val bridge = TravelPinsJsBridge(
                repository = repo,
                scope = scope,
                getCurrentSourceListId = { null },
                getCurrentSourceListName = { null },
                onImportFinished = { },
                onImportError = { },
                onLogMessage = { },
                getEnrichmentPlaceId = { worker.currentId },
                onDetailsFinished = { _, photos, reviews ->
                    log("  [W${worker.index}] salvati foto=$photos rec=$reviews")
                    worker.currentDeferred?.complete(true)
                },
                onDetailsError = {
                    worker.currentDeferred?.complete(false)
                }
            )

            wv.addJavascriptInterface(bridge, TravelPinsJsBridge.NAME)
            wv.addJavascriptInterface(bridge, TravelPinsJsBridge.BRIDGE_NAME)

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    worker.pageLoaded.complete(true)
                    view.evaluateJavascript(GoogleMapsScraperScript.NETWORK_HOOK_SCRIPT, null)
                    if (url.contains("consent.google.com") && !worker.consentAttempted) {
                        worker.consentAttempted = true
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

            (activity.window.decorView as? ViewGroup)?.addView(wv, FrameLayout.LayoutParams(1, 1))
            worker.webView = wv
            worker.attached.complete(wv)
            workers.add(worker)
        }

        scope.launch {
            val w0 = workers[0]
            withContext(Dispatchers.Main) { w0.webView?.loadUrl("https://www.google.com/maps") }
            withTimeoutOrNull(15000) { w0.pageLoaded.await() }
            log("Warm-up completato")
            ensureLoops()
        }
    }

    private fun ensureLoops() {
        if (loopsStarted || workers.isEmpty()) return
        loopsStarted = true
        workers.forEach { worker ->
            scope.launch { workerLoop(worker) }
        }
    }

    private suspend fun workerLoop(worker: Worker) {
        val wv = withTimeoutOrNull(15000) { worker.attached.await() }
        if (wv == null) {
            log("[W${worker.index}] WebView non disponibile")
            return
        }

        while (true) {
            val id = queue.firstOrNull()
            if (id == null) {
                delay(1000)
                continue
            }

            queue.removeFirst()
            queued.remove(id)

            val repo = repository ?: continue
            val place = repo.getPlaceById(id)
            if (place == null || place.detailsFetchedAt != null) continue

            inFlight.add(id)
            worker.currentId = id
            val deferred = CompletableDeferred<Boolean>()
            worker.currentDeferred = deferred

            val url = place.mapsUrl ?: "https://www.google.com/maps/search/?api=1&query=${place.latitude},${place.longitude}"
            log("[W${worker.index}] → ${place.name}")

            withContext(Dispatchers.Main) { wv.loadUrl(url) }

            val ok = withTimeoutOrNull(30000) { deferred.await() } ?: false

            if (ok) {
                attempts.remove(id)
                inFlight.remove(id)
                log("[W${worker.index}] ✓ ${place.name}")
                delay(800)
            } else {
                inFlight.remove(id)
                val n = (attempts[id] ?: 0) + 1
                attempts[id] = n
                if (n < MAX_ATTEMPTS) {
                    queue.addLast(id)
                    queued.add(id)
                    log("[W${worker.index}] ↻ ${place.name} (tentativo $n)")
                } else {
                    attempts.remove(id)
                    failed.add(id)
                    log("[W${worker.index}] ✗ ${place.name} — skip")
                }
                delay(2000)
            }

            worker.currentId = null
            worker.currentDeferred = null
        }
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
