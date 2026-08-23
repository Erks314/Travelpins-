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
    private lateinit var scanButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private val log = ConcurrentLinkedQueue<String>()

    private val places = ArrayList<Place>()
    private val categories = ArrayList<Category>()

    private var importing = false
    private var pageLoaded = false

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
            setPadding(12, 10, 12, 10)
            setBackgroundColor(Color.rgb(35, 35, 35))
        }

        val title = TextView(this).apply {
            text = "📍 TravelPins"
            textSize = 21f
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

        scanButton = Button(this).apply {
            text = "SCANSIONA"
            isEnabled = false

            setOnClickListener {
                scanGoogleList()
            }
        }

        toolbar.addView(
            scanButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
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
            setPadding(20, 10, 20, 10)
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
                "Mozilla/5.0 (Linux; Android 10; Pixel 5) " +
                "AppleWebKit/537.36 " +
                "(KHTML, like Gecko) " +
                "Chrome/151.0.0.0 " +
                "Mobile Safari/537.36"
        }

        CookieManager.getInstance().setAcceptCookie(true)

        CookieManager.getInstance()
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

                    val url = request.url.toString()

                    addLog(
                        "[NAVIGAZIONE]\n$url"
                    )

                    if (url.startsWith("intent://")) {

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
                        lower.contains("getlist") ||
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

                    pageLoaded = true

                    addLog(
                        "[PAGINA CARICATA]\n$url"
                    )

                    injectNetworkHook()

                    /*
                     * IMPORTANTISSIMO:
                     *
                     * Non lanciamo più automaticamente
                     * la scansione.
                     *
                     * L'utente può prima effettuare
                     * il login Google.
                     */

                    scanButton.isEnabled = true

                    showStatus(
                        "Pagina pronta. Se necessario completa il login Google, poi premi SCANSIONA."
                    )
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError
                ) {

                    if (request.isForMainFrame) {

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
    // BRIDGE
    // ============================================================

    inner class TravelPinsBridge {

        @JavascriptInterface
        fun log(message: String?) {

            if (!message.isNullOrBlank()) {
                addLog(message)
            }
        }

        @JavascriptInterface
        fun importPlaces(json: String) {

            runOnUiThread {

                try {

                    val array = JSONArray(json)

                    val imported =
                        ArrayList<Place>()

                    for (
                        i in 0 until array.length()
                    ) {

                        val item =
                            array.getJSONObject(i)

                        val name =
                            item.optString("name")
                                .trim()

                        val address =
                            item.optString("address")
                                .trim()

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

                        /*
                         * Scartiamo completamente
                         * risultati corrotti.
                         */

                        if (
                            name.isBlank() ||
                            name.equals(
                                "null",
                                ignoreCase = true
                            )
                        ) {
                            continue
                        }

                        if (
                            name.matches(
                                Regex(
                                    """[-+]?\d+([.,]\d+)?"""
                                )
                            )
                        ) {
                            continue
                        }

                        if (
                            lat.isNaN() ||
                            lng.isNaN()
                        ) {
                            continue
                        }

                        if (
                            lat !in -90.0..90.0 ||
                            lng !in -180.0..180.0
                        ) {
                            continue
                        }

                        imported.add(
                            Place(
                                name = name,
                                address = address,
                                lat = lat,
                                lng = lng
                            )
                        )
                    }

                    /*
                     * Duplicati.
                     */

                    val unique =
                        ArrayList<Place>()

                    val seen =
                        HashSet<String>()

                    imported.forEach { place ->

                        val key =
                            place.name.lowercase() +
                                    "|" +
                                    "%.6f".format(
                                        place.lat
                                    ) +
                                    "|" +
                                    "%.6f".format(
                                        place.lng
                                    )

                        if (seen.add(key)) {
                            unique.add(place)
                        }
                    }

                    places.clear()
                    places.addAll(unique)

                    savePlaces()

                    importing = false

                    progress.visibility =
                        ProgressBar.GONE

                    webView.visibility =
                        WebView.GONE

                    scanButton.isEnabled = true

                    showStatus(
                        "${places.size} luoghi importati correttamente."
                    )

                    showPlaces()

                    Toast.makeText(
                        this@MainActivity,
                        "${places.size} luoghi importati",
                        Toast.LENGTH_SHORT
                    ).show()

                } catch (e: Exception) {

                    importing = false

                    progress.visibility =
                        ProgressBar.GONE

                    scanButton.isEnabled = true

                    showStatus(
                        "Errore importazione: ${e.message}"
                    )

                    addLog(
                        "[IMPORT ERROR]\n" +
                                e.stackTraceToString()
                    )
                }
            }
        }

        @JavascriptInterface
        fun importError(message: String) {

            runOnUiThread {

                importing = false

                progress.visibility =
                    ProgressBar.GONE

                scanButton.isEnabled = true

                showStatus(message)

                addLog(
                    "[IMPORT ERROR]\n$message"
                )
            }
        }
    }

    // ============================================================
    // STATUS
    // ============================================================

    private fun showStatus(text: String) {

        statusText.text = text

        statusText.visibility =
            if (text.isBlank()) {
                TextView.GONE
            } else {
                TextView.VISIBLE
            }
    }

    // ============================================================
    // SCANSIONE
    // ============================================================

    private fun scanGoogleList() {

        if (importing) {
            return
        }

        if (!pageLoaded) {

            Toast.makeText(
                this,
                "Google Maps non è ancora pronta.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        importing = true

        scanButton.isEnabled = false

        progress.visibility =
            ProgressBar.VISIBLE

        webView.visibility =
            WebView.VISIBLE

        showStatus(
            "Ricerca della lista Google Maps…"
        )

        addLog(
            """
            ==============================
            SCANSIONE MANUALE

            URL:
            ${webView.url}

            ==============================
            """.trimIndent()
        )

        val javascript = """

            (async function() {

                try {

                    // =================================================
                    // FUNZIONI UTILI
                    // =================================================

                    function log(msg) {

                        try {
                            TravelPins.log(msg);
                        } catch(e) {}
                    }

                    function clean(value) {

                        if (
                            value === null ||
                            value === undefined
                        ) {
                            return '';
                        }

                        return String(value)
                            .replace(/\s+/g, ' ')
                            .trim();
                    }

                    function validLatLng(
                        lat,
                        lng
                    ) {

                        return (
                            typeof lat === 'number' &&
                            typeof lng === 'number' &&
                            isFinite(lat) &&
                            isFinite(lng) &&
                            lat >= -90 &&
                            lat <= 90 &&
                            lng >= -180 &&
                            lng <= 180
                        );
                    }

                    function validName(
                        name
                    ) {

                        name = clean(name);

                        if (
                            !name ||
                            name.length < 2 ||
                            name.length > 250
                        ) {
                            return false;
                        }

                        if (
                            name === 'null' ||
                            name === 'undefined'
                        ) {
                            return false;
                        }

                        if (
                            /^[-+]?\d+([.,]\d+)?$/.test(
                                name
                            )
                        ) {
                            return false;
                        }

                        return true;
                    }

                    // =================================================
                    // 1. CERCA IL VERO GETLIST GENERATO DA GOOGLE
                    // =================================================

                    var getListUrl = '';

                    try {

                        var resources =
                            performance.getEntriesByType(
                                'resource'
                            );

                        for (
                            var i = resources.length - 1;
                            i >= 0;
                            i--
                        ) {

                            var resource =
                                resources[i];

                            if (
                                resource &&
                                resource.name &&
                                resource.name.indexOf(
                                    '/maps/preview/entitylist/getlist'
                                ) >= 0
                            ) {

                                getListUrl =
                                    resource.name;

                                break;
                            }
                        }

                    } catch(e) {

                        log(
                            'PERFORMANCE ERROR: ' +
                            e.message
                        );
                    }

                    // =================================================
                    // 2. CERCA NELLA PAGINA HTML
                    // =================================================

                    if (!getListUrl) {

                        try {

                            var html =
                                document.documentElement
                                    .outerHTML;

                            var index =
                                html.indexOf(
                                    '/maps/preview/entitylist/getlist'
                                );

                            if (index >= 0) {

                                var start =
                                    Math.max(
                                        0,
                                        index - 500
                                    );

                                var end =
                                    Math.min(
                                        html.length,
                                        index + 5000
                                    );

                                var fragment =
                                    html.substring(
                                        start,
                                        end
                                    );

                                var match =
                                    fragment.match(
                                        /https?:\/\/[^"'\\s<>]+getlist[^"'\\s<>]*/i
                                    );

                                if (match) {

                                    getListUrl =
                                        match[0];

                                } else {

                                    var relative =
                                        fragment.match(
                                            /\/maps\/preview\/entitylist\/getlist[^"'\\s<>]*/i
                                        );

                                    if (relative) {

                                        getListUrl =
                                            'https://www.google.com' +
                                            relative[0];
                                    }
                                }
                            }

                        } catch(e) {

                            log(
                                'HTML SEARCH ERROR: ' +
                                e.message
                            );
                        }
                    }

                    // =================================================
                    // LOG
                    // =================================================

                    log(
                        'GETLIST TROVATO: ' +
                        (
                            getListUrl
                                ? 'SI'
                                : 'NO'
                        )
                    );

                    if (!getListUrl) {

                        log(
                            'NESSUN GETLIST PRESENTE.'
                        );

                        TravelPins.importError(
                            'Google Maps non ha ancora caricato i dati della lista. Completa il login, attendi qualche secondo e premi di nuovo SCANSIONA.'
                        );

                        return;
                    }

                    /*
                     * Evitiamo URL troncati accidentalmente
                     * dall'HTML.
                     */

                    getListUrl =
                        getListUrl
                            .replace(
                                /&amp;/g,
                                '&'
                            );

                    log(
                        'GETLIST URL:\\n' +
                        getListUrl
                    );

                    // =================================================
                    // 3. FETCH DELLA RISORSA ORIGINALE
                    // =================================================

                    var controller =
                        new AbortController();

                    var timer =
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
                                getListUrl,
                                {
                                    method: 'GET',

                                    credentials:
                                        'include',

                                    cache:
                                        'no-store',

                                    signal:
                                        controller.signal
                                }
                            );

                    } catch(e) {

                        clearTimeout(timer);

                        TravelPins.importError(
                            'Google Maps non ha risposto: ' +
                            e.message
                        );

                        return;
                    }

                    clearTimeout(timer);

                    log(
                        'GETLIST HTTP: ' +
                        response.status
                    );

                    if (!response.ok) {

                        TravelPins.importError(
                            'Google Maps ha restituito HTTP ' +
                            response.status
                        );

                        return;
                    }

                    // =================================================
                    // 4. RAW
                    // =================================================

                    var raw =
                        await response.text();

                    log(
                        'GETLIST LENGTH: ' +
                        raw.length
                    );

                    if (
                        !raw ||
                        raw.length < 20
                    ) {

                        TravelPins.importError(
                            'Google Maps ha restituito una risposta vuota.'
                        );

                        return;
                    }

                    // =================================================
                    // 5. RIMOZIONE XSSI
                    // =================================================

                    var cleaned =
                        raw;

                    if (
                        cleaned.indexOf(
                            ")]}'"
                        ) === 0
                    ) {

                        cleaned =
                            cleaned.substring(4);

                        if (
                            cleaned.charAt(0) ===
                            '\n'
                        ) {

                            cleaned =
                                cleaned.substring(1);
                        }
                    }

                    // =================================================
                    // 6. JSON
                    // =================================================

                    var data;

                    try {

                        data =
                            JSON.parse(cleaned);

                    } catch(e) {

                        log(
                            'JSON ERROR: ' +
                            e.message
                        );

                        log(
                            'RAW START:\\n' +
                            raw.substring(0, 2500)
                        );

                        TravelPins.importError(
                            'Google ha restituito dati non interpretabili.'
                        );

                        return;
                    }

                    log(
                        'JSON PARSATO.'
                    );

                    // =================================================
                    // 7. PARSER DELLA STRUTTURA GETLIST
                    // =================================================

                    var places = [];

                    /*
                     * Struttura osservata attualmente:
                     *
                     * [null,
                     *   [null,null,address,null,address,
                     *      [null,null,lat,lng],
                     *      [placeId1,placeId2],
                     *      "/g/...",
                     *   ],
                     *   "PLACE NAME",
                     *   "",
                     *   ...
                     * ]
                     *
                     * Il nome è quindi x[2].
                     * L'indirizzo è x[1][2] o x[1][4].
                     * Le coordinate sono x[1][5][2] e x[1][5][3].
                     */

                    function tryPlace(
                        x
                    ) {

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
                            clean(x[2]);

                        if (
                            !validName(name)
                        ) {
                            return;
                        }

                        var envelope =
                            x[1];

                        if (
                            !Array.isArray(envelope)
                        ) {
                            return;
                        }

                        /*
                         * Coordinate nella struttura
                         * attuale di getlist.
                         */

                        var coord =
                            envelope[5];

                        if (
                            !Array.isArray(coord)
                        ) {
                            return;
                        }

                        var lat =
                            coord[2];

                        var lng =
                            coord[3];

                        if (
                            !validLatLng(
                                lat,
                                lng
                            )
                        ) {
                            return;
                        }

                        var address = '';

                        /*
                         * Prima scelta: envelope[2]
                         */

                        if (
                            typeof envelope[2] ===
                            'string'
                        ) {

                            address =
                                clean(
                                    envelope[2]
                                );
                        }

                        /*
                         * Seconda scelta:
                         * envelope[4]
                         */

                        if (
                            !address &&
                            typeof envelope[4] ===
                            'string'
                        ) {

                            address =
                                clean(
                                    envelope[4]
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
                    }

                    // =================================================
                    // 8. WALK RICORSIVO
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
                            Array.isArray(node)
                        ) {

                            tryPlace(node);

                            for (
                                var i = 0;
                                i < node.length;
                                i++
                            ) {

                                walk(
                                    node[i]
                                );
                            }

                            return;
                        }

                        if (
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
                    // 9. DEDUPLICAZIONE
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
                            p.name.toLowerCase() +
                            '|' +
                            p.lat.toFixed(6) +
                            '|' +
                            p.lng.toFixed(6);

                        if (
                            !seen[key]
                        ) {

                            seen[key] =
                                true;

                            unique.push(p);
                        }
                    }

                    places =
                        unique;

                    // =================================================
                    // 10. RISULTATO
                    // =================================================

                    log(
                        'LUOGHI VALIDI TROVATI: ' +
                        places.length
                    );

                    if (
                        places.length === 0
                    ) {

                        /*
                         * Fallback DOM.
                         *
                         * Questo serve nel caso in cui Google
                         * abbia già renderizzato i luoghi ma
                         * il payload non sia leggibile.
                         */

                        var domPlaces = [];

                        try {

                            var elements =
                                document.querySelectorAll(
                                    '[data-item-id]'
                                );

                            for (
                                var i = 0;
                                i < elements.length;
                                i++
                            ) {

                                var el =
                                    elements[i];

                                var itemId =
                                    el.getAttribute(
                                        'data-item-id'
                                    );

                                if (
                                    !itemId ||
                                    itemId.indexOf(
                                        'ChIJ'
                                    ) !== 0
                                ) {
                                    continue;
                                }

                                var text =
                                    clean(
                                        el.innerText
                                    );

                                if (
                                    validName(text)
                                ) {

                                    domPlaces.push({

                                        name:
                                            text,

                                        address:
                                            '',

                                        lat:
                                            NaN,

                                        lng:
                                            NaN
                                    });
                                }
                            }

                        } catch(e) {

                            log(
                                'DOM FALLBACK ERROR: ' +
                                e.message
                            );
                        }

                        if (
                            domPlaces.length > 0
                        ) {

                            /*
                             * Non mandiamo risultati senza
                             * coordinate al database.
                             */

                            log(
                                'DOM TROVATI: ' +
                                domPlaces.length
                            );
                        }

                        TravelPins.importError(
                            'La lista è stata caricata, ma Google non ha fornito coordinate utilizzabili. Riprova premendo SCANSIONA dopo qualche secondo.'
                        );

                        return;
                    }

                    // =================================================
                    // 11. INVIO
                    // =================================================

                    log(
                        'INVIO A KOTLIN: ' +
                        places.length
                    );

                    TravelPins.importPlaces(
                        JSON.stringify(
                            places
                        )
                    );

                } catch(e) {

                    log(
                        'GENERAL ERROR: ' +
                        e.message +
                        '\\n' +
                        e.stack
                    );

                    TravelPins.importError(
                        'Errore durante la scansione: ' +
                        e.message
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
         * Timeout di sicurezza.
         */

        handler.postDelayed(
            {

                if (importing) {

                    importing = false

                    progress.visibility =
                        ProgressBar.GONE

                    scanButton.isEnabled = true

                    showStatus(
                        "Scansione terminata senza risposta da Google Maps."
                    )

                    addLog(
                        "[TIMEOUT] Nessuna risposta dopo 20 secondi."
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

                /*
                 * Non modifichiamo fetch.
                 *
                 * Ci limitiamo a registrare le richieste
                 * nella performance API.
                 */

                try {

                    performance.clearResourceTimings();

                } catch(e) {}

            })();
            """.trimIndent(),
            null
        )
    }

    // ============================================================
    // LUOGHI
    // ============================================================

    private fun showPlaces() {

        output.removeAllViews()

        val title =
            TextView(this).apply {

                text =
                    if (places.isEmpty()) {
                        "📍 TravelPins"
                    } else {
                        "📍 ${places.size} luoghi"
                    }

                textSize = 27f
                setTextColor(Color.BLACK)

                setPadding(
                    0,
                    5,
                    0,
                    20
                )
            }

        output.addView(title)

        if (places.isEmpty()) {

            val empty =
                TextView(this).apply {

                    text =
                        "Condividi una lista di Google Maps con TravelPins per iniziare."

                    textSize = 17f
                    setTextColor(Color.GRAY)

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
                it.id == place.categoryId
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
                setTextColor(Color.BLACK)
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
                    setTextColor(Color.DKGRAY)

                    setPadding(
                        0,
                        7,
                        0,
                        7
                    )
                }

            card.addView(address)
        }

        val coordinates =
            TextView(this).apply {

                text =
                    "📍 %.6f, %.6f".format(
                        place.lat,
                        place.lng
                    )

                textSize = 12f
                setTextColor(Color.GRAY)

                setPadding(
                    0,
                    3,
                    0,
                    3
                )
            }

        card.addView(coordinates)

        val categoryText =
            TextView(this).apply {

                text =
                    if (category == null) {
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
                    chooseCategory(place)
                }
            }

        card.addView(categoryText)

        card.setOnClickListener {
            chooseCategory(place)
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

        if (categories.isEmpty()) {
            createCategory()
            return
        }

        val names =
            categories.map {
                "${it.icon} ${it.name}"
            }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Categoria")
            .setItems(names) {
                    _: android.content.DialogInterface,
                    which: Int ->

                place.categoryId =
                    categories[which].id

                savePlaces()
                showPlaces()
            }
            .setNegativeButton(
                "Nessuna categoria"
            ) {
                    _: android.content.DialogInterface,
                    _: Int ->

                place.categoryId = ""

                savePlaces()
                showPlaces()
            }
            .show()
    }

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

        categories.forEach { category ->

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

            row.addView(icon)

            row.addView(
                name,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            layout.addView(row)
        }

        val addButton =
            Button(this).apply {

                text =
                    "＋ Nuova categoria"

                setOnClickListener {
                    createCategory()
                }
            }

        layout.addView(addButton)

        AlertDialog.Builder(this)
            .setTitle("Categorie")
            .setView(layout)
            .setPositiveButton("Chiudi", null)
            .show()
    }

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

                setSingleLine(true)
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

        layout.addView(name)

        layout.addView(
            iconSpinner,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        AlertDialog.Builder(this)
            .setTitle("Nuova categoria")
            .setView(layout)
            .setPositiveButton("Crea") {
                    _: android.content.DialogInterface,
                    _: Int ->

                val categoryName =
                    name.text.toString().trim()

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

                categories.add(category)

                saveCategories()

                Toast.makeText(
                    this,
                    "Categoria creata",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(
                "Annulla",
                null
            )
            .show()
    }

    // ============================================================
    // CATEGORIE STORAGE
    // ============================================================

    private fun saveCategories() {

        val array = JSONArray()

        categories.forEach { category ->

            val obj = JSONObject()

            obj.put("id", category.id)
            obj.put("name", category.name)
            obj.put("color", category.color)
            obj.put("icon", category.icon)

            array.put(obj)
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

        if (raw.isNullOrBlank()) {

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
                JSONArray(raw)

            for (
                i in 0 until array.length()
            ) {

                val obj =
                    array.getJSONObject(i)

                categories.add(
                    Category(
                        id =
                            obj.getString("id"),

                        name =
                            obj.getString("name"),

                        color =
                            obj.getInt("color"),

                        icon =
                            obj.getString("icon")
                    )
                )
            }

        } catch (_: Exception) {

            categories.clear()
        }
    }

    // ============================================================
    // PLACES STORAGE
    // ============================================================

    private fun savePlaces() {

        val array = JSONArray()

        places.forEach { place ->

            val obj = JSONObject()

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

            array.put(obj)
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

        if (raw.isNullOrBlank()) {
            return
        }

        try {

            val array =
                JSONArray(raw)

            for (
                i in 0 until array.length()
            ) {

                val obj =
                    array.getJSONObject(i)

                val name =
                    obj.optString("name")

                val lat =
                    obj.optDouble(
                        "lat",
                        Double.NaN
                    )

                val lng =
                    obj.optDouble(
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

                            address =
                                obj.optString(
                                    "address"
                                ),

                            lat = lat,
                            lng = lng,

                            categoryId =
                                obj.optString(
                                    "category"
                                )
                        )
                    )
                }
            }

        } catch (_: Exception) {

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
                Uri.parse(intentUrl)

            val fallback =
                uri.getQueryParameter(
                    "S.browser_fallback_url"
                )

            if (!fallback.isNullOrBlank()) {

                webView.loadUrl(fallback)

                return
            }

            val marker =
                "S.browser_fallback_url="

            val index =
                intentUrl.indexOf(marker)

            if (index >= 0) {

                var fallbackText =
                    intentUrl.substring(
                        index + marker.length
                    )

                val end =
                    fallbackText.indexOf(
                        "#Intent"
                    )

                if (end >= 0) {

                    fallbackText =
                        fallbackText.substring(
                            0,
                            end
                        )
                }

                fallbackText =
                    Uri.decode(fallbackText)

                webView.loadUrl(
                    fallbackText
                )
            }

        } catch (e: Exception) {

            addLog(
                "[INTENT ERROR]\n" +
                        e.message
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

        val sharedText =
            intent.getStringExtra(
                Intent.EXTRA_TEXT
            )

        if (sharedText.isNullOrBlank()) {
            return
        }

        val match =
            Regex(
                """https?://\S+"""
            ).find(sharedText)

        if (match == null) {

            Toast.makeText(
                this,
                "Nessun link Google Maps trovato",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        var url =
            match.value

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
            AVVIO GOOGLE MAPS...
            """.trimIndent()
        )

        importing = false
        pageLoaded = false

        scanButton.isEnabled = false

        progress.visibility =
            ProgressBar.VISIBLE

        webView.visibility =
            WebView.VISIBLE

        showStatus(
            "Apertura della lista Google Maps…"
        )

        webView.loadUrl(url)
    }

    // ============================================================
    // NUOVO INTENT
    // ============================================================

    override fun onNewIntent(
        intent: Intent
    ) {

        super.onNewIntent(intent)

        setIntent(intent)

        handleIntent(intent)
    }

    // ============================================================
    // LOG
    // ============================================================

    private fun addLog(
        message: String
    ) {

        log.add(
            message.take(15000)
        )

        while (log.size > 50) {
            log.poll()
        }
    }

    // ============================================================
    // BACK
    // ============================================================

    @Suppress("DEPRECATION")
    override fun onBackPressed() {

        if (webView.canGoBack()) {

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
