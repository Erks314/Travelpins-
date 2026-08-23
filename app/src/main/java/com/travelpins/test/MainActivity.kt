package com.travelpins.test

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
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

    private val handler = Handler(Looper.getMainLooper())
    private val log = ConcurrentLinkedQueue<String>()

    private val places = ArrayList<Place>()
    private val categories = ArrayList<Category>()

    private var importing = false
    private var importFinished = false

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
                        """
                        NAVIGAZIONE:
                        $url
                        """.trimIndent()
                    )

                    if (
                        url.startsWith("intent://")
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

                    inspectRequest(request)

                    return null
                }

                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {

                    addLog(
                        """
                        ==============================
                        PAGINA CARICATA

                        $url

                        ==============================
                        """.trimIndent()
                    )

                    injectNetworkHook()

                    if (
                        !importing &&
                        !importFinished &&
                        looksLikeGoogleMapsPage(url)
                    ) {

                        handler.postDelayed(
                            {

                                if (
                                    !importFinished
                                ) {

                                    importing = true

                                    scanGoogleList()
                                }

                            },
                            1800
                        )
                    }
                }
            }
    }

    // ============================================================
    // GOOGLE BRIDGE
    // ============================================================

    inner class TravelPinsBridge {

        @JavascriptInterface
        fun log(message: String?) {

            if (!message.isNullOrBlank()) {

                addLog(
                    message
                )
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

                        addLog(
                            "IMPORT PLACES: array vuoto"
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
                                    address =
                                        item.optString(
                                            "address"
                                        ),
                                    lat = lat,
                                    lng = lng
                                )
                            )
                        }
                    }

                    if (
                        places.isEmpty()
                    ) {

                        addLog(
                            "NESSUN LUOGO VALIDO NELL'IMPORT"
                        )

                        return@runOnUiThread
                    }

                    savePlaces()

                    importFinished = true
                    importing = false

                    progress.visibility =
                        ProgressBar.GONE

                    webView.visibility =
                        WebView.GONE

                    showPlaces()

                    Toast.makeText(
                        this@MainActivity,
                        "${places.size} luoghi importati",
                        Toast.LENGTH_SHORT
                    ).show()

                    addLog(
                        """
                        ==============================
                        IMPORTAZIONE COMPLETATA

                        LUOGHI:
                        ${places.size}

                        ==============================
                        """.trimIndent()
                    )

                } catch (
                    e: Exception
                ) {

                    importing = false

                    addLog(
                        "ERRORE importPlaces: ${e.message}"
                    )

                    progress.visibility =
                        ProgressBar.GONE

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

    private fun looksLikeGoogleMapsPage(
        url: String
    ): Boolean {

        val lower =
            url.lowercase()

        return lower.contains(
            "google.com/maps"
        ) ||
        lower.contains(
            "google.it/maps"
        ) ||
        lower.contains(
            "maps.google.com"
        ) ||
        lower.contains(
            "consent.google.com"
        ) ||
        lower.contains(
            "/local/userlists/"
        )
    }

    // ============================================================
    // RICONOSCIMENTO LISTA
    // ============================================================

    private fun isGoogleListUrl(
        url: String
    ): Boolean {

        val lower =
            url.lowercase()

        val direct =
            lower.contains(
                "/local/userlists/list/"
            )

        val mapsData =
            lower.contains(
                "/maps/@/data="
            ) &&
            lower.contains(
                "!11m2!2s"
            )

        val userList =
            lower.contains(
                "userlists"
            )

        return direct ||
               mapsData ||
               userList
    }

    // ============================================================
    // IMPORTAZIONE
    // ============================================================

    private fun scanGoogleList() {

        runOnUiThread {

            progress.visibility =
                ProgressBar.VISIBLE

            webView.visibility =
                WebView.VISIBLE
        }

        addLog(
            """
            ==============================
            AVVIO IMPORTAZIONE GOOGLE MAPS

            ==============================
            """.trimIndent()
        )

        handler.postDelayed(
            {

                webView.evaluateJavascript(
                    buildImportJavascript(),
                    null
                )

            },
            500
        )
    }

    // ============================================================
    // JAVASCRIPT IMPORT
    // ============================================================

    private fun buildImportJavascript(): String {

        return """
        (async function() {

            try {

                TravelPins.log(
                    '===== TRAVELPINS IMPORT START ====='
                );

                var currentUrl =
                    window.location.href;

                TravelPins.log(
                    'URL CORRENTE: ' +
                    currentUrl
                );

                // =====================================================
                // RACCOLTA POSSIBILI URL
                // =====================================================

                var urls = [];

                function addUrl(value) {

                    if (
                        !value ||
                        typeof value !== 'string'
                    ) {
                        return;
                    }

                    if (
                        urls.indexOf(value) < 0
                    ) {

                        urls.push(value);
                    }
                }

                addUrl(currentUrl);

                try {

                    var links =
                        document.querySelectorAll(
                            'a[href]'
                        );

                    for (
                        var i = 0;
                        i < links.length;
                        i++
                    ) {

                        addUrl(
                            links[i].href
                        );
                    }

                } catch(e) {}

                // =====================================================
                // CERCA LIST ID
                // =====================================================

                var listId = '';

                function findListId(
                    value
                ) {

                    if (
                        !value ||
                        typeof value !== 'string'
                    ) {

                        return '';
                    }

                    var match;

                    // !11m2!2sLIST_ID

                    match =
                        value.match(
                            /!11m2!2s([^!&]+)/i
                        );

                    if (
                        match &&
                        match[1]
                    ) {

                        return match[1];
                    }

                    // /local/userlists/list/LIST_ID

                    match =
                        value.match(
                            /\/local\/userlists\/list\/([^?\/&#]+)/i
                        );

                    if (
                        match &&
                        match[1]
                    ) {

                        return match[1];
                    }

                    // 2sLIST_ID

                    match =
                        value.match(
                            /(?:^|[!\/])2s([A-Za-z0-9_-]{20,})/i
                        );

                    if (
                        match &&
                        match[1]
                    ) {

                        return match[1];
                    }

                    // userlists/.../LIST_ID

                    match =
                        value.match(
                            /userlists[^A-Za-z0-9_-]+([A-Za-z0-9_-]{20,})/i
                        );

                    if (
                        match &&
                        match[1]
                    ) {

                        return match[1];
                    }

                    return '';
                }

                for (
                    var u = 0;
                    u < urls.length;
                    u++
                ) {

                    listId =
                        findListId(
                            urls[u]
                        );

                    if (
                        listId
                    ) {

                        TravelPins.log(
                            'LIST ID TROVATO IN URL: ' +
                            urls[u]
                        );

                        break;
                    }
                }

                // =====================================================
                // CERCA NEL DOCUMENTO
                // =====================================================

                if (!listId) {

                    try {

                        var html =
                            document.documentElement
                                .innerHTML;

                        listId =
                            findListId(
                                html
                            );

                    } catch(e) {}
                }

                TravelPins.log(
                    'LIST ID FINALE: ' +
                    (
                        listId ||
                        'NON TROVATO'
                    )
                );

                if (!listId) {

                    TravelPins.log(
                        'ERRORE: impossibile determinare LIST ID'
                    );

                    return;
                }

                // =====================================================
                // FUNZIONE PARSER
                // =====================================================

                function cleanString(
                    value
                ) {

                    if (
                        typeof value !== 'string'
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

                function isNumber(v) {

                    return (
                        typeof v === 'number' &&
                        isFinite(v)
                    );
                }

                function looksLikeLatLng(
                    lat,
                    lng
                ) {

                    return (
                        isNumber(lat) &&
                        isNumber(lng) &&
                        Math.abs(lat) <= 90 &&
                        Math.abs(lng) <= 180
                    );
                }

                function usefulName(
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

                var places = [];

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
                            !usefulName(
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

                function walk(
                    node
                ) {

                    if (!node) {
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

                // =====================================================
                // TENTATIVO GETLIST
                // =====================================================

                var endpoints = [];

                function addEndpoint(
                    value
                ) {

                    if (
                        endpoints.indexOf(value) < 0
                    ) {

                        endpoints.push(value);
                    }
                }

                var encodedId =
                    encodeURIComponent(
                        listId
                    );

                // Metodo che aveva già funzionato
                addEndpoint(
                    '/maps/preview/entitylist/getlist' +
                    '?authuser=0' +
                    '&hl=it' +
                    '&gl=it' +
                    '&pb=' +
                    '!1m4!1s' +
                    encodedId +
                    '!2e1!3m1!1e1!2e2!3e3!4i500!8i3!16b1'
                );

                // Variante senza encodeURIComponent
                addEndpoint(
                    '/maps/preview/entitylist/getlist' +
                    '?authuser=0' +
                    '&hl=it' +
                    '&gl=it' +
                    '&pb=' +
                    '!1m4!1s' +
                    listId +
                    '!2e1!3m1!1e1!2e2!3e3!4i500!8i3!16b1'
                );

                var success = false;

                for (
                    var ep = 0;
                    ep < endpoints.length;
                    ep++
                ) {

                    try {

                        TravelPins.log(
                            'TENTATIVO GETLIST #' +
                            (ep + 1)
                        );

                        TravelPins.log(
                            endpoints[ep]
                        );

                        var response =
                            await fetch(
                                endpoints[ep],
                                {
                                    method: 'GET',
                                    credentials: 'include',
                                    cache: 'no-store'
                                }
                            );

                        TravelPins.log(
                            'HTTP: ' +
                            response.status
                        );

                        var raw =
                            await response.text();

                        TravelPins.log(
                            'RISPOSTA: ' +
                            raw.length +
                            ' caratteri'
                        );

                        if (
                            !raw ||
                            raw.length < 10
                        ) {

                            continue;
                        }

                        // =================================================
                        // XSSI
                        // =================================================

                        if (
                            raw.indexOf(
                                ")]}'"
                            ) === 0
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

                        var data;

                        try {

                            data =
                                JSON.parse(
                                    raw
                                );

                        } catch(e) {

                            TravelPins.log(
                                'JSON FALLITO: ' +
                                e.message
                            );

                            continue;
                        }

                        places = [];

                        walk(data);

                        TravelPins.log(
                            'PLACE TROVATI: ' +
                            places.length
                        );

                        if (
                            places.length > 0
                        ) {

                            success = true;

                            break;
                        }

                    } catch(e) {

                        TravelPins.log(
                            'ERRORE GETLIST: ' +
                            e.message
                        );
                    }
                }

                // =====================================================
                // DEDUPLICAZIONE
                // =====================================================

                if (
                    success
                ) {

                    var unique = [];
                    var seen = {};

                    for (
                        var p = 0;
                        p < places.length;
                        p++
                    ) {

                        var place =
                            places[p];

                        var key =
                            place.name +
                            '|' +
                            place.lat +
                            '|' +
                            place.lng;

                        if (
                            !seen[key]
                        ) {

                            seen[key] = true;

                            unique.push(
                                place
                            );
                        }
                    }

                    places =
                        unique;

                    TravelPins.log(
                        'PLACE UNICI: ' +
                        places.length
                    );

                    TravelPins.importPlaces(
                        JSON.stringify(
                            places
                        )
                    );

                    TravelPins.log(
                        '===== IMPORT COMPLETATO ====='
                    );

                    return;
                }

                // =====================================================
                // FALLBACK: CERCA DATI GIÀ CARICATI NELLA PAGINA
                // =====================================================

                TravelPins.log(
                    'GETLIST NON HA RESTITUITO LUOGHI.'
                );

                TravelPins.log(
                    'AVVIO FALLBACK DATI PAGINA.'
                );

                try {

                    var text =
                        document.documentElement
                            .innerText || '';

                    TravelPins.log(
                        'TESTO PAGINA: ' +
                        text.length +
                        ' caratteri'
                    );

                } catch(e) {}

                TravelPins.log(
                    '===== GOOGLE MAPS NON HA RESTITUITO LA LISTA ====='
                );

            } catch(e) {

                TravelPins.log(
                    'ERRORE GENERALE IMPORT: ' +
                    e.message
                );

            }

        })();
        """.trimIndent()
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

                        try {

                            var input =
                                arguments[0];

                            var url =
                                typeof input ===
                                'string'
                                ? input
                                : input.url;

                            if (
                                url &&
                                (
                                    url.indexOf(
                                        'entitylist'
                                    ) >= 0 ||
                                    url.indexOf(
                                        'userlists'
                                    ) >= 0
                                )
                            ) {

                                TravelPins.log(
                                    'FETCH GOOGLE: ' +
                                    url
                                );
                            }

                        } catch(e) {}

                        return originalFetch.apply(
                            this,
                            arguments
                        );
                    };

                TravelPins.log(
                    'NETWORK HOOK INSTALLATO'
                );

            })();
            """.trimIndent(),
            null
        )
    }

    // ============================================================
    // REQUEST MONITOR
    // ============================================================

    private fun inspectRequest(
        request: WebResourceRequest
    ) {

        val url =
            request.url.toString()

        val lower =
            url.lowercase()

        if (
            lower.contains("entitylist") ||
            lower.contains("userlists")
        ) {

            addLog(
                """
                GOOGLE REQUEST:
                ${request.method}
                $url
                """.trimIndent()
            )
        }
    }

    // ============================================================
    // VISUALIZZAZIONE LUOGHI
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

        if (
            places.isEmpty()
        ) {

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
                    _: DialogInterface,
                    which: Int ->

                place.categoryId =
                    categories[which].id

                savePlaces()

                showPlaces()
            }
            .setNegativeButton(
                "Nessuna categoria"
            ) {
                    _: DialogInterface,
                    _: Int ->

                place.categoryId =
                    ""

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
                "Chiudi"
            ) {
                    _: DialogInterface,
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
                "⭐",
                "🌿",
                "⛪",
                "🎭",
                "🏛️"
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
                    _: DialogInterface,
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
                    _: DialogInterface,
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

                addLog(
                    "FALLBACK GOOGLE: $fallback"
                )

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
                        index + marker.length
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

                addLog(
                    "FALLBACK GOOGLE: $fallbackText"
                )

                webView.loadUrl(
                    fallbackText
                )
            }

        } catch (
            e: Exception
        ) {

            addLog(
                "ERRORE GOOGLE INTENT: ${e.message}"
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

            Toast.makeText(
                this,
                "Nessun contenuto ricevuto",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        addLog(
            """
            ==============================
            CONDIVISIONE RICEVUTA

            $sharedText

            ==============================
            """.trimIndent()
        )

        val match =
            Regex(
                """https?://[^\s]+"""
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

        url =
            url
                .trim()
                .trimEnd(
                    '.',
                    ',',
                    ')',
                    ']'
                )

        importing = false
        importFinished = false

        progress.visibility =
            ProgressBar.VISIBLE

        webView.visibility =
            WebView.VISIBLE

        addLog(
            """
            ==============================
            APERTURA GOOGLE MAPS

            $url

            ==============================
            """.trimIndent()
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

        super.onNewIntent(intent)

        setIntent(intent)

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
            message
        )

        while (
            log.size > 80
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
