package com.travelpins.test

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import com.travelpins.test.data.TravelPinsRepository
import com.travelpins.test.importer.TravelPinsJsBridge
import com.travelpins.test.scraper.GoogleMapsScraperScript

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var outputView: TextView
    private lateinit var consentButton: Button
    private lateinit var scanButton: Button

    private lateinit var repository: TravelPinsRepository

    private var currentListId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = TravelPinsRepository(applicationContext)

        setContentView(buildUi())
        createWebView()

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)

        // Quando arriva un nuovo link da Google Maps,
        // riportiamo l'interfaccia allo stato iniziale.
        scanButton.visibility = View.GONE
        consentButton.visibility = View.GONE
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

    // ===============================================================
    // UI
    // ===============================================================

    private fun buildUi(): View {

        outputView = TextView(this).apply {
            text = "TRAVELPINS NETWORK MONITOR\n\nIn attesa del link..."
            setPadding(24, 24, 24, 24)
            textSize = 13f
            setTextColor(Color.BLACK)
        }

        val logScroll = ScrollView(this).apply {

            addView(
                outputView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                0.45f
            )
        }

        // -----------------------------------------------------------
        // WEBVIEW
        // -----------------------------------------------------------

        webView = WebView(this).apply {

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                0.55f
            )

            setBackgroundColor(Color.WHITE)
        }

        // -----------------------------------------------------------
        // BUTTONS
        // -----------------------------------------------------------

        val copyButton = Button(this).apply {
            text = "COPIA TUTTO"

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

        consentButton = Button(this).apply {
            text = "ACCETTA GOOGLE"
            visibility = View.GONE

            setOnClickListener {
                acceptGoogleConsent()
            }
        }

        scanButton = Button(this).apply {
            text = "SCANSIONA"
            visibility = View.GONE

            setOnClickListener {
                scanGoogleList()
            }
        }

        val buttonRow = LinearLayout(this).apply {

            orientation = LinearLayout.HORIZONTAL

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

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

            addView(
                consentButton,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            addView(
                scanButton,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        }

        // -----------------------------------------------------------
        // LOGIN PLACEHOLDER
        // -----------------------------------------------------------

        val googleLoginButton = Button(this).apply {

            text = "🔐 Accedi con Google"

            setOnClickListener {

                Toast.makeText(
                    this@MainActivity,
                    "Login Google non ancora necessario per le liste pubbliche.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // -----------------------------------------------------------
        // ROOT
        // -----------------------------------------------------------

        return LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            addView(logScroll)

            // IMPORTANTISSIMO:
            // il WebView viene finalmente inserito nella UI.
            addView(webView)

            addView(buttonRow)

            addView(googleLoginButton)
        }
    }

    private fun appendOutput(section: String) {

        runOnUiThread {
            outputView.append("\n$section\n")
        }
    }

    private fun copyOutputToClipboard() {

        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
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

        outputView.text =
            "TRAVELPINS NETWORK MONITOR\n\nMonitor pulito."
    }

    // ===============================================================
    // WEBVIEW
    // ===============================================================

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {

        webView.settings.apply {

            javaScriptEnabled = true

            domStorageEnabled = true

            databaseEnabled = true

            loadsImagesAutomatically = true

            javaScriptCanOpenWindowsAutomatically = true

            userAgentString =
                "Mozilla/5.0 (Linux; Android 10) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
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
                        "IMPORTAZIONE COMPLETATA\n\n" +
                            "LUOGHI SALVATI: $savedCount"
                    )

                    Toast.makeText(
                        this,
                        "Salvati $savedCount luoghi nel database",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },

            onImportError = { error ->

                runOnUiThread {

                    appendOutput(
                        "ERRORE SALVATAGGIO:\n" +
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
                    appendOutput(message)
                }
            }
        )

        // Bridge usato dagli script JavaScript.
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

                override fun onPageStarted(
                    view: WebView,
                    url: String,
                    favicon: android.graphics.Bitmap?
                ) {

                    super.onPageStarted(
                        view,
                        url,
                        favicon
                    )

                    appendOutput(
                        "PAGINA IN CARICAMENTO:\n$url"
                    )
                }

                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {

                    super.onPageFinished(
                        view,
                        url
                    )

                    appendOutput(
                        "PAGINA CARICATA: $url"
                    )

                    // Installa l'hook di rete.
                    view.evaluateJavascript(
                        GoogleMapsScraperScript.NETWORK_HOOK_SCRIPT,
                        null
                    )

                    // ------------------------------------------------
                    // CONSENSO GOOGLE
                    // ------------------------------------------------

                    if (url.contains(
                            "consent.google.com",
                            ignoreCase = true
                        )
                    ) {

                        consentButton.visibility =
                            View.VISIBLE

                        appendOutput(
                            "CONSENSO GOOGLE RILEVATO\n\n" +
                                "Premi: ACCETTA GOOGLE"
                        )

                        return
                    }

                    // ------------------------------------------------
                    // LISTA GOOGLE MAPS
                    // ------------------------------------------------

                    if (
                        GoogleMapsScraperScript
                            .isGoogleListUrl(url)
                    ) {

                        currentListId =
                            extractListId(url)

                        if (currentListId != null) {

                            appendOutput(
                                "LIST ID RILEVATO:\n" +
                                    currentListId
                            )
                        }

                        scanButton.visibility =
                            View.VISIBLE

                        appendOutput(
                            "LISTA GOOGLE MAPS RILEVATA\n\n" +
                                "URL LISTA:\n$url\n\n" +
                                "Premere SCANSIONA."
                        )
                    }
                }

                // ----------------------------------------------------
                // Android / WebView moderno
                // ----------------------------------------------------

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {

                    val url =
                        request.url.toString()

                    return handleNavigation(
                        view,
                        url
                    )
                }

                // ----------------------------------------------------
                // Compatibilità con vecchie versioni WebView
                // ----------------------------------------------------

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    url: String
                ): Boolean {

                    return handleNavigation(
                        view,
                        url
                    )
                }

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail
                ): Boolean {

                    appendOutput(
                        "WEBVIEW RENDERER TERMINATO\n\n" +
                            "CRASH: $detail"
                    )

                    return true
                }
            }
    }

    // ===============================================================
    // NAVIGAZIONE GOOGLE MAPS
    // ===============================================================

    private fun handleNavigation(
        view: WebView,
        url: String
    ): Boolean {

        appendOutput(
            "NAVIGAZIONE:\n$url"
        )

        // -----------------------------------------------------------
        // Intent Google Maps
        // -----------------------------------------------------------

        if (
            url.startsWith(
                "intent://",
                ignoreCase = true
            )
        ) {

            appendOutput(
                "GOOGLE INTENT INTERCETTATO\n\n" +
                    "CERCO FALLBACK WEB..."
            )

            handleGoogleIntent(url)

            return true
        }

        // -----------------------------------------------------------
        // Link Google Maps normali
        // -----------------------------------------------------------

        if (
            url.startsWith(
                "https://maps.google.com",
                ignoreCase = true
            ) ||
            url.startsWith(
                "https://www.google.com/maps",
                ignoreCase = true
            ) ||
            url.startsWith(
                "https://google.com/maps",
                ignoreCase = true
            )
        ) {

            return false
        }

        // -----------------------------------------------------------
        // Link maps.app.goo.gl
        // -----------------------------------------------------------

        if (
            url.startsWith(
                "https://maps.app.goo.gl",
                ignoreCase = true
            )
        ) {

            appendOutput(
                "SHORT LINK GOOGLE MAPS RILEVATO"
            )

            return false
        }

        return false
    }

    // ===============================================================
    // ESTRAZIONE LIST ID
    // ===============================================================

    private fun extractListId(
        url: String
    ): String? {

        Regex(
            "!11m2!2s([^!&]+)",
            RegexOption.IGNORE_CASE
        )
            .find(url)
            ?.let {
                return it.groupValues[1]
            }

        Regex(
            """\/local\/userlists\/list\/([^?\/]+)""",
            RegexOption.IGNORE_CASE
        )
            .find(url)
            ?.let {
                return it.groupValues[1]
            }

        Regex(
            "2s([A-Za-z0-9_-]{20,})"
        )
            .find(url)
            ?.let {
                return it.groupValues[1]
            }

        return null
    }

    // ===============================================================
    // CONSENSO GOOGLE
    // ===============================================================

    private fun acceptGoogleConsent() {

        appendOutput(
            "AVVIO ACCETTAZIONE GOOGLE"
        )

        webView.evaluateJavascript(
            GoogleMapsScraperScript.ACCEPT_CONSENT_SCRIPT
        ) { result ->

            appendOutput(
                "CONSENSO RISULTATO\n\n$result"
            )
        }
    }

    // ===============================================================
    // SCANSIONE
    // ===============================================================

    private fun scanGoogleList() {

        val listId =
            currentListId

        if (listId.isNullOrBlank()) {

            appendOutput(
                "ERRORE: LIST ID NON DISPONIBILE"
            )

            Toast.makeText(
                this,
                "Lista Google Maps non identificata",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        appendOutput(
            "SCANSIONE LISTA AVVIATA\n\n" +
                "Metodo: entitylist/getlist\n\n" +
                "NON utilizzo il DOM.\n\n" +
                "LIST ID: $listId"
        )

        webView.evaluateJavascript(
            GoogleMapsScraperScript.GETLIST_SCRIPT
        ) { result ->

            appendOutput(
                "CALLBACK GETLIST\n\n$result"
            )
        }
    }

    // ===============================================================
    // GOOGLE INTENT FALLBACK
    // ===============================================================

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
                java.net.URLDecoder.decode(
                    value,
                    "UTF-8"
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

    // ===============================================================
    // SHARE INTENT
    // ===============================================================

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

            appendOutput(
                "Nessun testo ricevuto."
            )

            return
        }

        val match =
            Regex(
                """https?://\S+"""
            ).find(text)

        if (match == null) {

            appendOutput(
                "Nessun URL trovato."
            )

            return
        }

        val url =
            match.value

        appendOutput(
            "LINK RICEVUTO\n\n" +
                "$url\n\n" +
                "AVVIO GOOGLE MAPS WEB..."
        )

        webView.loadUrl(url)
    }
}
