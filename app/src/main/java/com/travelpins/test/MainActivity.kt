package com.travelpins.test

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.ConcurrentLinkedQueue

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var output: TextView
    private lateinit var consentButton: Button
    private lateinit var scanButton: Button

    private val log = ConcurrentLinkedQueue<String>()
    private val handler = Handler(Looper.getMainLooper())

    // ============================================================
    // JAVASCRIPT -> KOTLIN
    // ============================================================

    inner class TravelPinsBridge {

        @JavascriptInterface
        fun log(message: String?) {

            if (message.isNullOrBlank()) {
                return
            }

            addLog(
                """
                [JAVASCRIPT]

                $message

                """.trimIndent()
            )
        }

        @JavascriptInterface
        fun network(
            type: String?,
            method: String?,
            url: String?,
            data: String?
        ) {

            val currentUrl = url ?: ""

            val interesting =
                currentUrl.contains("userlists", true) ||
                currentUrl.contains("listview", true) ||
                currentUrl.contains("batchexecute", true) ||
                currentUrl.contains("entitylist", true) ||
                currentUrl.contains("rpc", true)

            if (!interesting) {
                return
            }

            val limitedData =
                (data ?: "").take(5000)

            addLog(
                """
                ==============================
                NETWORK $type

                METHOD:
                ${method ?: ""}

                URL:
                $currentUrl

                DATA:
                $limitedData

                ==============================
                """.trimIndent()
            )
        }
    }

    // ============================================================
    // ON CREATE
    // ============================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        createInterface()
        createWebView()

        handleIntent(intent)
    }

    // ============================================================
    // INTERFACCIA
    // ============================================================

    private fun createInterface() {

        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 12, 12, 12)
            }

        val toolbar =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

        val copyButton =
            Button(this).apply {

                text = "COPIA TUTTO"

                setOnClickListener {

                    val clipboard =
                        getSystemService(
                            Context.CLIPBOARD_SERVICE
                        ) as ClipboardManager

                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            "TravelPins",
                            output.text.toString()
                        )
                    )

                    Toast.makeText(
                        this@MainActivity,
                        "Copiato!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        val clearButton =
            Button(this).apply {

                text = "PULISCI"

                setOnClickListener {

                    log.clear()

                    output.text =
                        """
                        TRAVELPINS NETWORK MONITOR

                        Monitor pulito.
                        """.trimIndent()
                }
            }

        consentButton =
            Button(this).apply {

                text = "ACCETTA GOOGLE"

                visibility = Button.GONE

                setOnClickListener {
                    acceptGoogleConsent()
                }
            }

        scanButton =
            Button(this).apply {

                text = "SCANSIONA"

                visibility = Button.GONE

                isEnabled = false

                setOnClickListener {
                    scanGoogleList()
                }
            }

        toolbar.addView(
            copyButton,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        toolbar.addView(
            clearButton,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        toolbar.addView(
            consentButton,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        toolbar.addView(
            scanButton,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        output =
            TextView(this).apply {

                textSize = 13f

                setTextIsSelectable(true)

                setPadding(
                    12,
                    12,
                    12,
                    30
                )

                text =
                    """
                    TRAVELPINS NETWORK MONITOR

                    In attesa del link...
                    """.trimIndent()
            }

        val scroll =
            ScrollView(this).apply {
                addView(output)
            }

        root.addView(toolbar)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
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

                    inspectNavigation(url)

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
                        url.contains(
                            "consent.google.com",
                            true
                        )
                    ) {

                        consentButton.visibility =
                            Button.VISIBLE

                        scanButton.visibility =
                            Button.GONE

                        scanButton.isEnabled =
                            false

                        addLog(
                            """
                            ==============================
                            CONSENSO GOOGLE RILEVATO

                            Premi:
                            ACCETTA GOOGLE

                            ==============================
                            """.trimIndent()
                        )

                    } else {

                        consentButton.visibility =
                            Button.GONE
                    }

                    // ------------------------------------------------
                    // RICONOSCIMENTO LISTA
                    // ------------------------------------------------

                    if (
                        isGoogleListUrl(url)
                    ) {

                        addLog(
                            """
                            ==============================
                            LISTA GOOGLE MAPS RILEVATA

                            URL LISTA:

                            $url

                            ==============================
                            Premere SCANSIONA.

                            """.trimIndent()
                        )

                        scanButton.visibility =
                            Button.VISIBLE

                        scanButton.isEnabled =
                            true
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail
                ): Boolean {

                    addLog(
                        """
                        ==============================
                        WEBVIEW RENDERER TERMINATO

                        CRASH:
                        ${detail.didCrash()}

                        ==============================
                        """.trimIndent()
                    )

                    return true
                }
            }
    }

    // ============================================================
    // RICONOSCIMENTO LISTA
    // ============================================================

    private fun isGoogleListUrl(
        url: String
    ): Boolean {

        return url.contains(
            "/local/userlists/list/",
            true
        ) ||
        url.contains(
            "/maps/@/data=",
            true
        ) &&
        url.contains(
            "!11m2!2s",
            true
        )
    }

    // ============================================================
    // SCANSIONE
    // ============================================================

    private fun scanGoogleList() {

        if (!scanButton.isEnabled) {
            return
        }

        scanButton.isEnabled = false

        addLog(
            """
            ==============================
            SCANSIONE LISTA AVVIATA

            Metodo:
            entitylist/getlist

            NON utilizzo il DOM.

            ==============================
            """.trimIndent()
        )

        inspectGoogleListPage()
    }

    // ============================================================
    // LETTURA DIRETTA ENTITYLIST
    // ============================================================

    private fun inspectGoogleListPage() {

        handler.postDelayed({

            val javascript = """

                (async function() {

                    try {

                        // =================================================
                        // 1. URL CORRENTE
                        // =================================================

                        var currentUrl =
                            window.location.href;

                        TravelPins.log(
                            'URL ANALIZZATO: ' +
                            currentUrl
                        );

                        // =================================================
                        // 2. ESTRAZIONE LIST ID
                        // =================================================

                        var listId = '';

                        // Caso:
                        // !11m2!2sLIST_ID

                        var match =
                            currentUrl.match(
                                /!11m2!2s([^!&]+)/i
                            );

                        if (match) {

                            listId =
                                match[1];

                        }

                        // Fallback:
                        // /local/userlists/list/LIST_ID

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

                        // Fallback generico 2s

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

                        TravelPins.log(
                            'LIST ID: ' +
                            (
                                listId ||
                                'NON TROVATO'
                            )
                        );

                        if (!listId) {

                            TravelPins.log(
                                'ERRORE: LIST ID NON TROVATO'
                            );

                            return;
                        }

                        // =================================================
                        // 3. COSTRUZIONE GETLIST
                        // =================================================

                        var pb =
                            '!1m4' +
                            '!1s' +
                            encodeURIComponent(listId) +
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
                            'GETLIST URL: ' +
                            endpoint
                        );

                        // =================================================
                        // 4. FETCH GOOGLE
                        // =================================================

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
                                'RISPOSTA GETLIST VUOTA'
                            );

                            return;
                        }

                        // =================================================
                        // 5. MOSTRA INIZIO RISPOSTA
                        // =================================================

                        TravelPins.log(
                            'GETLIST RAW START:\\n' +
                            raw.substring(
                                0,
                                3500
                            )
                        );

                        // =================================================
                        // 6. RIMOZIONE XSSI
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
                                cleaned.charAt(0) === '\\n'
                            ) {

                                cleaned =
                                    cleaned.substring(
                                        1
                                    );
                            }
                        }

                        // =================================================
                        // 7. PARSE JSON
                        // =================================================

                        var data;

                        try {

                            data =
                                JSON.parse(
                                    cleaned
                                );

                            TravelPins.log(
                                'JSON PARSATO CORRETTAMENTE'
                            );

                        } catch(e) {

                            TravelPins.log(
                                'JSON PARSE FALLITO: ' +
                                e.message
                            );

                            return;
                        }

                        // =================================================
                        // 8. UTILITIES
                        // =================================================

                        var places = [];

                        function isNumber(v) {

                            return typeof v ===
                                   'number' &&
                                   isFinite(v);
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
                        // 9. PARSER PRINCIPALE CONOSCIUTO
                        //
                        // Google usa una struttura del tipo:
                        //
                        // [ ...,
                        //   [
                        //      ...,
                        //      [PLACE, PLACE, ...]
                        //   ]
                        // ]
                        //
                        // Il record contiene coordinate in:
                        //
                        // x[1][5][2]
                        // x[1][5][3]
                        //
                        // e nome in:
                        //
                        // x[2]
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

                                var placeId =
                                    '';

                                // Cerca un eventuale
                                // place ID nelle strutture
                                // vicine.

                                function findId(
                                    node
                                ) {

                                    if (
                                        placeId ||
                                        !node
                                    ) {
                                        return;
                                    }

                                    if (
                                        typeof node ===
                                        'string'
                                    ) {

                                        if (
                                            node.length >
                                            15 &&
                                            node.length <
                                            200 &&
                                            (
                                                node.indexOf(
                                                    'ChIJ'
                                                ) === 0 ||
                                                node.indexOf(
                                                    '0x'
                                                ) === 0
                                            )
                                        ) {

                                            placeId =
                                                node;
                                        }

                                        return;
                                    }

                                    if (
                                        Array.isArray(
                                            node
                                        )
                                    ) {

                                        for (
                                            var i = 0;
                                            i < node.length;
                                            i++
                                        ) {

                                            findId(
                                                node[i]
                                            );

                                            if (
                                                placeId
                                            ) {
                                                return;
                                            }
                                        }
                                    }
                                }

                                findId(
                                    envelope
                                );

                                places.push({

                                    name:
                                        name,

                                    address:
                                        address,

                                    lat:
                                        lat,

                                    lng:
                                        lng,

                                    placeId:
                                        placeId

                                });

                            } catch(e) {}
                        }

                        // =================================================
                        // 10. RICERCA NEL JSON
                        // =================================================

                        function walk(
                            node
                        ) {

                            if (
                                !node
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
                        // 11. RIMOZIONE DUPLICATI
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
                        // 12. RISULTATO
                        // =================================================

                        TravelPins.log(
                            'PLACE TROVATI: ' +
                            places.length
                        );

                        if (
                            places.length === 0
                        ) {

                            TravelPins.log(
                                'NESSUN PLACE TROVATO CON IL PARSER PRINCIPALE.'
                            );

                            // --------------------------------------------
                            // DIAGNOSTICA STRUTTURA
                            // --------------------------------------------

                            if (
                                Array.isArray(data)
                            ) {

                                TravelPins.log(
                                    'TOP LEVEL ARRAY LENGTH: ' +
                                    data.length
                                );

                                for (
                                    var z = 0;
                                    z < Math.min(
                                        data.length,
                                        15
                                    );
                                    z++
                                ) {

                                    var item =
                                        data[z];

                                    var preview =
                                        '';

                                    try {

                                        preview =
                                            JSON.stringify(
                                                item
                                            )
                                            .substring(
                                                0,
                                                1000
                                            );

                                    } catch(e) {}

                                    TravelPins.log(
                                        'TOP[' +
                                        z +
                                        ']: ' +
                                        preview
                                    );
                                }
                            }

                            return;
                        }

                        // =================================================
                        // 13. GENERA OUTPUT
                        // =================================================

                        var output =
                            '';

                        output +=
                            'TITLE: Google Maps List\\n\\n';

                        output +=
                            'PLACES (' +
                            places.length +
                            ')\\n';

                        output +=
                            '==============================\\n';

                        for (
                            var j = 0;
                            j < places.length;
                            j++
                        ) {

                            var place =
                                places[j];

                            output +=
                                '\\n' +
                                (j + 1) +
                                '. ' +
                                place.name +
                                '\\n';

                            if (
                                place.address
                            ) {

                                output +=
                                    '   ' +
                                    place.address +
                                    '\\n';
                            }

                            output +=
                                '   COORD: ' +
                                place.lat +
                                ', ' +
                                place.lng +
                                '\\n';

                            if (
                                place.placeId
                            ) {

                                output +=
                                    '   PLACE ID: ' +
                                    place.placeId +
                                    '\\n';
                            }

                            output +=
                                '   MAPS: ' +
                                'https://www.google.com/maps/search/?api=1&query=' +
                                encodeURIComponent(
                                    place.lat +
                                    ',' +
                                    place.lng
                                ) +
                                '\\n';

                            output +=
                                '------------------------------\\n';
                        }

                        // =================================================
                        // 14. INVIO RISULTATO
                        // =================================================

                        TravelPins.log(
                            '===== RISULTATO LISTA =====\\n' +
                            output.substring(
                                0,
                                14000
                            )
                        );

                        // Salviamo anche il risultato
                        // globale per eventuali funzioni future.

                        window.__travelpins_places =
                            places;

                    } catch(e) {

                        TravelPins.log(
                            'ERRORE GENERALE GETLIST: ' +
                            e.message
                        );
                    }

                })();

            """.trimIndent()

            webView.evaluateJavascript(
                javascript
            ) { result ->

                addLog(
                    """
                    ==============================
                    CALLBACK GETLIST

                    $result

                    ==============================
                    """.trimIndent()
                )

                runOnUiThread {

                    scanButton.isEnabled =
                        true
                }
            }

        }, 1000)
    }

    // ============================================================
    // CONSENSO GOOGLE
    // ============================================================

    private fun acceptGoogleConsent() {

        addLog(
            """
            ==============================
            AVVIO ACCETTAZIONE GOOGLE

            ==============================
            """.trimIndent()
        )

        val javascript = """

            (function() {

                try {

                    var result = [];

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
                            text === 'accetta tutto' ||
                            text === 'accetta' ||
                            text === 'accept all' ||
                            text === 'accept'
                        ) {

                            result.push(
                                'BUTTON_FOUND: ' +
                                text
                            );

                            try {

                                e.click();

                                result.push(
                                    'CLICK_OK'
                                );

                            } catch(err) {

                                result.push(
                                    'CLICK_ERROR: ' +
                                    err.message
                                );
                            }

                            break;
                        }
                    }

                    return result.join('|');

                } catch(e) {

                    return 'ERROR: ' +
                           e.message;
                }

            })();

        """.trimIndent()

        webView.evaluateJavascript(
            javascript
        ) { result ->

            addLog(
                """
                [CONSENSO RISULTATO]

                $result

                ==============================
                """.trimIndent()
            )
        }
    }

    // ============================================================
    // NETWORK HOOK
    // ============================================================

    private fun injectNetworkHook() {

        val javascript = """

            (function() {

                if (
                    window.__travelpins_hooked
                ) {

                    return 'ALREADY_INSTALLED';
                }

                window.__travelpins_hooked =
                    true;

                var originalFetch =
                    window.fetch;

                window.fetch =
                    async function() {

                        var input =
                            arguments[0];

                        var options =
                            arguments[1] || {};

                        var url =
                            typeof input === 'string'
                            ? input
                            : input.url;

                        var method =
                            options.method ||
                            (
                                typeof input !== 'string'
                                ? input.method
                                : 'GET'
                            ) ||
                            'GET';

                        var body =
                            options.body || '';

                        try {

                            TravelPins.network(
                                'FETCH_REQUEST',
                                method,
                                url,
                                body
                            );

                        } catch(e) {}

                        return originalFetch.apply(
                            this,
                            arguments
                        );
                    };

                var originalOpen =
                    XMLHttpRequest.prototype.open;

                var originalSend =
                    XMLHttpRequest.prototype.send;

                XMLHttpRequest.prototype.open =
                    function(
                        method,
                        url
                    ) {

                        this.__tp_method =
                            method;

                        this.__tp_url =
                            url;

                        return originalOpen.apply(
                            this,
                            arguments
                        );
                    };

                XMLHttpRequest.prototype.send =
                    function(body) {

                        var xhr =
                            this;

                        try {

                            TravelPins.network(
                                'XHR_REQUEST',
                                xhr.__tp_method ||
                                'GET',
                                xhr.__tp_url ||
                                '',
                                body || ''
                            );

                        } catch(e) {}

                        return originalSend.apply(
                            this,
                            arguments
                        );
                    };

                try {

                    TravelPins.log(
                        'NETWORK HOOK INSTALLATO'
                    );

                } catch(e) {}

                return 'HOOK_INSTALLED';

            })();

        """.trimIndent()

        webView.evaluateJavascript(
            javascript
        ) { result ->

            addLog(
                "[JAVASCRIPT HOOK] $result"
            )
        }
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

        val interesting =
            lower.contains("userlists") ||
            lower.contains("listview") ||
            lower.contains("entitylist") ||
            lower.contains("batchexecute") ||
            lower.contains("rpc")

        if (!interesting) {
            return
        }

        addLog(
            """
            [GOOGLE REQUEST]

            ${request.method}
            $url

            MAIN FRAME:
            ${request.isForMainFrame}

            """.trimIndent()
        )
    }

    // ============================================================
    // NAVIGATION
    // ============================================================

    private fun inspectNavigation(
        url: String
    ) {

        addLog(
            """
            [NAVIGAZIONE]

            $url

            """.trimIndent()
        )
    }

    // ============================================================
    // GOOGLE INTENT
    // ============================================================

    private fun handleGoogleIntent(
        intentUrl: String
    ) {

        addLog(
            """
            ==============================
            GOOGLE INTENT INTERCETTATO

            CERCO FALLBACK WEB...

            ==============================
            """.trimIndent()
        )

        try {

            val uri =
                Uri.parse(intentUrl)

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
                    Uri.decode(
                        fallbackText
                    )

                webView.loadUrl(
                    fallbackText
                )

                return
            }

            addLog(
                "FALLBACK URL NON TROVATO"
            )

        } catch(e: Exception) {

            addLog(
                """
                ERRORE PARSING INTENT:

                ${e.message}
                """.trimIndent()
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

        if (
            sharedText.isNullOrBlank()
        ) {

            addLog(
                "Nessun testo ricevuto."
            )

            return
        }

        val match =
            Regex(
                """https?://\S+"""
            ).find(
                sharedText
            )

        if (match == null) {

            addLog(
                "Nessun URL trovato."
            )

            return
        }

        val url =
            match.value

        addLog(
            """
            ==============================
            LINK RICEVUTO

            $url

            ==============================
            AVVIO GOOGLE MAPS WEB...
            """.trimIndent()
        )

        webView.loadUrl(url)
    }

    // ============================================================
    // LOG
    // ============================================================

    private fun addLog(
        text: String
    ) {

        log.add(
            text.take(15000)
        )

        while (
            log.size > 100
        ) {

            log.poll()
        }

        updateScreen()
    }

    private fun updateScreen() {

        runOnUiThread {

            output.text =
                "TRAVELPINS NETWORK MONITOR\n\n" +
                log.joinToString(
                    separator = "\n"
                )
        }
    }

    // ============================================================
    // NEW INTENT
    // ============================================================

    override fun onNewIntent(
        intent: Intent
    ) {

        super.onNewIntent(intent)

        setIntent(intent)

        handleIntent(intent)
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

        handler.removeCallbacksAndMessages(null)

        webView.stopLoading()

        webView.removeJavascriptInterface(
            "TravelPins"
        )

        webView.destroy()

        super.onDestroy()
    }
}
