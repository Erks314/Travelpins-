package com.travelpins.test

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var repository: TravelPinsRepository

    private lateinit var contentView: LinearLayout
    private lateinit var statusView: TextView

    private var currentListId: String? = null
    private var lastScannedListId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = TravelPinsRepository(applicationContext)

        setContentView(buildMainUi())
        createWebView()
        observePlaces()

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    // ===============================================================
    // INTERFACCIA PRINCIPALE
    // ===============================================================

    private fun buildMainUi(): View {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        // -----------------------------------------------------------
        // HEADER
        // -----------------------------------------------------------

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 20)
            setBackgroundColor(Color.rgb(63, 81, 181))
        }

        val title = TextView(this).apply {
            text = "TravelPins"
            textSize = 28f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }

        val subtitle = TextView(this).apply {
            text = "I tuoi luoghi, organizzati come vuoi"
            textSize = 15f
            setTextColor(Color.WHITE)
            alpha = 0.9f
            setPadding(0, 6, 0, 0)
        }

        header.addView(title)
        header.addView(subtitle)

        root.addView(
            header,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // -----------------------------------------------------------
        // STATO IMPORTAZIONE
        // -----------------------------------------------------------

        statusView = TextView(this).apply {
            text = "Condividi una lista da Google Maps per importarla."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(24, 18, 24, 18)
            gravity = Gravity.CENTER_VERTICAL
        }

        root.addView(
            statusView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // -----------------------------------------------------------
        // CONTENUTO LUOGHI
        // -----------------------------------------------------------

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
        }

        contentView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 24)
        }

        scrollView.addView(
            contentView,
            ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        return root
    }

    // ===============================================================
    // DATABASE
    // ===============================================================

    private fun observePlaces() {

        lifecycleScope.launch {
            repository.places.collectLatest { places ->
                runOnUiThread {
                    showPlaces(places)
                }
            }
        }
    }

    private fun showPlaces(places: List<Place>) {

        contentView.removeAllViews()

        if (places.isEmpty()) {

            val empty = TextView(this).apply {
                text = """
                    Nessun luogo ancora presente.

                    Per iniziare:
                    1. Apri Google Maps
                    2. Apri una tua lista
                    3. Premi Condividi
                    4. Seleziona TravelPins
                """.trimIndent()

                textSize = 16f
                setTextColor(Color.DKGRAY)
                setPadding(16, 40, 16, 40)
                gravity = Gravity.CENTER
            }

            contentView.addView(
                empty,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            return
        }

        val countTitle = TextView(this).apply {
            text = "${places.size} luoghi"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(40, 40, 40))
            setPadding(8, 8, 8, 16)
        }

        contentView.addView(countTitle)

        for (place in places) {
            contentView.addView(createPlaceCard(place))

            val spacer = View(this).apply {
                setBackgroundColor(Color.TRANSPARENT)
            }

            contentView.addView(
                spacer,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    10
                )
            )
        }
    }

    private fun createPlaceCard(place: Place): View {

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 18)
            setBackgroundColor(Color.rgb(245, 245, 245))
        }

        val name = TextView(this).apply {
            text = place.name
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(30, 30, 30))
        }

        card.addView(name)

        place.address?.takeIf { it.isNotBlank() }?.let { address ->

            val addressView = TextView(this).apply {
                text = address
                textSize = 14f
                setTextColor(Color.DKGRAY)
                setPadding(0, 8, 0, 0)
            }

            card.addView(addressView)
        }

        val coordinates = TextView(this).apply {
            text = "📍 ${place.latitude}, ${place.longitude}"
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, 8, 0, 0)
        }

        card.addView(coordinates)

        return card
    }

    // ===============================================================
    // WEBVIEW / GOOGLE MAPS
    // ===============================================================

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {

        webView = WebView(this).apply {

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true

            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

            /*
             * La WebView rimane presente ma praticamente invisibile.
             * Serve per eseguire il flusso Google Maps già funzionante.
             */
            alpha = 0f
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

                    statusView.text =
                        if (savedCount > 0) {
                            "Importazione completata: $savedCount luoghi salvati."
                        } else {
                            "Importazione completata."
                        }

                    Toast.makeText(
                        this,
                        "Salvati $savedCount luoghi nel database",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },

            onImportError = { error ->

                runOnUiThread {

                    statusView.text =
                        "Errore durante l'importazione."

                    Toast.makeText(
                        this,
                        "Errore salvataggio: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },

            onLogMessage = { message ->

                runOnUiThread {

                    when {
                        message.startsWith("PLACE TROVATI:") -> {
                            statusView.text = message
                        }

                        message == "NETWORK HOOK INSTALLATO" -> {
                            // Nessuna azione: messaggio tecnico.
                        }

                        message.startsWith("GETLIST HTTP:") -> {
                            statusView.text = "Lettura della lista Google Maps..."
                        }

                        message.startsWith("JSON PARSATO CORRETTAMENTE") -> {
                            statusView.text = "Lista Google Maps analizzata."
                        }
                    }
                }
            }
        )

        /*
         * Il bridge funzionante utilizza il nome TravelPins.
         *
         * NON aggiungiamo BRIDGE_NAME perché nel file
         * TravelPinsJsBridge attuale non esiste.
         */
        webView.addJavascriptInterface(
            bridge,
            TravelPinsJsBridge.NAME
        )

        webView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(
                view: WebView,
                url: String
            ) {

                super.onPageFinished(view, url)

                // Hook diagnostico già funzionante.
                view.evaluateJavascript(
                    GoogleMapsScraperScript.NETWORK_HOOK_SCRIPT,
                    null
                )

                // ---------------------------------------------------
                // CONSENSO GOOGLE
                // ---------------------------------------------------

                if (url.contains("consent.google.com")) {

                    statusView.text =
                        "Autorizzazione Google in corso..."

                    view.evaluateJavascript(
                        GoogleMapsScraperScript.ACCEPT_CONSENT_SCRIPT
                    ) { result ->

                        /*
                         * Google normalmente prosegue automaticamente
                         * dopo il click sul consenso.
                         */
                        if (result.contains("CLICK_OK")) {
                            statusView.text =
                                "Consenso Google accettato. Caricamento lista..."
                        }
                    }

                    return
                }

                // ---------------------------------------------------
                // LISTA GOOGLE MAPS
                // ---------------------------------------------------

                if (GoogleMapsScraperScript.isGoogleListUrl(url)) {

                    val listId = extractListId(url)

                    if (listId != null) {
                        currentListId = listId
                    }

                    /*
                     * Evita di lanciare GETLIST più volte sulla stessa
                     * pagina quando Google richiama onPageFinished.
                     */
                    if (
                        currentListId != null &&
                        currentListId != lastScannedListId
                    ) {

                        lastScannedListId = currentListId

                        statusView.text =
                            "Importazione lista Google Maps in corso..."

                        scanGoogleList()
                    }
                }
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail
            ): Boolean {

                runOnUiThread {
                    statusView.text =
                        "Errore nel motore Google Maps."
                }

                return true
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {

                val url = request.url.toString()

                if (url.startsWith("intent://")) {

                    handleGoogleIntent(url)
                    return true
                }

                return false
            }
        }

        /*
         * WebView di servizio.
         * Altezza minima per rimanere attaccata alla gerarchia della
         * Activity senza occupare la schermata.
         */
        val params = ViewGroup.LayoutParams(
            1,
            1
        )

        addContentView(webView, params)
    }

    // ===============================================================
    // SCANSIONE
    // ===============================================================

    private fun scanGoogleList() {

        webView.evaluateJavascript(
            GoogleMapsScraperScript.GETLIST_SCRIPT,
            null
        )
    }

    // ===============================================================
    // LIST ID
    // ===============================================================

    private fun extractListId(url: String): String? {

        Regex(
            "!11m2!2s([^!&]+)",
            RegexOption.IGNORE_CASE
        ).find(url)?.let {
            return it.groupValues[1]
        }

        Regex(
            """\/local\/userlists\/list\/([^?\/]+)""",
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

    // ===============================================================
    // GOOGLE INTENT
    // ===============================================================

    private fun handleGoogleIntent(intentUrl: String) {

        try {

            val marker = "S.browser_fallback_url="

            val start = intentUrl.indexOf(marker)

            if (start == -1) {
                statusView.text =
                    "URL Google Maps non riconosciuto."
                return
            }

            var value =
                intentUrl.substring(start + marker.length)

            val end = value.indexOf("#Intent")

            if (end != -1) {
                value = value.substring(0, end)
            }

            val decoded =
                java.net.URLDecoder.decode(
                    value,
                    "UTF-8"
                )

            statusView.text =
                "Apertura della lista Google Maps..."

            webView.loadUrl(decoded)

        } catch (e: Exception) {

            statusView.text =
                "Errore apertura Google Maps."
        }
    }

    // ===============================================================
    // CONDIVIDI DA GOOGLE MAPS
    // ===============================================================

    private fun handleIntent(intent: Intent?) {

        if (intent?.action != Intent.ACTION_SEND) {
            return
        }

        val text =
            intent.getStringExtra(Intent.EXTRA_TEXT)

        if (text.isNullOrBlank()) {
            statusView.text =
                "Nessun link ricevuto da Google Maps."
            return
        }

        val match =
            Regex("""https?://\S+""").find(text)

        if (match == null) {
            statusView.text =
                "Nessun URL trovato."
            return
        }

        val url = match.value

        statusView.text =
            "Ricevuta lista Google Maps..."

        /*
         * Nuova condivisione = nuova scansione.
         */
        lastScannedListId = null
        currentListId = null

        webView.loadUrl(url)
    }
}
