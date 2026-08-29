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

    private var started = false
    private var repository: TravelPinsRepository? = null
    private var webView: WebView? = null
    private val attachDeferred = CompletableDeferred<WebView>()
    private val scope = MainScope()
    private val queue = ArrayDeque<Long>()
    private val queued = mutableSetOf<Long>()
    private val failed = mutableSetOf<Long>()
    private val attempts = mutableMapOf<Long, Int>()
    private var processing = false
    private var currentId: Long? = null
    private var currentDeferred: CompletableDeferred<Boolean>? = null
    private var sessionReadyDeferred: CompletableDeferred<Boolean>? = null
    private var throttleMs = 1500L
    private var consentAttempted = false
    private var warmupDone = false

    private fun log(msg: String) {
        println("[EnrichmentManager] $msg")
    }

    fun start(context: Context, repository: TravelPinsRepository) {
        if (started) return
        started = true
        this.repository = repository
        log("Avviato")

        scope.launch {
            repository.places.collect { places ->
                val pending = places.filter { it.detailsFetchedAt == null }.map { it.id }.toSet()

                var added = 0
                for (id in pending) {
                    if (id !in failed && queued.add(id)) {
                        queue.addLast(id)
                        added++
                    }
                }

                val it = queue.iterator()
                while (it.hasNext()) {
                    val id = it.next()
                    if (id !in pending && id != currentId) {
                        it.remove()
                        queued.remove(id)
                    }
                }

                if (added > 0) log("+$added in coda, totale ${queue.size}")
                ensureProcessing()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(activity: Activity) {
        if (webView != null) return
        log("Attach WebView alla finestra")

        val repo = repository ?: TravelPinsRepository(activity.applicationContext)
        if (repository == null) repository = repo

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
            getEnrichmentPlaceId = { currentId },
            onDetailsFinished = { _, photos, reviews ->
                log("  salvati foto=$photos rec=$reviews")
                currentDeferred?.complete(true)
            },
            onDetailsError = {
                currentDeferred?.complete(false)
            }
        )

        wv.addJavascriptInterface(bridge, TravelPinsJsBridge.NAME)
        wv.addJavascriptInterface(bridge, TravelPinsJsBridge.BRIDGE_NAME)

        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                sessionReadyDeferred?.complete(true)
                view.evaluateJavascript(GoogleMapsScraperScript.NETWORK_HOOK_SCRIPT, null)
                if (url.contains("consent.google.com") && !consentAttempted) {
                    consentAttempted = true
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
        webView = wv
        attachDeferred.complete(wv)
        ensureProcessing()
    }

    private fun ensureProcessing() {
        if (processing) return
        processing = true
        scope.launch { processLoop() }
    }

    private suspend fun processLoop() {
        try {
            val wv = withTimeoutOrNull(15000) { attachDeferred.await() }
            if (wv == null) {
                log("WebView non attachato, il loop partirà dopo attach()")
                return
            }

            if (!warmupDone) {
                warmupDone = true
                val deferred = CompletableDeferred<Boolean>()
                sessionReadyDeferred = deferred
                log("Warm-up iniziale (cookie Google)")
                withContext(Dispatchers.Main) { wv.loadUrl("https://www.google.com/maps") }
                withTimeoutOrNull(15000) { deferred.await() }
                sessionReadyDeferred = null
                log("Warm-up completato")
            }

            while (true) {
                val id = queue.firstOrNull() ?: break
                val repo = repository ?: break
                val place = repo.getPlaceById(id)

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

                withContext(Dispatchers.Main) { wv.loadUrl(url) }

                val ok = withTimeoutOrNull(30000) { deferred.await() } ?: false

                queue.removeFirst()

                if (ok) {
                    attempts.remove(id)
                    queued.remove(id)
                    log("✓ ${place.name}")
                    throttleMs = maxOf(1000, throttleMs - 200)
                } else {
                    val n = (attempts[id] ?: 0) + 1
                    attempts[id] = n
                    if (n < MAX_ATTEMPTS) {
                        queued.remove(id)
                        queue.addLast(id)
                        queued.add(id)
                        log("✗ ${place.name} — ritento ($n/$MAX_ATTEMPTS)")
                    } else {
                        queued.remove(id)
                        failed.add(id)
                        attempts.remove(id)
                        log("✗ ${place.name} — max tentativi raggiunti, skip")
                    }
                    throttleMs = minOf(5000, throttleMs + 1000)
                }

                currentId = null
                currentDeferred = null
                delay(throttleMs)
            }

            log("Coda vuota, prefetch completato")
        } catch (e: Exception) {
            log("Errore loop: ${e.message}")
        } finally {
            processing = false
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
