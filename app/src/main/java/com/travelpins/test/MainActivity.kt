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
    // DESKTOP USER AGENT
    //
    // Google Maps dal 2026 non restituisce correttamente alcune
    // liste con user-agent mobile/WebView.
    //
    // GeoShare usa un desktop UA per questo caso.
    // ============================================================

    private val desktopUserAgent =
        "Mozilla/5.0 (X11; Linux x86_64) " +
        "AppleWebKit/537.36 " +
        "(KHTML, like Gecko) " +
        "Chrome/131.0.0.0 " +
        "Safari/537.36"

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
                currentUrl.contains(
                    "userlists",
                    ignoreCase = true
                ) ||
                currentUrl.contains(
                    "listview",
                    ignoreCase = true
                ) ||
                currentUrl.contains(
                    "batchexecute",
                    ignoreCase = true
                ) ||
                currentUrl.contains(
                    "rpc",
                    ignoreCase = true
                )

            if (!interesting) {
                return
            }

            val limitedData =
                (data ?: "").take(2000)

            addLog(
                """
                ==============================
                NETWORK ${type ?: ""}

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

        super.onCreate(
            savedInstanceState
        )

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

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    12,
                    12,
                    12,
                    12
                )
            }

        val toolbar =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL
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

                visibility =
                    Button.GONE

                setOnClickListener {

                    acceptGoogleConsent()
                }
            }

        scanButton =
            Button(this).apply {

                text = "SCANSIONA"

                visibility =
                    Button.GONE

                isEnabled =
                    false

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

                setTextIsSelectable(
                    true
                )

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

        webView =
            WebView(this)

        webView.settings.apply {

            javaScriptEnabled =
                true

            domStorageEnabled =
                true

            databaseEnabled =
                true

            loadsImagesAutomatically =
                true

            javaScriptCanOpenWindowsAutomatically =
                true

            setSupportMultipleWindows(
                false
            )

            // ====================================================
            // CAMBIAMENTO IMPORTANTE
            // ====================================================

            userAgentString =
                desktopUserAgent
        }

        CookieManager
            .getInstance()
            .setAcceptCookie(
                true
            )

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

                        USER AGENT:
                        DESKTOP

                        ==============================
                        """.trimIndent()
                    )

                    injectNetworkHook()

                    if (
                        url.contains(
                            "consent.google.com",
                            ignoreCase = true
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
                            CONSENSO GOOGLE RILEVATO.

                            Premi ACCETTA GOOGLE.

                            ==============================
                            """.trimIndent()
                        )

                    } else {

                        consentButton.visibility =
                            Button.GONE
                    }

                    // =================================================
                    // RICONOSCIMENTO LISTA
                    //
                    // Accettiamo sia il vecchio percorso /local/
                    // sia /maps/placelists/
                    // =================================================

                    if (
                        isGoogleListUrl(url)
                    ) {

                        addLog(
                            """
                            ==============================
                            LISTA GOOGLE MAPS RILEVATA

                            Google sta utilizzando
                            la modalità desktop.

                            Premi SCANSIONA.

                            ==============================
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

        val lower =
            url.lowercase()

        return lower.contains(
            "/local/userlists/list/"
        ) ||
        lower.contains(
            "/maps/placelists/list/"
        ) ||
        lower.contains(
            "placelists/list"
        )
    }

    // ============================================================
    // SCANSIONE
    // ============================================================

    private fun scanGoogleList() {

        if (
            !scanButton.isEnabled
        ) {
            return
        }

        scanButton.isEnabled =
            false

        addLog(
            """
            ==============================
            SCANSIONE AVVIATA

            Analizzo APP_INITIALIZATION_STATE
            di Google Maps.

            ==============================
            """.trimIndent()
        )

        inspectGoogleListPage()
    }

    // ============================================================
    // LETTURA GOOGLE MAPS
    //
    // NON cerchiamo più solamente i tag <a>.
    //
    // Google inserisce i dati della lista nello stato iniziale
    // dell'applicazione JavaScript.
    // ============================================================

    private fun inspectGoogleListPage() {

        handler.postDelayed({

            val javascript = """

                (function() {

                    try {

                        var result = [];

                        result.push(
                            'URL: ' +
                            location.href
                        );

                        result.push(
                            'TITLE: ' +
                            (document.title || '')
                        );

                        // =================================================
                        // APP_INITIALIZATION_STATE
                        // =================================================

                        if (
                            typeof APP_INITIALIZATION_STATE !==
                            'undefined'
                        ) {

                            result.push(
                                'APP_INITIALIZATION_STATE: PRESENTE'
                            );

                            try {

                                var state =
                                    JSON.stringify(
                                        APP_INITIALIZATION_STATE
                                    );

                                result.push(
                                    'STATE_LENGTH: ' +
                                    state.length
                                );

                                // Cerchiamo URL Google Maps
                                // e coordinate presenti nello stato.

                                var found =
                                    [];

                                var urlRegex =
                                    /https?:\\\\/\\\\/(?:www\\\\.)?google\\\\.com\\\\/maps[^"\\\\s\\\\]+/g;

                                var matches =
                                    state.match(
                                        urlRegex
                                    );

                                if (matches) {

                                    for (
                                        var i = 0;
                                        i < matches.length;
                                        i++
                                    ) {

                                        var u =
                                            matches[i];

                                        u =
                                            u.replace(
                                                /\\\\\\\\\\\\/g,
                                                '/'
                                            );

                                        if (
                                            found.indexOf(u)
                                            === -1
                                        ) {

                                            found.push(u);
                                        }
                                    }
                                }

                                result.push(
                                    'MAP_URLS: ' +
                                    found.length
                                );

                                for (
                                    var j = 0;
                                    j < found.length;
                                    j++
                                ) {

                                    result.push(
                                        found[j]
                                    );
                                }

                                // =================================================
                                // Coordinate
                                // =================================================

                                var coordRegex =
                                    /-?\\\\d{1,3}\\\\.\\\\d{4,}/g;

                                var coords =
                                    state.match(
                                        coordRegex
                                    ) || [];

                                var uniqueCoords =
                                    [];

                                for (
                                    var k = 0;
                                    k < coords.length;
                                    k++
                                ) {

                                    if (
                                        uniqueCoords.indexOf(
                                            coords[k]
                                        ) === -1
                                    ) {

                                        uniqueCoords.push(
                                            coords[k]
                                        );
                                    }
                                }

                                result.push(
                                    'NUMERIC_VALUES: ' +
                                    uniqueCoords.length
                                );

                            } catch(e) {

                                result.push(
                                    'STATE_ERROR: ' +
                                    e.message
                                );
                            }

                        } else {

                            result.push(
                                'APP_INITIALIZATION_STATE: ASSENTE'
                            );
                        }

                        // =================================================
                        // FALLBACK: testo pagina
                        // =================================================

                        var bodyText =
                            document.body
                                ? document.body.innerText
                                : '';

                        result.push(
                            'VISIBLE_TEXT: ' +
                            bodyText
                                .substring(
                                    0,
                                    3000
                                )
                        );

                        return result.join(
                            '\\n'
                        );

                    } catch(e) {

                        return 'SCAN_ERROR:' +
                               e.message;
                    }

                })();

            """.trimIndent()

            webView.evaluateJavascript(
                javascript
            ) { rawResult ->

                val decoded =
                    decodeJavascriptResult(
                        rawResult
                    )

                val limited =
                    decoded.take(
                        12000
                    )

                addLog(
                    """
                    ==============================
                    RISULTATO SCANSIONE

                    $limited

                    ==============================
                    """.trimIndent()
                )

                runOnUiThread {

                    scanButton.isEnabled =
                        true
                }
            }

        }, 2500)
    }

    // ============================================================
    // DECODIFICA JAVASCRIPT
    // ============================================================

    private fun decodeJavascriptResult(
        value: String?
    ): String {

        if (
            value.isNullOrBlank()
        ) {

            return ""
        }

        var result =
            value

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

            Cerco fallback web...

            ==============================
            """.trimIndent()
        )

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

            if (index >= 0) {

                var fallbackText =
                    intentUrl.substring(
                        index +
                        marker.length
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
                "❌ FALLBACK URL NON TROVATO"
            )

        } catch (
            e: Exception
        ) {

            addLog(
                """
                ❌ ERRORE PARSING INTENT

                ${e.message}
                """.trimIndent()
            )
        }
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

                            return (
                                'CLICK_OK: ' +
                                text
                            );
                        }
                    }

                    return 'PULSANTE NON TROVATO';

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
                [CONSENSO]

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
    // WEBVIEW REQUEST MONITOR
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

            """.trimIndent()
        )
    }

    // ============================================================
    // NAVIGATION MONITOR
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

        webView.loadUrl(
            url
        )
    }

    // ============================================================
    // LOG
    // ============================================================

    private fun addLog(
        text: String
    ) {

        log.add(
            text.take(4000)
        )

        while (
            log.size > 80
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
