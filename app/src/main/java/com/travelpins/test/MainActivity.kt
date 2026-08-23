package com.travelpins.test

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var output: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var statusText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val log = ConcurrentLinkedQueue<String>()

    private val places = ArrayList<Place>()
    private val categories = ArrayList<Category>()

    private var importing = false
    private var scanStarted = false

    // ============================================================
    // MODELLI
    // ============================================================

    data class Place(
        val name: String,
        val address: String,
        val lat: Double,
        val lng: Double,
        var categoryId: String = ""
    )

    data class Category(
        val id: String,
        var name: String,
        var color: Int,
        var icon: String
    )

    // ============================================================
    // ON CREATE
    // ============================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadCategories()
        loadPlaces()

        createInterface()
        createWebView()

        handleIntent(intent)
    }

    // ============================================================
    // INTERFACCIA
    // ============================================================

    private fun createInterface() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.rgb(35, 35, 35))
        }

        val title = TextView(this).apply {
            text = "📍 TravelPins"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }

        toolbar.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val categoriesButton = Button(this).apply {
            text = "Categorie"

            setOnClickListener {
                showCategories()
            }
        }

        toolbar.addView(categoriesButton)

        progress = ProgressBar(this).apply {
            visibility = ProgressBar.GONE
        }

        statusText = TextView(this).apply {
            text = ""
            textSize = 15f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(20, 12, 20, 12)
            visibility = TextView.GONE
        }

        root.addView(toolbar)

        root.addView(
            progress,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            statusText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val scroll = ScrollView(this)

        output = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 40)
        }

        scroll.addView(output)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        showPlaces()
    }

    // ============================================================
    // WEBVIEW
    // ============================================================

    private fun createWebView() {

        webView = WebView(this)

        webView.settings.apply {

            javaScriptEnabled = true

            domStorageEnabled = true

            databaseEnabled = true

            loadsImagesAutomatically = true

            javaScriptCanOpenWindowsAutomatically = true

            setSupportMultipleWindows(false)

            userAgentString =
                "Mozilla/5.0 (Linux; Android 10) " +
                "AppleWebKit/537.36 " +
                "(KHTML, like Gecko) " +
                "Chrome/131.0.0.0 " +
                "Mobile Safari/537.36"
        }

        CookieManager
            .getInstance()
            .setAcceptCookie(true)

        CookieManager
            .getInstance()
            .setAcceptThirdPartyCookies(
                webView,
                true
            )

        webView.addJavascriptInterface(
            TravelPinsBridge(),
            "TravelPins"
        )

        webView.webChromeClient =
            WebChromeClient()

        webView.webViewClient =
            object : WebViewClient() {

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {

                    val url =
                        request.url.toString()

                    addLog(
                        "[NAVIGAZIONE]\n$url"
                    )

                    if (
                        url.startsWith(
                            "intent://"
                        )
                    ) {

                        handleGoogleIntent(url)

                        return true
                    }

                    return false
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {

                    val url =
                        request.url.toString()

                    val lower =
                        url.lowercase()

                    if (
                        lower.contains("entitylist") ||
                        lower.contains("userlists")
                    ) {

                        addLog(
                            "[GOOGLE REQUEST]\n" +
                            "${request.method}\n" +
                            url
                        )
                    }

                    return null
                }

                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {

                    super.onPageFinished(
                        view,
                        url
                    )

                    addLog(
                        "[PAGINA CARICATA]\n$url"
                    )

                    injectNetworkHook()

                    /*
                     * NON aspettiamo esclusivamente che l'URL
                     * corrisponda perfettamente a una forma.
                     *
                     * Google Maps può fare diversi redirect.
                     *
                     * Dopo il caricamento proviamo comunque
                     * a individuare la lista.
                     */

                    if (!scanStarted) {

                        handler.postDelayed(
                            {

                                if (
                                    !scanStarted &&
                                    !importing
                                ) {

                                    scanStarted = true

                                    scanGoogleList()

                                }

                            },
                            1800
                        )
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError
                ) {

                    if (
                        request.isForMainFrame
                    ) {

                        addLog(
                            "[WEBVIEW ERROR]\n" +
                            error.description
                        )

                        showStatus(
                            "Errore nel caricamento di Google Maps"
                        )
                    }

                    super.onReceivedError(
                        view,
                        request,
                        error
                    )
                }
            }
    }

    // ============================================================
    // JAVASCRIPT BRIDGE
    // ============================================================

    inner class TravelPinsBridge {

        @JavascriptInterface
        fun log(message: String?) {

            if (
                !message.isNullOrBlank()
            ) {

                addLog(message)
            }
        }

        @JavascriptInterface
        fun importPlaces(
            json: String
        ) {

            runOnUiThread {

                try {

                    val array =
                        JSONArray(json)

                    if (
                        array.length() == 0
                    ) {

                        importing = false
                        scanStarted = false

                        progress.visibility =
                            ProgressBar.GONE

                        showStatus(
                            "Google Maps non ha restituito nessun luogo."
                        )

                        return@runOnUiThread
                    }

                    places.clear()

                    for (
                        i in 0 until array.length()
                    ) {

                        val item =
                            array.getJSONObject(i)

                        val name =
                            item.optString(
                                "name"
                            )

                        val address =
                            item.optString(
                                "address"
                            )

                        val lat =
                            item.optDouble(
                                "lat",
                                Double.NaN
                            )

                        val lng =
                            item.optDouble(
                                "lng",
                                Double.NaN
                            )

                        if (
                            name.isNotBlank() &&
                            !lat.isNaN() &&
                            !lng.isNaN()
                        ) {

                            places.add(
                                Place(
                                    name = name,
                                    address = address,
                                    lat = lat,
                                    lng = lng
                                )
                            )
                        }
                    }

                    savePlaces()

                    progress.visibility =
                        ProgressBar.GONE

                    webView.visibility =
                        WebView.GONE

                    importing = false
                    scanStarted = false

                    showStatus(
                        "${places.size} luoghi importati"
                    )

                    showPlaces()

                    Toast.makeText(
                        this@MainActivity,
                        "${places.size} luoghi importati",
                        Toast.LENGTH_SHORT
                    ).show()

                } catch (
                    e: Exception
                ) {

                    importing = false
                    scanStarted = false

                    progress.visibility =
                        ProgressBar.GONE

                    showStatus(
                        "Errore importazione: ${e.message}"
                    )

                    addLog(
                        "[IMPORT ERROR]\n${e.stackTraceToString()}"
                    )
                }
            }
        }

        @JavascriptInterface
        fun importError(
            message: String
        ) {

            runOnUiThread {

                importing = false
                scanStarted = false

                progress.visibility =
                    ProgressBar.GONE

                showStatus(
                    message
                )

                addLog(
                    "[IMPORT ERROR]\n$message"
                )
            }
        }
    }

    // ============================================================
    // STATUS
    // ============================================================

    private fun showStatus(
        text: String
    ) {

        statusText.text =
            text

        statusText.visibility =
            if (text.isBlank()) {
                TextView.GONE
            } else {
                TextView.VISIBLE
            }
    }

    // ============================================================
    // RICONOSCIMENTO LISTA
    // ============================================================

    private fun isGoogleListUrl(
        url: String
    ): Boolean {

        val lower =
            url.lowercase()

        return (
            lower.contains(
                "/local/userlists/list/"
            ) ||
            (
                lower.contains(
                    "/maps/"
                ) &&
                lower.contains(
                    "!11m2!2s"
                )
            ) ||
            lower.contains(
                "maps.app.goo.gl"
            )
        )
    }

    // ============================================================
    // IMPORTAZIONE LISTA
    // ============================================================

    private fun scanGoogleList() {

        if (importing) {
            return
        }

        importing = true

        progress.visibility =
            ProgressBar.VISIBLE

        webView.visibility =
            WebView.VISIBLE

        showStatus(
            "Importazione della lista Google Maps…"
        )

        addLog(
            """
            ==============================
            SCANSIONE AUTOMATICA

            URL:
            ${webView.url}

            ==============================
            """.trimIndent()
        )

        val javascript = """

            (async function() {

                try {

                    // =================================================
                    // URL
                    // =================================================

                    var currentUrl =
                        window.location.href;

                    TravelPins.log(
                        'URL ANALIZZATO: ' +
                        currentUrl
                    );

                    // =================================================
                    // LIST ID
                    // =================================================

                    var listId = '';

                    var match =
                        currentUrl.match(
                            /!11m2!2s([^!&]+)/i
                        );

                    if (match) {

                        listId =
                            decodeURIComponent(
                                match[1]
                            );

                    }

                    // -------------------------------------------------
                    // /local/userlists/list/...
                    // -------------------------------------------------

                    if (!listId) {

                        match =
                            currentUrl.match(
                                /\/local\/userlists\/list\/([^?\/#]+)/i
                            );

                        if (match) {

                            listId =
                                decodeURIComponent(
                                    match[1]
                                );
                        }
                    }

                    // -------------------------------------------------
                    // Cerca genericamente 2s...
                    // -------------------------------------------------

                    if (!listId) {

                        match =
                            currentUrl.match(
                                /!2s([A-Za-z0-9_-]{10,})/
                            );

                        if (match) {

                            listId =
                                decodeURIComponent(
                                    match[1]
                                );
                        }
                    }

                    // -------------------------------------------------
                    // Cerca nel data URL
                    // -------------------------------------------------

                    if (!listId) {

                        var dataMatch =
                            currentUrl.match(
                                /data=.*?!11m2!2s([^!&]+)/i
                            );

                        if (dataMatch) {

                            listId =
                                decodeURIComponent(
                                    dataMatch[1]
                                );
                        }
                    }

                    TravelPins.log(
                        'LIST ID: ' +
                        (
                            listId ||
                            'NON TROVATO'
                        )
                    );

                    if (!listId) {

                        TravelPins.importError(
                            'Google Maps ha aperto la pagina, ma non è stato possibile individuare l\\'ID della lista.'
                        );

                        return;
                    }

                    // =================================================
                    // ENDPOINT
                    // =================================================

                    var pb =
                        '!1m4' +
                        '!1s' +
                        encodeURIComponent(
                            listId
                        ) +
                        '!2e1' +
                        '!3m1!1e1' +
                        '!2e2' +
                        '!3e3' +
                        '!4i500' +
                        '!8i3' +
                        '!16b1';

                    var endpoint =
                        'https://www.google.com/maps/preview/entitylist/getlist' +
                        '?authuser=0' +
                        '&hl=it' +
                        '&gl=it' +
                        '&pb=' +
                        pb;

                    TravelPins.log(
                        'GETLIST URL:\n' +
                        endpoint
                    );

                    // =================================================
                    // FETCH CON TIMEOUT
                    // =================================================

                    var controller =
                        new AbortController();

                    var timeout =
                        setTimeout(
                            function() {

                                controller.abort();

                            },
                            15000
                        );

                    var response;

                    try {

                        response =
                            await fetch(
                                endpoint,
                                {
                                    method: 'GET',

                                    credentials:
                                        'include',

                                    cache:
                                        'no-store',

                                    signal:
                                        controller.signal,

                                    headers: {

                                        'Accept':
                                            '*/*',

                                        'X-Requested-With':
                                            'XMLHttpRequest'
                                    }
                                }
                            );

                    } catch(fetchError) {

                        clearTimeout(
                            timeout
                        );

                        TravelPins.importError(
                            'Google Maps non ha risposto alla richiesta della lista: ' +
                            fetchError.message
                        );

                        return;
                    }

                    clearTimeout(
                        timeout
                    );

                    TravelPins.log(
                        'GETLIST HTTP: ' +
                        response.status
                    );

                    if (
                        !response.ok
                    ) {

                        TravelPins.importError(
                            'Google Maps ha restituito HTTP ' +
                            response.status
                        );

                        return;
                    }

                    // =================================================
                    // RAW
                    // =================================================

                    var raw =
                        await response.text();

                    TravelPins.log(
                        'GETLIST LENGTH: ' +
                        raw.length
                    );

                    if (
                        !raw ||
                        raw.length < 10
                    ) {

                        TravelPins.importError(
                            'Google Maps ha restituito una risposta vuota.'
                        );

                        return;
                    }

                    TravelPins.log(
                        'GETLIST RAW START:\n' +
                        raw.substring(
                            0,
                            3000
                        )
                    );

                    // =================================================
                    // XSSI
                    // =================================================

                    var cleaned =
                        raw;

                    if (
                        cleaned.indexOf(
                            ")]}'"
                        ) === 0
                    ) {

                        cleaned =
                            cleaned.substring(
                                4
                            );

                        if (
                            cleaned.charAt(0) ===
                            '\n'
                        ) {

                            cleaned =
                                cleaned.substring(
                                    1
                                );
                        }
                    }

                    // =================================================
                    // JSON
                    // =================================================

                    var data;

                    try {

                        data =
                            JSON.parse(
                                cleaned
                            );

                    } catch(jsonError) {

                        TravelPins.importError(
                            'Risposta Google non interpretabile come JSON.'
                        );

                        TravelPins.log(
                            'JSON ERROR: ' +
                            jsonError.message
                        );

                        return;
                    }

                    TravelPins.log(
                        'JSON PARSATO CORRETTAMENTE'
                    );

                    // =================================================
                    // UTILITIES
                    // =================================================

                    var places = [];

                    function isNumber(v) {

                        return (
                            typeof v ===
                            'number' &&
                            isFinite(v)
                        );
                    }

                    function looksLikeLatLng(
                        a,
                        b
                    ) {

                        return (
                            isNumber(a) &&
                            isNumber(b) &&
                            Math.abs(a) <= 90 &&
                            Math.abs(b) <= 180
                        );
                    }

                    function cleanString(
                        value
                    ) {

                        if (
                            typeof value !==
                            'string'
                        ) {

                            return '';
                        }

                        return value
                            .replace(
                                /\s+/g,
                                ' '
                            )
                            .trim();
                    }

                    function isUsefulName(
                        value
                    ) {

                        var s =
                            cleanString(
                                value
                            );

                        if (
                            !s ||
                            s.length < 2 ||
                            s.length > 250
                        ) {

                            return false;
                        }

                        if (
                            s.indexOf(
                                'http://'
                            ) === 0 ||
                            s.indexOf(
                                'https://'
                            ) === 0
                        ) {

                            return false;
                        }

                        return true;
                    }

                    // =================================================
                    // PARSER GOOGLE
                    // =================================================

                    function tryKnownPlace(
                        x
                    ) {

                        try {

                            if (
                                !Array.isArray(x)
                            ) {

                                return;
                            }

                            if (
                                x.length < 3
                            ) {

                                return;
                            }

                            var name =
                                cleanString(
                                    x[2]
                                );

                            if (
                                !isUsefulName(
                                    name
                                )
                            ) {

                                return;
                            }

                            var envelope =
                                x[1];

                            if (
                                !Array.isArray(
                                    envelope
                                )
                            ) {

                                return;
                            }

                            var coordBlock =
                                envelope[5];

                            if (
                                !Array.isArray(
                                    coordBlock
                                )
                            ) {

                                return;
                            }

                            var lat =
                                coordBlock[2];

                            var lng =
                                coordBlock[3];

                            if (
                                !looksLikeLatLng(
                                    lat,
                                    lng
                                )
                            ) {

                                return;
                            }

                            var address =
                                '';

                            if (
                                typeof x[3] ===
                                'string'
                            ) {

                                address =
                                    cleanString(
                                        x[3]
                                    );
                            }

                            places.push({

                                name:
                                    name,

                                address:
                                    address,

                                lat:
                                    lat,

                                lng:
                                    lng
                            });

                        } catch(e) {

                            // Ignora strutture
                            // che non sono place
                        }
                    }

                    // =================================================
                    // WALK
                    // =================================================

                    function walk(
                        node
                    ) {

                        if (
                            node === null ||
                            node === undefined
                        ) {

                            return;
                        }

                        if (
                            Array.isArray(
                                node
                            )
                        ) {

                            tryKnownPlace(
                                node
                            );

                            for (
                                var i = 0;
                                i < node.length;
                                i++
                            ) {

                                walk(
                                    node[i]
                                );
                            }

                        } else if (
                            typeof node ===
                            'object'
                        ) {

                            for (
                                var key in node
                            ) {

                                try {

                                    walk(
                                        node[key]
                                    );

                                } catch(e) {}
                            }
                        }
                    }

                    walk(data);

                    // =================================================
                    // DUPLICATI
                    // =================================================

                    var unique = [];

                    var seen = {};

                    for (
                        var i = 0;
                        i < places.length;
                        i++
                    ) {

                        var p =
                            places[i];

                        var key =
                            p.name +
                            '|' +
                            p.lat +
                            '|' +
                            p.lng;

                        if (
                            !seen[key]
                        ) {

                            seen[key] =
                                true;

                            unique.push(
                                p
                            );
                        }
                    }

                    places =
                        unique;

                    // =================================================
                    // RISULTATO
                    // =================================================

                    TravelPins.log(
                        'PLACE TROVATI: ' +
                        places.length
                    );

                    if (
                        places.length === 0
                    ) {

                        TravelPins.log(
                            'NESSUN PLACE TROVATO.'
                        );

                        if (
                            Array.isArray(data)
                        ) {

                            TravelPins.log(
                                'TOP LEVEL LENGTH: ' +
                                data.length
                            );

                            for (
                                var z = 0;
                                z < Math.min(
                                    data.length,
                                    10
                                );
                                z++
                            ) {

                                try {

                                    TravelPins.log(
                                        'TOP[' +
                                        z +
                                        ']: ' +
                                        JSON.stringify(
                                            data[z]
                                        ).substring(
                                            0,
                                            800
                                        )
                                    );

                                } catch(e) {}
                            }
                        }

                        TravelPins.importError(
                            'Google Maps ha restituito la lista, ma non è stato possibile estrarre i luoghi.'
                        );

                        return;
                    }

                    // =================================================
                    // INVIO A KOTLIN
                    // =================================================

                    TravelPins.log(
                        'INVIO ' +
                        places.length +
                        ' LUOGHI A TRAVELPINS'
                    );

                    TravelPins.importPlaces(
                        JSON.stringify(
                            places
                        )
                    );

                } catch(e) {

                    TravelPins.importError(
                        'Errore durante l\\'importazione: ' +
                        e.message
                    );

                    TravelPins.log(
                        'GENERAL ERROR: ' +
                        e.stack
                    );
                }

            })();

        """.trimIndent()

        webView.evaluateJavascript(
            javascript
        ) { result ->

            addLog(
                "[JAVASCRIPT CALLBACK]\n$result"
            )
        }

        /*
         * SICUREZZA:
         * se per qualsiasi motivo JavaScript non chiama
         * mai importPlaces/importError, dopo 20 secondi
         * sblocchiamo comunque l'app.
         */

        handler.postDelayed(
            {

                if (importing) {

                    importing = false
                    scanStarted = false

                    progress.visibility =
                        ProgressBar.GONE

                    showStatus(
                        "Importazione interrotta: Google Maps non ha risposto in tempo."
                    )

                    addLog(
                        "[TIMEOUT KOTLIN] Importazione interrotta dopo 20 secondi."
                    )
                }

            },
            20000
        )
    }

    // ============================================================
    // NETWORK HOOK
    // ============================================================

    private fun injectNetworkHook() {

        webView.evaluateJavascript(
            """
            (function() {

                if (
                    window.__travelpins_hooked
                ) {
                    return;
                }

                window.__travelpins_hooked =
                    true;

                var originalFetch =
                    window.fetch;

                window.fetch =
                    function() {

                        return originalFetch.apply(
                            this,
                            arguments
                        );
                    };

            })();
            """.trimIndent(),
            null
        )
    }

    // ============================================================
    // VISUALIZZAZIONE LUOGHI
    // ============================================================

    private fun showPlaces() {

        output.removeAllViews()

        val title =
            TextView(this).apply {

                text =
                    if (
                        places.isEmpty()
                    ) {
                        "📍 TravelPins"
                    } else {
                        "📍 ${places.size} luoghi"
                    }

                textSize = 27f

                setTextColor(
                    Color.BLACK
                )

                setPadding(
                    0,
                    5,
                    0,
                    20
                )
            }

        output.addView(title)

        if (
            places.isEmpty()
        ) {

            val empty =
                TextView(this).apply {

                    text =
                        "Condividi una lista di Google Maps con TravelPins per iniziare."

                    textSize = 17f

                    setTextColor(
                        Color.GRAY
                    )

                    setPadding(
                        0,
                        10,
                        0,
                        20
                    )
                }

            output.addView(empty)

            return
        }

        places.forEachIndexed {
            index,
            place ->

            addPlaceCard(
                index,
                place
            )
        }
    }

    private fun addPlaceCard(
        index: Int,
        place: Place
    ) {

        val category =
            categories.find {
                it.id ==
                place.categoryId
            }

        val card =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    18,
                    16,
                    18,
                    16
                )

                setBackgroundColor(
                    Color.rgb(
                        245,
                        245,
                        245
                    )
                )
            }

        val title =
            TextView(this).apply {

                text =
                    "${index + 1}. ${place.name}"

                textSize = 18f

                setTextColor(
                    Color.BLACK
                )
            }

        card.addView(title)

        if (
            place.address.isNotBlank()
        ) {

            val address =
                TextView(this).apply {

                    text =
                        place.address

                    textSize = 14f

                    setTextColor(
                        Color.DKGRAY
                    )

                    setPadding(
                        0,
                        7,
                        0,
                        7
                    )
                }

            card.addView(address)
        }

        val categoryText =
            TextView(this).apply {

                text =
                    if (
                        category == null
                    ) {

                        "⚪ Nessuna categoria"

                    } else {

                        "${category.icon} ${category.name}"
                    }

                textSize = 15f

                setTextColor(
                    category?.color
                        ?: Color.GRAY
                )

                setPadding(
                    0,
                    5,
                    0,
                    5
                )

                setOnClickListener {

                    chooseCategory(
                        place
                    )
                }
            }

        card.addView(
            categoryText
        )

        card.setOnClickListener {

            chooseCategory(
                place
            )
        }

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        params.setMargins(
            0,
            0,
            0,
            14
        )

        output.addView(
            card,
            params
        )
    }

    // ============================================================
    // CATEGORIE
    // ============================================================

    private fun chooseCategory(
        place: Place
    ) {

        if (
            categories.isEmpty()
        ) {

            createCategory()

            return
        }

        val names =
            categories.map {
                category ->

                "${category.icon} ${category.name}"

            }.toTypedArray()

        AlertDialog.Builder(this)

            .setTitle(
                "Categoria"
            )

            .setItems(
                names
            ) {
                    _: android.content.DialogInterface,
                    which: Int ->

                place.categoryId =
                    categories[
                        which
                    ].id

                savePlaces()

                showPlaces()
            }

            .setNegativeButton(
                "Nessuna categoria"
            ) {
                    _: android.content.DialogInterface,
                    _: Int ->

                place.categoryId =
                    ""

                savePlaces()

                showPlaces()
            }

            .show()
    }

    // ============================================================
    // MOSTRA CATEGORIE
    // ============================================================

    private fun showCategories() {

        val layout =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    30,
                    10,
                    30,
                    10
                )
            }

        categories.forEach {
            category ->

            val row =
                LinearLayout(this).apply {

                    orientation =
                        LinearLayout.HORIZONTAL

                    gravity =
                        Gravity.CENTER_VERTICAL

                    setPadding(
                        0,
                        12,
                        0,
                        12
                    )
                }

            val icon =
                TextView(this).apply {

                    text =
                        category.icon

                    textSize = 25f
                }

            val name =
                TextView(this).apply {

                    text =
                        category.name

                    textSize = 18f

                    setTextColor(
                        category.color
                    )

                    setPadding(
                        18,
                        0,
                        0,
                        0
                    )
                }

            row.addView(
                icon
            )

            row.addView(
                name,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            layout.addView(
                row
            )
        }

        val addButton =
            Button(this).apply {

                text =
                    "＋ Nuova categoria"

                setOnClickListener {

                    createCategory()
                }
            }

        layout.addView(
            addButton
        )

        AlertDialog.Builder(this)

            .setTitle(
                "Categorie"
            )

            .setView(
                layout
            )

            .setPositiveButton(
                "Chiudi"
            ) {
                    _: android.content.DialogInterface,
                    _: Int ->
            }

            .show()
    }

    // ============================================================
    // CREAZIONE CATEGORIA
    // ============================================================

    private fun createCategory() {

        val layout =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    30,
                    10,
                    30,
                    10
                )
            }

        val name =
            EditText(this).apply {

                hint =
                    "Nome categoria"

                setSingleLine(
                    true
                )
            }

        val icons =
            arrayOf(
                "📍",
                "🍴",
                "🏰",
                "🌊",
                "🏔️",
                "🏖️",
                "☕",
                "🍺",
                "📸",
                "🚗",
                "🏨",
                "⭐"
            )

        val iconSpinner =
            Spinner(this)

        iconSpinner.adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                icons
            )

        layout.addView(
            name
        )

        layout.addView(
            iconSpinner,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        AlertDialog.Builder(this)

            .setTitle(
                "Nuova categoria"
            )

            .setView(
                layout
            )

            .setPositiveButton(
                "Crea"
            ) {
                    _: android.content.DialogInterface,
                    _: Int ->

                val categoryName =
                    name.text
                        .toString()
                        .trim()

                if (
                    categoryName.isBlank()
                ) {

                    return@setPositiveButton
                }

                val icon =
                    icons[
                        iconSpinner
                            .selectedItemPosition
                    ]

                val category =
                    Category(
                        id =
                            System.currentTimeMillis()
                                .toString(),

                        name =
                            categoryName,

                        color =
                            Color.rgb(
                                30,
                                100,
                                200
                            ),

                        icon =
                            icon
                    )

                categories.add(
                    category
                )

                saveCategories()

                Toast.makeText(
                    this,
                    "Categoria creata",
                    Toast.LENGTH_SHORT
                ).show()
            }

            .setNegativeButton(
                "Annulla"
            ) {
                    _: android.content.DialogInterface,
                    _: Int ->
            }

            .show()
    }

    // ============================================================
    // SALVATAGGIO CATEGORIE
    // ============================================================

    private fun saveCategories() {

        val array =
            JSONArray()

        categories.forEach {
            category ->

            val obj =
                JSONObject()

            obj.put(
                "id",
                category.id
            )

            obj.put(
                "name",
                category.name
            )

            obj.put(
                "color",
                category.color
            )

            obj.put(
                "icon",
                category.icon
            )

            array.put(
                obj
            )
        }

        getPreferences(
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                "categories",
                array.toString()
            )
            .apply()
    }

    // ============================================================
    // CARICAMENTO CATEGORIE
    // ============================================================

    private fun loadCategories() {

        categories.clear()

        val raw =
            getPreferences(
                Context.MODE_PRIVATE
            )
                .getString(
                    "categories",
                    null
                )

        if (
            raw.isNullOrBlank()
        ) {

            categories.add(
                Category(
                    id = "default_1",
                    name = "Da vedere",
                    color =
                        Color.rgb(
                            30,
                            100,
                            200
                        ),
                    icon = "📍"
                )
            )

            categories.add(
                Category(
                    id = "default_2",
                    name = "Ristoranti",
                    color =
                        Color.rgb(
                            220,
                            80,
                            50
                        ),
                    icon = "🍴"
                )
            )

            categories.add(
                Category(
                    id = "default_3",
                    name = "Natura",
                    color =
                        Color.rgb(
                            40,
                            150,
                            70
                        ),
                    icon = "🌿"
                )
            )

            saveCategories()

            return
        }

        try {

            val array =
                JSONArray(
                    raw
                )

            for (
                i in 0 until array.length()
            ) {

                val obj =
                    array.getJSONObject(
                        i
                    )

                categories.add(
                    Category(
                        id =
                            obj.getString(
                                "id"
                            ),

                        name =
                            obj.getString(
                                "name"
                            ),

                        color =
                            obj.getInt(
                                "color"
                            ),

                        icon =
                            obj.getString(
                                "icon"
                            )
                    )
                )
            }

        } catch (
            _: Exception
        ) {

            categories.clear()
        }
    }

    // ============================================================
    // SALVATAGGIO LUOGHI
    // ============================================================

    private fun savePlaces() {

        val array =
            JSONArray()

        places.forEach {
            place ->

            val obj =
                JSONObject()

            obj.put(
                "name",
                place.name
            )

            obj.put(
                "address",
                place.address
            )

            obj.put(
                "lat",
                place.lat
            )

            obj.put(
                "lng",
                place.lng
            )

            obj.put(
                "category",
                place.categoryId
            )

            array.put(
                obj
            )
        }

        getPreferences(
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                "places",
                array.toString()
            )
            .apply()
    }

    // ============================================================
    // CARICAMENTO LUOGHI
    // ============================================================

    private fun loadPlaces() {

        places.clear()

        val raw =
            getPreferences(
                Context.MODE_PRIVATE
            )
                .getString(
                    "places",
                    null
                )

        if (
            raw.isNullOrBlank()
        ) {

            return
        }

        try {

            val array =
                JSONArray(
                    raw
                )

            for (
                i in 0 until array.length()
            ) {

                val obj =
                    array.getJSONObject(
                        i
                    )

                places.add(
                    Place(
                        name =
                            obj.optString(
                                "name"
                            ),

                        address =
                            obj.optString(
                                "address"
                            ),

                        lat =
                            obj.optDouble(
                                "lat"
                            ),

                        lng =
                            obj.optDouble(
                                "lng"
                            ),

                        categoryId =
                            obj.optString(
                                "category"
                            )
                    )
                )
            }

        } catch (
            _: Exception
        ) {

            places.clear()
        }
    }

    // ============================================================
    // GOOGLE INTENT
    // ============================================================

    private fun handleGoogleIntent(
        intentUrl: String
    ) {

        try {

            val uri =
                Uri.parse(
                    intentUrl
                )

            val fallback =
                uri.getQueryParameter(
                    "S.browser_fallback_url"
                )

            if (
                !fallback.isNullOrBlank()
            ) {

                webView.loadUrl(
                    fallback
                )

                return
            }

            val marker =
                "S.browser_fallback_url="

            val index =
                intentUrl.indexOf(
                    marker
                )

            if (
                index >= 0
            ) {

                var fallbackText =
                    intentUrl.substring(
                        index +
                            marker.length
                    )

                val end =
                    fallbackText.indexOf(
                        "#Intent"
                    )

                if (
                    end >= 0
                ) {

                    fallbackText =
                        fallbackText.substring(
                            0,
                            end
                        )
                }

                fallbackText =
                    Uri.decode(
                        fallbackText
                    )

                webView.loadUrl(
                    fallbackText
                )
            }

        } catch (
            _: Exception
        ) {

            addLog(
                "[INTENT ERROR]"
            )
        }
    }

    // ============================================================
    // CONDIVIDI DA GOOGLE MAPS
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

        val sharedText =
            intent.getStringExtra(
                Intent.EXTRA_TEXT
            )

        if (
            sharedText.isNullOrBlank()
        ) {

            return
        }

        val match =
            Regex(
                """https?://\S+"""
            ).find(
                sharedText
            )

        if (
            match == null
        ) {

            Toast.makeText(
                this,
                "Nessun link Google Maps trovato",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        var url =
            match.value

        /*
         * Alcune app inseriscono punteggiatura
         * dopo il link.
         */

        url =
            url.trimEnd(
                '.',
                ',',
                ';',
                ')',
                ']'
            )

        addLog(
            """
            ==============================
            LINK RICEVUTO

            $url

            ==============================
            AVVIO IMPORTAZIONE...
            """.trimIndent()
        )

        importing = false
        scanStarted = false

        progress.visibility =
            ProgressBar.VISIBLE

        webView.visibility =
            WebView.VISIBLE

        showStatus(
            "Apertura della lista Google Maps…"
        )

        webView.loadUrl(
            url
        )
    }

    // ============================================================
    // NUOVO INTENT
    // ============================================================

    override fun onNewIntent(
        intent: Intent
    ) {

        super.onNewIntent(
            intent
        )

        setIntent(
            intent
        )

        handleIntent(
            intent
        )
    }

    // ============================================================
    // LOG
    // ============================================================

    private fun addLog(
        message: String
    ) {

        log.add(
            message.take(
                15000
            )
        )

        while (
            log.size > 50
        ) {

            log.poll()
        }
    }

    // ============================================================
    // BACK
    // ============================================================

    @Suppress(
        "DEPRECATION"
    )
    override fun onBackPressed() {

        if (
            webView.canGoBack()
        ) {

            webView.goBack()

        } else {

            super.onBackPressed()
        }
    }

    // ============================================================
    // DESTROY
    // ============================================================

    override fun onDestroy() {

        handler.removeCallbacksAndMessages(
            null
        )

        webView.stopLoading()

        webView.removeJavascriptInterface(
            "TravelPins"
        )

        webView.destroy()

        super.onDestroy()
    }
}
