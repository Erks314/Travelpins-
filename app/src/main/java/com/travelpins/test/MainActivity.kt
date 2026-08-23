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
import android.view.View
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

        createWebView()
        createInterface()

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
            visibility = View.GONE
        }

        root.addView(toolbar)

        root.addView(
            progress,
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

        /*
         * IMPORTANTISSIMO:
         *
         * Il WebView viene aggiunto alla gerarchia.
         *
         * Rimane GONE normalmente e viene usato solo
         * durante l'importazione.
         */
        webView.visibility = View.GONE

        root.addView(
            webView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
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

            allowFileAccess = true
            allowContentAccess = true

            userAgentString =
                "Mozilla/5.0 (Linux; Android 10) " +
                "AppleWebKit/537.36 " +
                "(KHTML, like Gecko) " +
                "Chrome/131.0.0.0 " +
                "Mobile Safari/537.36"
        }

        CookieManager.getInstance().setAcceptCookie(true)

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
                        "NAVIGAZIONE: $url"
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

                    return null
                }

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

                    addLog(
                        "PAGINA INIZIATA: $url"
                    )

                    /*
                     * Il link condiviso può passare
                     * attraverso più redirect.
                     *
                     * Non facciamo partire l'importazione
                     * qui: aspettiamo onPageFinished.
                     */
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
                        "PAGINA FINITA: $url"
                    )

                    /*
                     * Iniettiamo il codice ad ogni pagina.
                     */
                    injectNetworkHook()

                    /*
                     * Gestione automatica del consenso Google.
                     */
                    if (
                        url.contains(
                            "consent.google.",
                            true
                        )
                    ) {

                        handler.postDelayed(
                            {
                                acceptGoogleConsent()
                            },
                            700
                        )

                        return
                    }

                    /*
                     * Dopo il caricamento della pagina
                     * proviamo a capire se siamo arrivati
                     * a Google Maps.
                     */
                    if (
                        isGoogleMapsPage(url) &&
                        importing &&
                        !scanStarted
                    ) {

                        /*
                         * Aspettiamo un attimo che Google
                         * completi eventuali redirect/
                         * inizializzazioni interne.
                         */
                        handler.postDelayed(
                            {

                                if (
                                    importing &&
                                    !scanStarted
                                ) {

                                    scanGoogleList()
                                }

                            },
                            1500
                        )
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError
                ) {

                    super.onReceivedError(
                        view,
                        request,
                        error
                    )

                    if (
                        request.isForMainFrame
                    ) {

                        addLog(
                            "WEBVIEW ERROR: " +
                            error.description
                        )
                    }
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
                            View.GONE

                        Toast.makeText(
                            this@MainActivity,
                            "Nessun luogo trovato",
                            Toast.LENGTH_LONG
                        ).show()

                        return@runOnUiThread
                    }

                    /*
                     * NON cancelliamo subito i luoghi
                     * se l'importazione fallisce.
                     */
                    val imported =
                        ArrayList<Place>()

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

                            imported.add(
                                Place(
                                    name = name,
                                    address = address,
                                    lat = lat,
                                    lng = lng
                                )
                            )
                        }
                    }

                    if (
                        imported.isEmpty()
                    ) {

                        importing = false
                        scanStarted = false

                        progress.visibility =
                            View.GONE

                        Toast.makeText(
                            this@MainActivity,
                            "Nessun luogo valido trovato",
                            Toast.LENGTH_LONG
                        ).show()

                        return@runOnUiThread
                    }

                    places.clear()
                    places.addAll(imported)

                    savePlaces()

                    progress.visibility =
                        View.GONE

                    webView.visibility =
                        View.GONE

                    importing = false
                    scanStarted = false

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
                        View.GONE

                    webView.visibility =
                        View.GONE

                    Toast.makeText(
                        this@MainActivity,
                        "Errore importazione: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ============================================================
    // RICONOSCIMENTO GOOGLE MAPS
    // ============================================================

    private fun isGoogleMapsPage(
        url: String
    ): Boolean {

        val lower =
            url.lowercase()

        return lower.contains(
            "google.com/maps"
        ) ||
        lower.contains(
            "maps.google."
        ) ||
        lower.contains(
            "google.it/maps"
        ) ||
        lower.contains(
            "/local/userlists/"
        ) ||
        lower.contains(
            "/maps/preview/"
        )
    }

    // ============================================================
    // SCANSIONE
    // ============================================================

    private fun scanGoogleList() {

        if (
            !importing ||
            scanStarted
        ) {
            return
        }

        scanStarted = true

        addLog(
            "AVVIO SCANSIONE AUTOMATICA"
        )

        val javascript = """

            (async function() {

                try {

                    TravelPins.log(
                        'URL ANALIZZATO: ' +
                        window.location.href
                    );

                    // =================================================
                    // 1. URL
                    // =================================================

                    var currentUrl =
                        window.location.href;

                    // =================================================
                    // 2. LIST ID
                    // =================================================

                    var listId = '';

                    var match =
                        currentUrl.match(
                            /!11m2!2s([^!&]+)/i
                        );

                    if (match) {

                        listId =
                            match[1];
                    }

                    if (!listId) {

                        match =
                            currentUrl.match(
                                /\/local\/userlists\/list\/([^?\/]+)/i
                            );

                        if (match) {

                            listId =
                                match[1];
                        }
                    }

                    if (!listId) {

                        match =
                            currentUrl.match(
                                /2s([A-Za-z0-9_-]{20,})/
                            );

                        if (match) {

                            listId =
                                match[1];
                        }
                    }

                    /*
                     * Alcuni URL Google Maps possono
                     * avere il parametro list=.
                     */
                    if (!listId) {

                        match =
                            currentUrl.match(
                                /[?&]list=([^&]+)/i
                            );

                        if (match) {

                            listId =
                                decodeURIComponent(
                                    match[1]
                                );
                        }
                    }

                    if (!listId) {

                        TravelPins.log(
                            'LIST ID NON TROVATO'
                        );

                        /*
                         * Riproviamo una volta dopo
                         * che Google ha terminato
                         * eventuali redirect.
                         */
                        setTimeout(
                            function() {

                                TravelPins.log(
                                    'RIPROVO ESTRAZIONE LIST ID'
                                );

                                window.location.reload();

                            },
                            1200
                        );

                        return;
                    }

                    TravelPins.log(
                        'LIST ID: ' +
                        listId
                    );

                    // =================================================
                    // 3. GETLIST
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
                        '/maps/preview/entitylist/getlist' +
                        '?authuser=0' +
                        '&hl=it' +
                        '&gl=it' +
                        '&pb=' +
                        pb;

                    TravelPins.log(
                        'CHIAMATA GETLIST'
                    );

                    var response =
                        await fetch(
                            endpoint,
                            {
                                method: 'GET',
                                credentials: 'include',
                                cache: 'no-store'
                            }
                        );

                    TravelPins.log(
                        'GETLIST HTTP: ' +
                        response.status
                    );

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

                        TravelPins.log(
                            'GETLIST VUOTA'
                        );

                        return;
                    }

                    // =================================================
                    // 4. XSSI
                    // =================================================

                    if (
                        raw.indexOf(")]}'") === 0
                    ) {

                        raw =
                            raw.substring(4);

                        if (
                            raw.charAt(0) === '\\n'
                        ) {

                            raw =
                                raw.substring(1);
                        }
                    }

                    // =================================================
                    // 5. JSON
                    // =================================================

                    var data;

                    try {

                        data =
                            JSON.parse(raw);

                    } catch(e) {

                        TravelPins.log(
                            'JSON PARSE FALLITO: ' +
                            e.message
                        );

                        return;
                    }

                    TravelPins.log(
                        'JSON PARSATO'
                    );

                    // =================================================
                    // 6. UTILITIES
                    // =================================================

                    var places = [];

                    function isNumber(v) {

                        return (
                            typeof v === 'number' &&
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
                                /\\s+/g,
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
                    // 7. PLACE PARSER
                    // =================================================

                    function tryKnownPlace(
                        x
                    ) {

                        try {

                            if (
                                !Array.isArray(x) ||
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

                            var address = '';

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

                        } catch(e) {}
                    }

                    // =================================================
                    // 8. WALK
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
                    // 9. DUPLICATI
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

                            seen[key] = true;

                            unique.push(p);
                        }
                    }

                    TravelPins.log(
                        'LUOGHI TROVATI: ' +
                        unique.length
                    );

                    // =================================================
                    // 10. INVIO
                    // =================================================

                    if (
                        unique.length > 0
                    ) {

                        TravelPins.importPlaces(
                            JSON.stringify(
                                unique
                            )
                        );

                    } else {

                        TravelPins.log(
                            'NESSUN LUOGO TROVATO'
                        );
                    }

                } catch(e) {

                    TravelPins.log(
                        'ERRORE SCANSIONE: ' +
                        e.message
                    );
                }

            })();

        """.trimIndent()

        webView.evaluateJavascript(
            javascript
        ) { result ->

            addLog(
                "CALLBACK SCANSIONE: $result"
            )
        }

        /*
         * Timeout di sicurezza.
         *
         * Se dopo 15 secondi Google non ha
         * restituito nulla, non lasciamo l'app
         * bloccata per sempre.
         */
        handler.postDelayed(
            {

                if (
                    importing &&
                    scanStarted
                ) {

                    scanStarted = false
                    importing = false

                    progress.visibility =
                        View.GONE

                    webView.visibility =
                        View.GONE

                    Toast.makeText(
                        this,
                        "Google Maps non ha restituito la lista. Riprova a condividere il link.",
                        Toast.LENGTH_LONG
                    ).show()
                }

            },
            15000
        )
    }

    // ============================================================
    // CONSENSO GOOGLE AUTOMATICO
    // ============================================================

    private fun acceptGoogleConsent() {

        val javascript = """

            (function() {

                try {

                    var elements =
                        document.querySelectorAll(
                            'button, div[role="button"], input'
                        );

                    for (
                        var i = 0;
                        i < elements.length;
                        i++
                    ) {

                        var e =
                            elements[i];

                        var text =
                            (
                                e.innerText ||
                                e.value ||
                                e.getAttribute(
                                    'aria-label'
                                ) ||
                                ''
                            )
                            .trim()
                            .toLowerCase();

                        if (
                            text ===
                            'accetta tutto' ||
                            text ===
                            'accetta' ||
                            text ===
                            'accept all' ||
                            text ===
                            'accept'
                        ) {

                            e.click();

                            TravelPins.log(
                                'CONSENSO GOOGLE ACCETTATO AUTOMATICAMENTE'
                            );

                            return;
                        }
                    }

                    TravelPins.log(
                        'PULSANTE CONSENSO NON TROVATO'
                    );

                } catch(e) {

                    TravelPins.log(
                        'ERRORE CONSENSO: ' +
                        e.message
                    );
                }

            })();

        """.trimIndent()

        webView.evaluateJavascript(
            javascript,
            null
        )

        /*
         * Dopo il consenso riproviamo
         * automaticamente.
         */
        handler.postDelayed(
            {

                if (
                    importing &&
                    !scanStarted
                ) {

                    scanGoogleList()
                }

            },
            1500
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

    // ============================================================
    // CARD LUOGO
    // ============================================================

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

                setOnClickListener {
                    chooseCategory(place)
                }
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
                    chooseCategory(place)
                }
            }

        card.addView(categoryText)

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
    // SCELTA CATEGORIA
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

                place.categoryId = ""

                savePlaces()

                showPlaces()
            }
            .show()
    }

    // ============================================================
    // CATEGORIE
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
            .setTitle(
                "Categorie"
            )
            .setView(layout)
            .setPositiveButton(
                "Chiudi",
                null
            )
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

                setSingleLine()
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
                "⭐",
                "🌿"
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
            .setTitle(
                "Nuova categoria"
            )
            .setView(layout)
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
                            getCategoryColor(
                                categories.size
                            ),

                        icon =
                            icon
                    )

                categories.add(
                    category
                )

                saveCategories()

                showPlaces()

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
    // COLORI CATEGORIE
    // ============================================================

    private fun getCategoryColor(
        index: Int
    ): Int {

        val colors =
            intArrayOf(

                Color.rgb(
                    30,
                    100,
                    200
                ),

                Color.rgb(
                    220,
                    80,
                    50
                ),

                Color.rgb(
                    40,
                    150,
                    70
                ),

                Color.rgb(
                    140,
                    70,
                    190
                ),

                Color.rgb(
                    220,
                    140,
                    30
                ),

                Color.rgb(
                    20,
                    150,
                    170
                ),

                Color.rgb(
                    210,
                    50,
                    120
                ),

                Color.rgb(
                    90,
                    90,
                    90
                )
            )

        return colors[
            index % colors.size
        ]
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
                JSONArray(raw)

            for (
                i in 0 until array.length()
            ) {

                val obj =
                    array.getJSONObject(i)

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
                JSONArray(raw)

            for (
                i in 0 until array.length()
            ) {

                val obj =
                    array.getJSONObject(i)

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
                "ERRORE GOOGLE INTENT"
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
         * Rimuove eventuali caratteri finali
         * provenienti dal testo condiviso.
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
            "LINK RICEVUTO: $url"
        )

        importing = true
        scanStarted = false

        progress.visibility =
            View.VISIBLE

        /*
         * Il WebView ora è realmente nella
         * gerarchia dell'Activity.
         */
        webView.visibility =
            View.VISIBLE

        /*
         * Puliamo la pagina precedente.
         */
        webView.stopLoading()

        webView.clearHistory()

        /*
         * Partiamo direttamente dal link
         * ricevuto.
         */
        webView.loadUrl(url)

        /*
         * Timeout generale.
         */
        handler.postDelayed(
            {

                if (
                    importing &&
                    !scanStarted
                ) {

                    addLog(
                        "ATTESA GOOGLE MAPS OLTRE IL TEMPO PREVISTO"
                    )
                }

            },
            8000
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
            message
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

    @Suppress("DEPRECATION")
    override fun onBackPressed() {

        if (
            webView.visibility ==
            View.VISIBLE &&
            webView.canGoBack()
        ) {

            webView.goBack()

        } else {

            webView.visibility =
                View.GONE

            progress.visibility =
                View.GONE

            importing = false
            scanStarted = false

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
