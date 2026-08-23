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

            if (message.isNullOrBlank()) return

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
                currentUrl.contains("entitylist", true) ||
                currentUrl.contains("getlist", true) ||
                currentUrl.contains("userlists", true) ||
                currentUrl.contains("listview", true) ||
                currentUrl.contains("batchexecute", true)

            if (!interesting) return

            val limitedData =
                (data ?: "").take(4000)

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

                    // ------------------------------------------------
                    // CONSENSO
                    // ------------------------------------------------

                    if (
                        url.contains(
                            "consent.google.com",
                            true
                        )
                    ) {

                        consentButton.visibility =
                            Button.VISIBLE

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
                    // LISTA GOOGLE MAPS
                    //
                    // IMPORTANTE:
                    // ora riconosciamo anche:
                    //
                    // /maps/@/data=...
                    //
                    // che è esattamente il formato del tuo link.
                    // ------------------------------------------------

                    if (
                        isGoogleListUrl(url)
                    ) {

                        scanButton.visibility =
                            Button.VISIBLE

                        scanButton.isEnabled =
                            true

                        addLog(
                            """
                            ==============================
                            LISTA GOOGLE MAPS RILEVATA

                            SCANSIONE DISPONIBILE.

                            ==============================
                            """.trimIndent()
                        )
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

        return (
            url.contains(
                "/local/userlists/list/",
                true
            )
            ||
            url.contains(
                "/maps/@/data=",
                true
            )
            ||
            url.contains(
                "11m2!2s",
                true
            )
        )
    }

    // ============================================================
    // SCANSIONE
    // ============================================================

    private fun scanGoogleList() {

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

        extractListIdFromCurrentUrl()
    }

    // ============================================================
    // ESTRAZIONE LIST ID
    // ============================================================

    private fun extractListIdFromCurrentUrl() {

        val javascript = """

            (function() {

                try {

                    var url =
                        window.location.href;

                    TravelPins.log(
                        'URL ANALIZZATO: ' + url
                    );

                    // ------------------------------------------------
                    // FORMATO:
                    //
                    // !11m2!2sLIST_ID!3e3
                    // ------------------------------------------------

                    var marker =
                        '!11m2!2s';

                    var index =
                        url.indexOf(marker);

                    if (index >= 0) {

                        var start =
                            index + marker.length;

                        var rest =
                            url.substring(start);

                        var end =
                            rest.indexOf('!');

                        if (end >= 0) {
                            rest =
                                rest.substring(0, end);
                        }

                        if (rest.length > 0) {

                            TravelPins.log(
                                'LIST ID TROVATO: ' + rest
                            );

                            window.__travelpins_list_id =
                                rest;

                            return rest;
                        }
                    }

                    // ------------------------------------------------
                    // Fallback:
                    // local/userlists/list/ID
                    // ------------------------------------------------

                    var marker2 =
                        '/local/userlists/list/';

                    var index2 =
                        url.indexOf(marker2);

                    if (index2 >= 0) {

                        var rest2 =
                            url.substring(
                                index2 + marker2.length
                            );

                        var end2 =
                            rest2.indexOf(
                                '?'
                            );

                        if (end2 >= 0) {

                            rest2 =
                                rest2.substring(
                                    0,
                                    end2
                                );
                        }

                        if (rest2.length > 0) {

                            TravelPins.log(
                                'LIST ID FALLBACK: ' +
                                rest2
                            );

                            window.__travelpins_list_id =
                                rest2;

                            return rest2;
                        }
                    }

                    return '';

                } catch(e) {

                    TravelPins.log(
                        'ERRORE LIST ID: ' +
                        e.message
                    );

                    return '';
                }

            })();

        """.trimIndent()

        webView.evaluateJavascript(
            javascript
        ) { result ->

            val listId =
                decodeJavascriptResult(result)

            if (
                listId.isBlank()
            ) {

                addLog(
                    """
                    ❌ LIST ID NON TROVATO

                    URL:
                    ${webView.url}

                    ==============================
                    """.trimIndent()
                )

                scanButton.isEnabled = true

                return@evaluateJavascript
            }

            addLog(
                """
                ==============================
                LIST ID

                $listId

                ==============================
                CHIAMATA GOOGLE
                entitylist/getlist

                ==============================
                """.trimIndent()
            )

            fetchGoogleEntityList(listId)
        }
    }

    // ============================================================
    // GETLIST
    // ============================================================

    private fun fetchGoogleEntityList(
        listId: String
    ) {

        val safeListId =
            listId
                .replace("\\", "\\\\")
                .replace("'", "\\'")

        val javascript = """

            (async function() {

                try {

                    var listId =
                        '$safeListId';

                    // =================================================
                    // URL GETLIST
                    //
                    // Struttura confermata da implementazioni
                    // funzionanti di Google Maps list export.
                    // =================================================

                    var pb =
                        '!1m4' +
                        '!1s' + listId +
                        '!2e1' +
                        '!3m1!1e1' +
                        '!2e2' +
                        '!3e3' +
                        '!4i500' +
                        '!8i3' +
                        '!16b1';

                    var url =
                        '/maps/preview/entitylist/getlist' +
                        '?authuser=0' +
                        '&hl=it' +
                        '&gl=it' +
                        '&pb=' +
                        encodeURIComponent(pb);

                    TravelPins.log(
                        'GETLIST URL: ' + url
                    );

                    var response =
                        await fetch(
                            url,
                            {
                                method: 'GET',
                                credentials: 'include',
                                headers: {
                                    'Accept':
                                        '*/*'
                                }
                            }
                        );

                    var text =
                        await response.text();

                    TravelPins.log(
                        'GETLIST HTTP: ' +
                        response.status
                    );

                    TravelPins.log(
                        'GETLIST LENGTH: ' +
                        text.length
                    );

                    if (
                        !response.ok
                    ) {

                        return 'HTTP_ERROR:' +
                               response.status;
                    }

                    // -------------------------------------------------
                    // Non inviamo tutta la risposta attraverso
                    // JavascriptInterface.
                    //
                    // Salviamo temporaneamente il dato.
                    // -------------------------------------------------

                    window.__travelpins_getlist =
                        text;

                    return 'GETLIST_OK';

                } catch(e) {

                    return 'GETLIST_ERROR:' +
                           e.message;
                }

            })();

        """.trimIndent()

        webView.evaluateJavascript(
            javascript
        ) { result ->

            val status =
                decodeJavascriptResult(result)

            addLog(
                """
                [GETLIST]

                $status

                ==============================
                """.trimIndent()
            )

            if (
                status == "GETLIST_OK"
            ) {

                parseGetListResult()

            } else {

                addLog(
                    """
                    ❌ GETLIST FALLITA

                    $status

                    ==============================
                    """.trimIndent()
                )

                scanButton.isEnabled = true
            }
        }
    }

    // ============================================================
    // LETTURA RISPOSTA GETLIST
    // ============================================================

    private fun parseGetListResult() {

        val javascript = """

            (function() {

                try {

                    var raw =
                        window.__travelpins_getlist;

                    if (!raw) {

                        return 'NO_RESPONSE';
                    }

                    // =================================================
                    // Rimuoviamo protezione XSSI
                    //
                    // )]}'
                    // =================================================

                    if (
                        raw.indexOf(")]}'") === 0
                    ) {

                        raw =
                            raw.substring(4);
                    }

                    raw =
                        raw.trim();

                    var data =
                        JSON.parse(raw);

                    TravelPins.log(
                        'JSON PARSATO'
                    );

                    // =================================================
                    // Cerchiamo ricorsivamente gli elementi della lista
                    //
                    // Le implementazioni pubbliche confermano che
                    // il payload contiene una struttura annidata
                    // con nome, coordinate e indirizzo.
                    // =================================================

                    var results = [];

                    function walk(
                        value,
                        depth
                    ) {

                        if (
                            depth > 20
                        ) {
                            return;
                        }

                        if (
                            !Array.isArray(value)
                        ) {
                            return;
                        }

                        // ---------------------------------------------
                        // Pattern tipico di un elemento:
                        //
                        // [
                        //   null,
                        //   [...coordinate...],
                        //   "NOME",
                        //   ...
                        // ]
                        // ---------------------------------------------

                        if (
                            value.length >= 3 &&
                            typeof value[2] === 'string' &&
                            value[2].trim().length > 0
                        ) {

                            var name =
                                value[2].trim();

                            var lat = '';
                            var lng = '';
                            var address = '';

                            // Cerca coordinate numeriche
                            // nella struttura dell'elemento.

                            function findNumbers(
                                v,
                                d
                            ) {

                                if (
                                    d > 8
                                ) {
                                    return;
                                }

                                if (
                                    Array.isArray(v)
                                ) {

                                    for (
                                        var k = 0;
                                        k < v.length;
                                        k++
                                    ) {

                                        var item =
                                            v[k];

                                        if (
                                            typeof item === 'number'
                                        ) {

                                            if (
                                                lat === '' &&
                                                item >= -90 &&
                                                item <= 90
                                            ) {

                                                lat =
                                                    String(item);

                                            } else if (
                                                lng === '' &&
                                                item >= -180 &&
                                                item <= 180
                                            ) {

                                                lng =
                                                    String(item);
                                            }
                                        }

                                        if (
                                            typeof item === 'string' &&
                                            item.length > 8 &&
                                            address === ''
                                        ) {

                                            if (
                                                item.indexOf(' ') >= 0
                                            ) {

                                                address =
                                                    item;
                                            }
                                        }

                                        if (
                                            Array.isArray(item)
                                        ) {

                                            findNumbers(
                                                item,
                                                d + 1
                                            );
                                        }
                                    }
                                }
                            }

                            findNumbers(
                                value,
                                0
                            );

                            // Evita falsi positivi evidenti.
                            if (
                                name.length < 300
                            ) {

                                results.push({
                                    name: name,
                                    address: address,
                                    lat: lat,
                                    lng: lng
                                });
                            }
                        }

                        for (
                            var i = 0;
                            i < value.length;
                            i++
                        ) {

                            if (
                                Array.isArray(
                                    value[i]
                                )
                            ) {

                                walk(
                                    value[i],
                                    depth + 1
                                );
                            }
                        }
                    }

                    walk(data, 0);

                    // =================================================
                    // Rimuove duplicati per nome + coordinate
                    // =================================================

                    var unique = [];
                    var seen = {};

                    for (
                        var i = 0;
                        i < results.length;
                        i++
                    ) {

                        var r =
                            results[i];

                        var key =
                            r.name +
                            '|' +
                            r.lat +
                            '|' +
                            r.lng;

                        if (
                            !seen[key]
                        ) {

                            seen[key] = true;

                            unique.push(r);
                        }
                    }

                    TravelPins.log(
                        'ELEMENTI TROVATI: ' +
                        unique.length
                    );

                    window.__travelpins_results =
                        JSON.stringify(unique);

                    return 'RESULTS:' +
                           unique.length;

                } catch(e) {

                    return 'PARSE_ERROR:' +
                           e.message;
                }

            })();

        """.trimIndent()

        webView.evaluateJavascript(
            javascript
        ) { result ->

            val status =
                decodeJavascriptResult(result)

            addLog(
                """
                ==============================
                PARSER GETLIST

                $status

                ==============================
                """.trimIndent()
            )

            readResultsFromWebView()
        }
    }

    // ============================================================
    // RISULTATI
    // ============================================================

    private fun readResultsFromWebView() {

        val javascript = """

            (function() {

                return (
                    window.__travelpins_results ||
                    '[]'
                );

            })();

        """.trimIndent()

        webView.evaluateJavascript(
            javascript
        ) { result ->

            val decoded =
                decodeJavascriptResult(result)

            if (
                decoded.isBlank() ||
                decoded == "[]"
            ) {

                addLog(
                    """
                    ❌ NESSUN LUOGO ESTRATTO

                    La risposta getlist è arrivata,
                    ma il parser non ha riconosciuto
                    gli elementi.

                    ==============================
                    """.trimIndent()
                )

                // In questo caso stampiamo anche
                // la risposta grezza iniziale per
                // permetterci di adattare il parser
                // al formato preciso della tua lista.

                readRawResponseForDiagnostics()

                scanButton.isEnabled = true

                return@evaluateJavascript
            }

            try {

                val json =
                    org.json.JSONArray(
                        decoded
                    )

                val sb =
                    StringBuilder()

                sb.append(
                    "\n==============================\n"
                )

                sb.append(
                    "RISULTATO FINALE\n"
                )

                sb.append(
                    "LUOGHI TROVATI: "
                )

                sb.append(
                    json.length()
                )

                sb.append(
                    "\n==============================\n\n"
                )

                for (
                    i in 0 until json.length()
                ) {

                    val item =
                        json.getJSONObject(i)

                    sb.append(
                        "${i + 1}. "
                    )

                    sb.append(
                        item.optString(
                            "name"
                        )
                    )

                    sb.append("\n")

                    val address =
                        item.optString(
                            "address"
                        )

                    if (
                        address.isNotBlank()
                    ) {

                        sb.append(
                            "   $address\n"
                        )
                    }

                    val lat =
                        item.optString(
                            "lat"
                        )

                    val lng =
                        item.optString(
                            "lng"
                        )

                    if (
                        lat.isNotBlank() &&
                        lng.isNotBlank()
                    ) {

                        sb.append(
                            "   $lat, $lng\n"
                        )
                    }

                    sb.append("\n")
                }

                addLog(
                    sb.toString()
                )

            } catch (
                e: Exception
            ) {

                addLog(
                    """
                    ❌ ERRORE RISULTATI

                    ${e.message}

                    ==============================
                    """.trimIndent()
                )
            }

            scanButton.isEnabled = true
        }
    }

    // ============================================================
    // RISPOSTA GREZZA DIAGNOSTICA
    // ============================================================

    private fun readRawResponseForDiagnostics() {

        val javascript = """

            (function() {

                var raw =
                    window.__travelpins_getlist ||
                    '';

                return raw.substring(
                    0,
                    12000
                );

            })();

        """.trimIndent()

        webView.evaluateJavascript(
            javascript
        ) { result ->

            val raw =
                decodeJavascriptResult(result)

            addLog(
                """
                ==============================
                GETLIST RAW RESPONSE
                PRIMI 12000 CARATTERI

                $raw

                ==============================
                """.trimIndent()
            )
        }
    }

    // ============================================================
    // DECODIFICA JS
    // ============================================================

    private fun decodeJavascriptResult(
        value: String?
    ): String {

        if (
            value.isNullOrBlank()
        ) {
            return ""
        }

        var result = value

        if (
            result.startsWith("\"") &&
            result.endsWith("\"")
        ) {

            result =
                result.substring(
                    1,
                    result.length - 1
                )
        }

        result =
            result.replace(
                "\\n",
                "\n"
            )

        result =
            result.replace(
                "\\r",
                "\r"
            )

        result =
            result.replace(
                "\\t",
                "\t"
            )

        result =
            result.replace(
                "\\\"",
                "\""
            )

        result =
            result.replace(
                "\\/",
                "/"
            )

        result =
            result.replace(
                "\\\\",
                "\\"
            )

        return result
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

                webView.loadUrl(fallback)

                return
            }

            val marker =
                "S.browser_fallback_url="

            val index =
                intentUrl.indexOf(marker)

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

                webView.loadUrl(
                    fallbackText
                )
            }

        } catch (
            e: Exception
        ) {

            addLog(
                "ERRORE INTENT: ${e.message}"
            )
        }
    }

    // ============================================================
    // CONSENSO
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
                            text === 'accetta tutto' ||
                            text === 'accetta' ||
                            text === 'accept all' ||
                            text === 'accept'
                        ) {

                            e.click();

                            return 'CLICK_OK: ' +
                                   text;
                        }
                    }

                    return 'BUTTON_NOT_FOUND';

                } catch(e) {

                    return 'ERROR:' +
                           e.message;
                }

            })();

        """.trimIndent()

        webView.evaluateJavascript(
            javascript
        ) { result ->

            addLog(
                """
                ==============================
                CONSENSO

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

                window.__travelpins_hooked = true;

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

                        return await originalFetch.apply(
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

                        try {

                            TravelPins.network(
                                'XHR_REQUEST',
                                this.__tp_method ||
                                'GET',
                                this.__tp_url ||
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

        if (
            !lower.contains("entitylist") &&
            !lower.contains("getlist") &&
            !lower.contains("userlists")
        ) {
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
            ).find(sharedText)

        if (
            match == null
        ) {

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
            AVVIO GOOGLE MAPS...
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
