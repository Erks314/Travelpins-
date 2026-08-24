package com.travelpins.test

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.travelpins.test.data.Place
import com.travelpins.test.data.TravelPinsRepository
import com.travelpins.test.importer.TravelPinsJsBridge
import com.travelpins.test.scraper.GoogleMapsScraperScript
import kotlinx.coroutines.launch
import java.net.URLDecoder

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var outputView: TextView

    private lateinit var repository: TravelPinsRepository

    private var currentListId: String? = null

    // Evita di eseguire due volte la stessa fase.
    private var consentAttempted = false
    private var scanStarted = false
    private var importStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = TravelPinsRepository(applicationContext)

        createWebView()

        showHome()

        handleIntent(intent)

        observePlaces()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Nuovo link condiviso = nuovo import.
        consentAttempted = false
        scanStarted = false
        importStarted = false
        currentListId = null

        handleIntent(intent)
    }

    override fun onDestroy() {

        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }

        super.onDestroy()
    }

    // ============================================================
    // HOME
    // ============================================================

    private fun showHome() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 24)
        }

        val title = TextView(this).apply {
            text = "TRAVELPINS"
            textSize = 28f
            setPadding(0, 0, 0, 8)
        }

        val subtitle = TextView(this).apply {
            text = "I miei luoghi"
            textSize = 18f
            setPadding(0, 0, 0, 20)
        }

        val countView = TextView(this).apply {
            tag = "place_count"
            text = "Luoghi salvati: 0"
            textSize = 16f
            setPadding(0, 0, 0, 16)
        }

        val importButton = Button(this).apply {

            text = "＋ IMPORTA DA GOOGLE MAPS"

            setOnClickListener {
                showImporter()
            }
        }

        val categoriesButton = Button(this).apply {

            text = "CATEGORIE"

            setOnClickListener {

                Toast.makeText(
                    this@MainActivity,
                    "Sezione categorie: prossimo step",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        root.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            subtitle,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            countView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            importButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            categoriesButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val placesScroll = ScrollView(this)

        val placesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "places_container"
        }

        placesScroll.addView(placesContainer)

        root.addView(
            placesScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
    }

    // ============================================================
    // DATABASE OBSERVER
    // ============================================================

    private fun observePlaces() {

        lifecycleScope.launch {

            repository.places.collect { places ->

                runOnUiThread {

                    val content =
                        findViewById<View>(android.R.id.content)

                    val countView =
                        content.findViewWithTag<TextView>(
                            "place_count"
                        )

                    countView?.text =
                        "Luoghi salvati: ${places.size}"

                    val container =
                        content.findViewWithTag<LinearLayout>(
                            "places_container"
                        )
                            ?: return@runOnUiThread

                    container.removeAllViews()

                    if (places.isEmpty()) {

                        val emptyView =
                            TextView(this@MainActivity).apply {

                                text =
                                    "\nNessun luogo ancora salvato.\n\n" +
                                    "Importa una lista da Google Maps."

                                textSize = 16f

                                setPadding(
                                    8,
                                    24,
                                    8,
                                    24
                                )
                            }

                        container.addView(emptyView)

                    } else {

                        places.forEach { place ->

                            container.addView(
                                createPlaceView(place)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun createPlaceView(
        place: Place
    ): View {

        val box = LinearLayout(this).apply {

            orientation =
                LinearLayout.VERTICAL

            setPadding(
                16,
                16,
                16,
                16
            )
        }

        val name = TextView(this).apply {

            text = place.name

            textSize = 18f

            setPadding(
                0,
                0,
                0,
                6
            )
        }

        box.addView(name)

        if (!place.address.isNullOrBlank()) {

            val address = TextView(this).apply {

                text = place.address

                textSize = 14f

                setPadding(
                    0,
                    0,
                    0,
                    6
                )
            }

            box.addView(address)
        }

        val coordinates = TextView(this).apply {

            text =
                "📍 ${place.latitude}, ${place.longitude}"

            textSize = 12f
        }

        box.addView(coordinates)

        if (place.categoryId != null) {

            val category = TextView(this).apply {

                text =
                    "Categoria ID: ${place.categoryId}"

                textSize = 12f

                setPadding(
                    0,
                    6,
                    0,
                    0
                )
            }

            box.addView(category)
        }

        val separator = View(this).apply {

            setBackgroundResource(
                android.R.color.darker_gray
            )
        }

        box.addView(
            separator,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1
            ).apply {

                topMargin = 12
                bottomMargin = 4
            }
        )

        return box
    }

    // ============================================================
    // IMPORTER
    // ============================================================

    private fun showImporter() {

        // Reset del ciclo dell'importazione.
        consentAttempted = false
        scanStarted = false
        importStarted = false
        currentListId = null

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val backButton = Button(this).apply {

            text = "← TORNA AI MIEI LUOGHI"

            setOnClickListener {

                webView.stopLoading()

                showHome()
            }
        }

        outputView = TextView(this).apply {

            text =
                "TRAVELPINS NETWORK MONITOR\n\n" +
                "In attesa del link..."

            setPadding(
                16,
                16,
                16,
                16
            )

            textSize = 12f
        }

        val logScroll = ScrollView(this).apply {

            addView(outputView)

            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    0.40f
                )
        }

        val copyButton = Button(this).apply {

            text = "COPIA"

            setOnClickListener {

                copyOutputToClipboard()
            }
        }

        val cleanButton = Button(this).apply {

            text = "PULISCI"

            setOnClickListener {

                clearOutput()
            }
        }

        val buttonRow = LinearLayout(this).apply {

            orientation =
                LinearLayout.HORIZONTAL

            addView(
                copyButton,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            addView(
                cleanButton,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        }

        root.addView(
            backButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // ========================================================
        // WEBVIEW
        // ========================================================

        root.addView(
            webView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                0.60f
            )
        )

        // ========================================================
        // LOG
        // ========================================================

        root.addView(logScroll)

        // ========================================================
        // BUTTONS
        // ========================================================

        root.addView(buttonRow)

        setContentView(root)
    }

    // ============================================================
    // WEBVIEW
    // ============================================================

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {

        webView = WebView(this).apply {

            settings.javaScriptEnabled = true

            settings.domStorageEnabled = true

            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 10) " +
                "AppleWebKit/537.36 " +
                "(KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Mobile Safari/537.36"
        }

        val bridge = TravelPinsJsBridge(

            repository = repository,

            scope = lifecycleScope,

            getCurrentSourceListId = {
                currentListId
            },

            getCurrentSourceListName = {
                null
            },

            onImportFinished = { savedCount ->

                runOnUiThread {

                    appendOutput(
                        "\nIMPORTAZIONE COMPLETATA\n\n" +
                        "Luoghi salvati: $savedCount"
                    )

                    Toast.makeText(
                        this,
                        "Importazione completata: $savedCount luoghi",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },

            onImportError = { error ->

                runOnUiThread {

                    appendOutput(
                        "\nERRORE SALVATAGGIO:\n" +
                        "${error.message}"
                    )

                    Toast.makeText(
                        this,
                        "Errore salvataggio: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },

            onLogMessage = { message ->

                runOnUiThread {

                    if (::outputView.isInitialized) {

                        appendOutput(message)
                    }
                }
            }
        )

        webView.addJavascriptInterface(
            bridge,
            TravelPinsJsBridge.NAME
        )

        webView.addJavascriptInterface(
            bridge,
            TravelPinsJsBridge.BRIDGE_NAME
        )

        webView.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {

                    super.onPageFinished(
                        view,
                        url
                    )

                    if (::outputView.isInitialized) {

                        appendOutput(
                            "PAGINA CARICATA: $url"
                        )
                    }

                    // Installa l'hook di rete.
                    view.evaluateJavascript(
                        GoogleMapsScraperScript
                            .NETWORK_HOOK_SCRIPT,
                        null
                    )

                    // ====================================================
                    // CONSENSO GOOGLE
                    // ====================================================

                    if (
                        url.contains(
                            "consent.google.com"
                        )
                    ) {

                        if (!consentAttempted) {

                            consentAttempted = true

                            appendOutput(
                                "CONSENSO GOOGLE RILEVATO\n\n" +
                                "Tento automaticamente di accettare..."
                            )

                            // Piccolo ritardo per permettere
                            // alla pagina di costruire completamente
                            // il pulsante.
                            view.postDelayed({

                                acceptGoogleConsent()

                            }, 700)
                        }

                        return
                    }

                    // ====================================================
                    // LISTA GOOGLE
                    // ====================================================

                    if (
                        GoogleMapsScraperScript
                            .isGoogleListUrl(url)
                    ) {

                        currentListId =
                            extractListId(url)

                        appendOutput(
                            "LISTA GOOGLE MAPS RILEVATA\n\n" +
                            "URL LISTA:\n$url"
                        )

                        // Avvio automatico UNA SOLA VOLTA.
                        if (
                            currentListId != null &&
                            !scanStarted
                        ) {

                            scanStarted = true

                            appendOutput(
                                "\nSCANSIONE AUTOMATICA..."
                            )

                            view.postDelayed({

                                scanGoogleList()

                            }, 500)
                        }
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail
                ): Boolean {

                    if (
                        ::outputView.isInitialized
                    ) {

                        appendOutput(
                            "WEBVIEW RENDERER TERMINATO\n\n" +
                            "CRASH: $detail"
                        )
                    }

                    return true
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {

                    val url =
                        request.url.toString()

                    if (
                        url.startsWith("intent://")
                    ) {

                        handleGoogleIntent(url)

                        return true
                    }

                    return false
                }
            }
    }

    // ============================================================
    // GOOGLE LIST ID
    // ============================================================

    private fun extractListId(
        url: String
    ): String? {

        Regex(
            "!11m2!2s([^!&]+)",
            RegexOption.IGNORE_CASE
        ).find(url)?.let {

            return it.groupValues[1]
        }

        Regex(
            """/local/userlists/list/([^?/]+)""",
            RegexOption.IGNORE_CASE
        ).find(url)?.let {

            return it.groupValues[1]
        }

        Regex(
            "2s([A-Za-z0-9_-]{20,})"
        ).find(url)?.let {

            return it.groupValues[1]
        }

        return null
    }

    // ============================================================
    // GOOGLE CONSENT
    // ============================================================

    private fun acceptGoogleConsent() {

        if (!::webView.isInitialized) {
            return
        }

        appendOutput(
            "AVVIO ACCETTAZIONE GOOGLE"
        )

        webView.evaluateJavascript(
            GoogleMapsScraperScript
                .ACCEPT_CONSENT_SCRIPT
        ) { result ->

            appendOutput(
                "CONSENSO RISULTATO\n\n$result"
            )
        }
    }

    // ============================================================
    // GOOGLE SCAN
    // ============================================================

    private fun scanGoogleList() {

        if (importStarted) {
            return
        }

        importStarted = true

        appendOutput(
            "SCANSIONE LISTA AVVIATA\n\n" +
            "Metodo: entitylist/getlist\n\n" +
            "NON utilizzo il DOM."
        )

        webView.evaluateJavascript(
            GoogleMapsScraperScript
                .GETLIST_SCRIPT
        ) { result ->

            appendOutput(
                "CALLBACK GETLIST\n\n$result"
            )
        }
    }

    // ============================================================
    // GOOGLE INTENT
    // ============================================================

    private fun handleGoogleIntent(
        intentUrl: String
    ) {

        try {

            val marker =
                "S.browser_fallback_url="

            val start =
                intentUrl.indexOf(marker)

            if (start == -1) {

                appendOutput(
                    "FALLBACK URL NON TROVATO"
                )

                return
            }

            var value =
                intentUrl.substring(
                    start + marker.length
                )

            val end =
                value.indexOf("#Intent")

            if (end != -1) {

                value =
                    value.substring(
                        0,
                        end
                    )
            }

            val decoded =
                URLDecoder.decode(
                    value,
                    "UTF-8"
                )

            appendOutput(
                "GOOGLE INTENT INTERCETTATO\n\n" +
                "CERCO FALLBACK WEB..."
            )

            appendOutput(
                "FALLBACK WEB TROVATO:\n\n" +
                decoded
            )

            webView.loadUrl(decoded)

        } catch (e: Exception) {

            appendOutput(
                "ERRORE PARSING INTENT:\n$e"
            )
        }
    }

    // ============================================================
    // SHARE INTENT
    // ============================================================

    private fun handleIntent(
        intent: Intent?
    ) {

        if (
            intent?.action !=
            Intent.ACTION_SEND
        ) {
            return
        }

        val text =
            intent.getStringExtra(
                Intent.EXTRA_TEXT
            )

        if (text.isNullOrBlank()) {

            Toast.makeText(
                this,
                "Nessun testo ricevuto",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val match =
            Regex(
                """https?://\S+"""
            ).find(text)

        if (match == null) {

            Toast.makeText(
                this,
                "Nessun URL trovato",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val url =
            match.value

        // Nuovo ciclo di importazione.
        consentAttempted = false
        scanStarted = false
        importStarted = false
        currentListId = null

        // Mostra immediatamente l'importatore.
        showImporter()

        appendOutput(
            "LINK RICEVUTO\n\n" +
            "$url\n\n" +
            "AVVIO GOOGLE MAPS WEB..."
        )

        webView.loadUrl(url)
    }

    // ============================================================
    // LOG
    // ============================================================

    private fun appendOutput(
        section: String
    ) {

        if (!::outputView.isInitialized) {
            return
        }

        outputView.append(
            "\n$section\n"
        )
    }

    private fun copyOutputToClipboard() {

        if (!::outputView.isInitialized) {
            return
        }

        val clipboard =
            getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as? ClipboardManager
                ?: return

        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "TravelPins",
                outputView.text
            )
        )

        Toast.makeText(
            this,
            "Copiato!",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun clearOutput() {

        if (!::outputView.isInitialized) {
            return
        }

        outputView.text =
            "TRAVELPINS NETWORK MONITOR\n\n" +
            "Monitor pulito."
    }
}
