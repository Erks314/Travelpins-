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
import com.travelpins.test.data.TravelPinsRepository
import com.travelpins.test.importer.TravelPinsJsBridge
import com.travelpins.test.scraper.GoogleMapsScraperScript

/**
 * Ricostruzione fedele della MainActivity funzionante trovata nell'APK di
 * test (disassemblata dal .dex — non riscritta a intuito). Rispetto
 * all'originale è stata aggiunta SOLO la persistenza in Room via
 * TravelPinsJsBridge.onPlacesExtracted; tutta la logica di scraping,
 * gestione consenso, hook di rete e gestione intent è invariata.
 *
 * UI nativa "da debug" (TextView di log + pulsanti), non layout XML,
 * per restare fedele a quanto trovato nell'app funzionante.
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var outputView: TextView
    private lateinit var consentButton: Button
    private lateinit var scanButton: Button

    private lateinit var repository: TravelPinsRepository

    /** listId della lista attualmente caricata (estratto lato Kotlin dall'URL, per taggare i luoghi salvati) */
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
        handleIntent(intent)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    // ---------------------------------------------------------------
    // UI
    // ---------------------------------------------------------------

    private fun buildUi(): View {
        outputView = TextView(this).apply {
            text = "TRAVELPINS NETWORK MONITOR\n\nIn attesa del link..."
            setPadding(24, 24, 24, 24)
            textSize = 13f
        }

        val scroll = ScrollView(this).apply {
            addView(outputView)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        val copyButton = Button(this).apply {
            text = "COPIA TUTTO"
            setOnClickListener { copyOutputToClipboard() }
        }

        val cleanButton = Button(this).apply {
            text = "PULISCI"
            setOnClickListener { clearOutput() }
        }

        consentButton = Button(this).apply {
            text = "ACCETTA GOOGLE"
            visibility = View.GONE
            setOnClickListener { acceptGoogleConsent() }
        }

        scanButton = Button(this).apply {
            text = "SCANSIONA"
            visibility = View.GONE
            setOnClickListener { scanGoogleList() }
        }

        // Richiesto dalla spec: pulsante di login opzionale, usato solo se
        // Google richiede una sessione autenticata per una lista privata.
        // Il flusso di scraping funziona anche senza toccarlo, per le liste
        // pubbliche. Integrazione reale (Credential Manager / Google Sign-In)
        // da collegare qui quando serve: non presente nell'APK originale,
        // quindi non "ricostruita" ma aggiunta come placeholder esplicito.
        val googleLoginButton = Button(this).apply {
            text = "🔐 Accedi con Google"
            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "Login opzionale non ancora collegato: da implementare con Credential Manager se serve per liste private.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(copyButton)
            addView(cleanButton)
            addView(consentButton)
            addView(scanButton)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(scroll)
            addView(buttonRow)
            addView(googleLoginButton)
        }
    }

    private fun appendOutput(section: String) {
        outputView.append("\n$section\n")
    }

    private fun copyOutputToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("TravelPins", outputView.text))
        Toast.makeText(this, "Copiato!", Toast.LENGTH_SHORT).show()
    }

    private fun clearOutput() {
        outputView.text = "TRAVELPINS NETWORK MONITOR\n\nMonitor pulito."
    }

    // ---------------------------------------------------------------
    // WebView
    // ---------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        }

        val bridge = TravelPinsJsBridge(
            repository = repository,
            scope = lifecycleScope,
            getCurrentSourceListId = { currentListId },
            getCurrentSourceListName = { null },
            onImportFinished = { savedCount ->
                runOnUiThread {
                    Toast.makeText(this, "Salvati $savedCount luoghi nel database", Toast.LENGTH_LONG).show()
                }
            },
            onImportError = { error ->
                runOnUiThread {
                    Toast.makeText(this, "Errore salvataggio: ${error.message}", Toast.LENGTH_LONG).show()
                }
            },
            onLogMessage = { message ->
                runOnUiThread { appendOutput(message) }
            }
        )

        // Lo script chiama sia TravelPins.log/network sia TravelPinsBridge.onPlacesExtracted:
        // registriamo lo stesso oggetto sotto entrambi i nomi.
        webView.addJavascriptInterface(bridge, TravelPinsJsBridge.NAME)
        webView.addJavascriptInterface(bridge, TravelPinsJsBridge.BRIDGE_NAME)

        webView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                appendOutput("PAGINA CARICATA: $url")

                // Hook di rete diagnostico, installato su ogni pagina caricata.
                view.evaluateJavascript(GoogleMapsScraperScript.NETWORK_HOOK_SCRIPT, null)

                if (url.contains("consent.google.com")) {
                    consentButton.visibility = View.VISIBLE
                    appendOutput("CONSENSO GOOGLE RILEVATO\n\nPremi: ACCETTA GOOGLE")
                } else if (GoogleMapsScraperScript.isGoogleListUrl(url)) {
                    currentListId = extractListId(url)
                    scanButton.visibility = View.VISIBLE
                    appendOutput("LISTA GOOGLE MAPS RILEVATA\n\nURL LISTA:\n$url\n\nPremere SCANSIONA.")
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                appendOutput("WEBVIEW RENDERER TERMINATO\n\nCRASH: $detail")
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
    }

    /** Stessa logica di estrazione del listId usata anche lato JS, duplicata qui per taggare i salvataggi lato Kotlin. */
    private fun extractListId(url: String): String? {
        Regex("!11m2!2s([^!&]+)", RegexOption.IGNORE_CASE).find(url)?.let { return it.groupValues[1] }
        Regex("""/local/userlists/list/([^?/]+)""", RegexOption.IGNORE_CASE).find(url)?.let { return it.groupValues[1] }
        Regex("2s([A-Za-z0-9_-]{20,})").find(url)?.let { return it.groupValues[1] }
        return null
    }

    private fun acceptGoogleConsent() {
        appendOutput("AVVIO ACCETTAZIONE GOOGLE")
        webView.evaluateJavascript(GoogleMapsScraperScript.ACCEPT_CONSENT_SCRIPT) { result ->
            appendOutput("CONSENSO RISULTATO\n\n$result")
        }
    }

    private fun scanGoogleList() {
        appendOutput("SCANSIONE LISTA AVVIATA\n\nMetodo: entitylist/getlist\n\nNON utilizzo il DOM.")
        webView.evaluateJavascript(GoogleMapsScraperScript.GETLIST_SCRIPT) { result ->
            appendOutput("CALLBACK GETLIST\n\n$result")
        }
    }

    /**
     * Alcuni link Google Maps arrivano come intent:// (per aprire l'app
     * nativa). Estraiamo il browser_fallback_url e lo carichiamo nella
     * WebView invece di fallire.
     */
    private fun handleGoogleIntent(intentUrl: String) {
        try {
            val marker = "S.browser_fallback_url="
            val start = intentUrl.indexOf(marker)
            if (start == -1) {
                appendOutput("FALLBACK URL NON TROVATO")
                return
            }
            var value = intentUrl.substring(start + marker.length)
            val end = value.indexOf("#Intent")
            if (end != -1) value = value.substring(0, end)
            val decoded = java.net.URLDecoder.decode(value, "UTF-8")
            appendOutput("GOOGLE INTENT INTERCETTATO\n\nCERCO FALLBACK WEB...")
            webView.loadUrl(decoded)
        } catch (e: Exception) {
            appendOutput("ERRORE PARSING INTENT:\n$e")
        }
    }

    // ---------------------------------------------------------------
    // Intent in ingresso (condivisione da Google Maps)
    // ---------------------------------------------------------------

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (text.isNullOrBlank()) {
            appendOutput("Nessun testo ricevuto.")
            return
        }

        val match = Regex("""https?://\S+""").find(text)
        if (match == null) {
            appendOutput("Nessun URL trovato.")
            return
        }

        val url = match.value
        appendOutput("LINK RICEVUTO\n\n$url\n\nAVVIO GOOGLE MAPS WEB...")
        webView.loadUrl(url)
    }
}
