package com.travelpins.test

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
            setBackgroundColor(Color.rgb(248, 249, 250))
            setPadding(20, 28, 20, 20)
        }

        val title = TextView(this).apply {
            text = "TRAVELPINS"
            textSize = 30f
            setTextColor(Color.rgb(30, 30, 30))
            setPadding(0, 0, 0, 4)
        }

        val subtitle = TextView(this).apply {
            text = "I miei luoghi"
            textSize = 18f
            setTextColor(Color.rgb(90, 90, 90))
            setPadding(0, 0, 0, 18)
        }

        root.addView(title)
        root.addView(subtitle)

        // --------------------------------------------------------
        // CONTATORE
        // --------------------------------------------------------

        val countCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 18)
            background = roundedBackground(
                Color.WHITE,
                18f
            )
        }

        val countTitle = TextView(this).apply {
            text = "I TUOI LUOGHI"
            textSize = 12f
            setTextColor(Color.rgb(110, 110, 110))
        }

        val countView = TextView(this).apply {
            tag = "place_count"
            text = "0"
            textSize = 30f
            setTextColor(Color.rgb(30, 30, 30))
            setPadding(0, 4, 0, 0)
        }

        countCard.addView(countTitle)
        countCard.addView(countView)

        root.addView(
            countCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 14
            }
        )

        // --------------------------------------------------------
        // IMPORTA
        // --------------------------------------------------------

        val importButton = Button(this).apply {

            text = "＋  IMPORTA DA GOOGLE MAPS"

            textSize = 15f

            setTextColor(Color.WHITE)

            background = roundedBackground(
                Color.rgb(45, 105, 225),
                16f
            )

            setPadding(12, 8, 12, 8)

            setOnClickListener {
                showImporter()
            }
        }

        root.addView(
            importButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                58
            ).apply {
                bottomMargin = 10
            }
        )

        // --------------------------------------------------------
        // CATEGORIE
        // --------------------------------------------------------

        val categoriesButton = Button(this).apply {

            text = "CATEGORIE"

            textSize = 14f

            setTextColor(Color.rgb(45, 45, 45))

            background = roundedBackground(
                Color.WHITE,
                16f
            )

            setOnClickListener {

                Toast.makeText(
                    this@MainActivity,
                    "Sezione categorie: prossimo step",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        root.addView(
            categoriesButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                52
            ).apply {
                bottomMargin = 18
            }
        )

        // --------------------------------------------------------
        // TITOLO LISTA
        // --------------------------------------------------------

        val placesTitle = TextView(this).apply {
            text = "LUOGHI SALVATI"
            textSize = 13f
            setTextColor(Color.rgb(100, 100, 100))
            setPadding(2, 0, 0, 10)
        }

        root.addView(placesTitle)

        // --------------------------------------------------------
        // LISTA LUOGHI
        // --------------------------------------------------------

        val placesScroll = ScrollView(this)

        val placesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "places_container"
            setPadding(0, 0, 0, 20)
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
                        places.size.toString()

                    val container =
                        content.findViewWithTag<LinearLayout>(
                            "places_container"
                        )
                            ?: return@runOnUiThread

                    container.removeAllViews()

                    if (places.isEmpty()) {

                        val emptyCard =
                            LinearLayout(this@MainActivity).apply {

                                orientation =
                                    LinearLayout.VERTICAL

                                gravity =
                                    android.view.Gravity.CENTER

                                setPadding(
                                    24,
                                    32,
                                    24,
                                    32
                                )

                                background =
                                    roundedBackground(
                                        Color.WHITE,
                                        18f
                                    )
                            }

                        val emptyTitle =
                            TextView(this@MainActivity).apply {

                                text =
                                    "Nessun luogo ancora salvato"

                                textSize = 17f

                                setTextColor(
                                    Color.rgb(
                                        50,
                                        50,
                                        50
                                    )
                                )

                                gravity =
                                    android.view.Gravity.CENTER
                            }

                        val emptyText =
                            TextView(this@MainActivity).apply {

                                text =
                                    "Importa una lista da Google Maps\n" +
                                    "per iniziare a organizzare i tuoi luoghi."

                                textSize = 14f

                                setTextColor(
                                    Color.rgb(
                                        110,
                                        110,
                                        110
                                    )
                                )

                                gravity =
                                    android.view.Gravity.CENTER

                                setPadding(
                                    0,
                                    8,
                                    0,
                                    0
                                )
                            }

                        emptyCard.addView(emptyTitle)
                        emptyCard.addView(emptyText)

                        container.addView(
                            emptyCard,
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        )

                    } else {

                        places.forEach { place ->

                            container.addView(
                                createPlaceView(place),
                                LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    bottomMargin = 10
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ============================================================
    // CARD SINGOLO LUOGO
    // ============================================================

    private fun createPlaceView(
        place: Place
    ): View {

        val box = LinearLayout(this).apply {

            orientation =
                LinearLayout.VERTICAL

            setPadding(
                18,
                16,
                18,
                16
            )

            background =
                roundedBackground(
                    Color.WHITE,
                    18f
                )
        }

        val name = TextView(this).apply {

            text = place.name

            textSize = 17f

            setTextColor(
                Color.rgb(
                    35,
                    35,
                    35
                )
            )

            setPadding(
                0,
                0,
                0,
                7
            )
        }

        box.addView(name)

        if (!place.address.isNullOrBlank()) {

            val address =
                TextView(this).apply {

                    text = place.address

                    textSize = 14f

                    setTextColor(
                        Color.rgb(
                            95,
                            95,
                            95
                        )
                    )

                    setPadding(
                        0,
                        0,
                        0,
                        7
                    )
                }

            box.addView(address)
        }

        if (place.categoryId != null) {

            val category =
                TextView(this).apply {

                    text =
                        "Categoria ID: ${place.categoryId}"

                    textSize = 12f

                    setTextColor(
                        Color.rgb(
                            70,
                            100,
                            180
                        )
                    )

                    setPadding(
                        0,
                        4,
                        0,
                        7
                    )
                }

            box.addView(category)
        }

        val coordinates =
            TextView(this).apply {

                text =
                    "📍 ${place.latitude}, ${place.longitude}"

                textSize = 11f

                setTextColor(
                    Color.rgb(
                        130,
                        130,
                        130
                    )
                )
            }

        box.addView(coordinates)

        return box
    }

    // ============================================================
    // BACKGROUND CARD
    // ============================================================

    private fun roundedBackground(
        color: Int,
        radius: Float
    ): GradientDrawable {

        return GradientDrawable().apply {
            setColor(color)
            cornerRadius =
                radius *
                    resources.displayMetrics.density
        }
    }

    // ============================================================
    // IMPORTER
    // ============================================================

    private fun showImporter() {

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

        root.addView(
            webView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                0.60f
            )
        )

        root.addView(logScroll)

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
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
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

                    // ====================================================
                    // NUOVO:
                    // dopo il salvataggio torniamo automaticamente
                    // alla Home.
                    // ====================================================

                    webView.stopLoading()

                    showHome()
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

                    view.evaluateJavascript(
                        GoogleMapsScraperScript
                            .NETWORK_HOOK_SCRIPT,
                        null
                    )

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

                            view.postDelayed({

                                acceptGoogleConsent()

                            }, 700)
                        }

                        return
                    }

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

        consentAttempted = false
        scanStarted = false
        importStarted = false
        currentListId = null

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
