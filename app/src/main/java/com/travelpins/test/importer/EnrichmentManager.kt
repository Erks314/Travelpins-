package com.travelpins.test.importer

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.travelpins.test.data.Place
import com.travelpins.test.data.TravelPinsRepository
import com.travelpins.test.scraper.GoogleMapsScraperScript
import kotlinx.coroutines.CompletableDeferred
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

/**
 * Coda di arricchimento in background.
 *
 * - Un WebView dedicato (con sessione Google Maps) esegue per ogni luogo
 *   in coda un fetch diretto di /maps/preview/place (~1-2 s/luogo).
 * - Se il fetch diretto fallisce, fallback: caricamento della pagina
 *   del luogo (metodo vecchio, piu' lento ma collaudato).
 * - Throttle adattivo: aumenta le pause se arrivano errori.
 * - Il luogo aperto dall'utente salta in testa alla coda.
 */
object EnrichmentManager {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    data class EnrichStatus(
        val done: Int = 0,
        val total: Int = 0,
        val running: Boolean = false
    )

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

    private var throttleMs = 1200L
    private var consentAttempted = false
    private var sessionWarmed = false

    private val _status = MutableStateFlow(EnrichStatus())
    val status: StateFlow<EnrichStatus> = _status

    @SuppressLint("SetJavaScriptEnabled")
    fun start(context: Context, repository: TravelPinsRepository) {
        if (started) return
        started = true
        appContext = context.applicationContext
        this.repository = repository

        // Pulizia una-tantum: ri-arricchisce i luoghi importati
        // con le versioni precedenti (rimuove foto Street View
        // e dati parziali salvati in cache).
        val prefs = appContext!!.getSharedPreferences(
            "travelpins", Context.MODE_PRIVATE
        )
        if (!prefs.getBoolean("cleanup_v5_done", false)) {
            scope.launch {
                repository.resetAllDetailsFetched()
                prefs.edit().putBoolean("cleanup_v5_done", true).apply()
            }
        }

        scope.launch {
            repository.places.collect { places ->
                val pendingIds = places
                    .filter { it.detailsFetchedAt == null }
                    .map { it.id }
                    .toSet()

                for (id in pendingIds) {
                    if (queued.add(id)) {
                        queue.addLast(id)
                    }
                }

                val it = queue.iterator()
                while (it.hasNext()) {
                    val id = it.next()
                    if (id !in pendingIds && id != currentId) {
                        it.remove()
                        queued.remove(id)
                    }
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

    /**
     * Mette il luogo in testa alla coda.
     * Con force = true lo reimposta come "da arricchire"
     * (usato da "Aggiorna dati Google").
     */
    fun prioritize(placeId: Long, force: Boolean = false) {
        scope.launch {
            val repo = repository ?: return@launch
            if (force) {
                repo.clearDetailsFetched(placeId)
            }
            queue.remove(placeId)
            queue.addFirst(placeId)
            queued.add(placeId)
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
                if (place == null || place.detailsFetchedAt != null) {
                    queue.removeFirst()
                    queued.remove(id)
                    continue
                }

                currentId = id
                val deferred = CompletableDeferred<Boolean>()
                currentDeferred = deferred

                ensureWebView()
                val wv = webView ?: break

                if (!sessionWarmed) {
                    sessionWarmed = true
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        wv.loadUrl("https://www.google.com/maps")
                    }
                    delay(3500)
                }

                val ok = enrichPlace(wv, place, deferred)

                queue.removeFirst()
                queued.remove(id)
                currentId = null
                currentDeferred = null

                throttleMs = if (ok) {
                    maxOf(1000, throttleMs - 200)
                } else {
                    minOf(5000, throttleMs + 800)
                }

                _status.value = _status.value.copy(running = true)
                delay(throttleMs)
            }
        } finally {
            processing = false
            _status.value = _status.value.copy(running = false)
        }
    }

    private suspend fun enrichPlace(
        wv: WebView,
        place: Place,
        deferred: CompletableDeferred<Boolean>
    ): Boolean {

        var ok = false

        val ref = place.mapsPlaceRef
        if (!ref.isNullOrBlank()) {
            val query = place.name +
                (place.address?.let { ", $it" } ?: "")

            val script = GoogleMapsScraperScript.detailsFetchScript(
                query = query,
                ref = ref,
                lat = place.latitude,
                lng = place.longitude
            )

            val result = withContext(kotlinx.coroutines.Dispatchers.Main) {
                suspendCancellableCoroutine<String> { cont ->
                    wv.evaluateJavascript(script) { r ->
                        cont.resume(r ?: "")
                    }
                }
            }

            val clean = result.trim().trim('"')
            if (clean.startsWith("OK")) {
                ok = withTimeoutOrNull(10000) { deferred.await() } ?: false
            }
        }

        if (!ok) {
            // Fallback: caricamento pagina del luogo
            val url = place.mapsUrl
                ?: "https://www.google.com/maps/search/?api=1&query=" +
                    "${place.latitude},${place.longitude}"

            withContext(kotlinx.coroutines.Dispatchers.Main) {
                wv.loadUrl(url)
            }
            ok = withTimeoutOrNull(20000) { deferred.await() } ?: false
        }

        return ok
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView() {
        if (webView != null) return
        val ctx = appContext ?: return

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
            onLogMessage = { },
            getEnrichmentPlaceId = { currentId },
            onDetailsFinished = { _, _, _ ->
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

                view.evaluateJavascript(
                    GoogleMapsScraperScript.NETWORK_HOOK_SCRIPT,
                    null
                )

                if (url.contains("consent.google.com")) {
                    if (!consentAttempted) {
                        consentAttempted = true
                        view.postDelayed({
                            view.evaluateJavascript(
                                GoogleMapsScraperScript.ACCEPT_CONSENT_SCRIPT,
                                null
                            )
                        }, 700)
                    }
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
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
            if (end != -1) {
                value = value.substring(0, end)
            }
            URLDecoder.decode(value, "UTF-8")
        } catch (e: Exception) {
            null
        }
    }
}
