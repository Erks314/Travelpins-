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
                currentUrl.contains("rpc", true)

            if (!interesting) {
                return
            }

            val limitedData =
                (data ?: "").take(3000)

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

        // ========================================================
        // COPIA
        // ========================================================

        val copyButton =
            Button(this).apply {

                text =
                    "COPIA TUTTO"

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

        // ========================================================
        // PULISCI
        // ========================================================

        val clearButton =
            Button(this).apply {

                text =
                    "PULISCI"

                setOnClickListener {

                    log.clear()

                    output.text =
                        """
                        TRAVELPINS NETWORK MONITOR

                        Monitor pulito.
                        """.trimIndent()
                }
            }

        // ========================================================
        // CONSENSO GOOGLE
        // ========================================================

        consentButton =
            Button(this).apply {

                text =
                    "ACCETTA GOOGLE"

                visibility =
                    Button.GONE

                setOnClickListener {

                    acceptGoogleConsent()
                }
            }

        // ========================================================
        // SCANSIONA
        // ========================================================

        scanButton =
            Button(this).apply {

                text =
                    "SCANSIONA"

                visibility =
                    Button.GONE

                isEnabled =
                    false

                setOnClickListener {

                    scanGoogleList()
                }
            }

        // ========================================================
        // TOOLBAR
        // ========================================================

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

        // ========================================================
        // OUTPUT
        // ========================================================

        output =
            TextView(this).apply {

                textSize =
                    13f

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

                // =================================================
                // NAVIGAZIONE
                // =================================================

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

                // =================================================
                // REQUEST
                // =================================================

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {

                    inspectRequest(request)

                    return null
                }

                // =================================================
                // PAGINA CARICATA
                // =================================================

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

                    // =================================================
                    // CONSENSO
                    // =================================================

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

                    // =================================================
                    // LISTA GOOGLE
                    // =================================================

                    if (
                        url.contains(
                            "/local/userlists/list/",
                            true
                        )
                    ) {

                        addLog(
                            """
                            ==============================
                            LISTA GOOGLE MAPS RILEVATA

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

                // =================================================
                // RENDERER
                // =================================================

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
    // SCANSIONE
    // ============================================================

    private fun scanGoogleList() {

        if (!scanButton.isEnabled) {
            return
        }

        scanButton.isEnabled =
            false

        addLog(
            """
            ==============================
            SCANSIONE DIAGNOSTICA AVVIATA

            Analizzo la struttura reale
            della pagina Google Maps.

            ==============================
            """.trimIndent()
        )

        handler.postDelayed({

            inspectGoogleListPage()

        }, 2500)
    }

    // ============================================================
    // ANALISI PROFONDA DELLA PAGINA
    // ============================================================

    private fun inspectGoogleListPage() {

        val javascript = """

            (function() {

                try {

                    var result = [];

                    // ==================================================
                    // INFORMAZIONI BASE
                    // ==================================================

                    result.push(
                        '===== INFO PAGINA ====='
                    );

                    result.push(
                        'URL: ' +
                        window.location.href
                    );

                    result.push(
                        'TITLE: ' +
                        (document.title || '')
                    );

                    result.push(
                        'READY: ' +
                        document.readyState
                    );

                    // ==================================================
                    // BODY TEXT
                    // ==================================================

                    result.push(
                        ''
                    );

                    result.push(
                        '===== TESTO VISIBILE ====='
                    );

                    var bodyText =
                        document.body
                            ? document.body.innerText
                            : '';

                    bodyText =
                        bodyText.trim();

                    if (
                        bodyText.length > 12000
                    ) {

                        bodyText =
                            bodyText.substring(
                                0,
                                12000
                            );
                    }

                    result.push(
                        bodyText ||
                        '[NESSUN TESTO VISIBILE]'
                    );

                    // ==================================================
                    // ELEMENTI CLICCABILI
                    // ==================================================

                    result.push(
                        ''
                    );

                    result.push(
                        '===== ELEMENTI CLICCABILI ====='
                    );

                    var clickable =
                        document.querySelectorAll(
                            'a, button, [role="button"], [role="link"]'
                        );

                    var clickableCount = 0;

                    for (
                        var i = 0;
                        i < clickable.length;
                        i++
                    ) {

                        if (
                            clickableCount >= 200
                        ) {
                            break;
                        }

                        var el =
                            clickable[i];

                        var txt =
                            (
                                el.innerText ||
                                el.textContent ||
                                ''
                            )
                            .trim()
                            .replace(
                                /\\s+/g,
                                ' '
                            );

                        var aria =
                            el.getAttribute(
                                'aria-label'
                            ) || '';

                        var href =
                            el.getAttribute(
                                'href'
                            ) || '';

                        if (
                            txt ||
                            aria ||
                            href
                        ) {

                            result.push(
                                'ELEMENT ' +
                                clickableCount +
                                ': ' +
                                'TEXT=[' +
                                txt.substring(
                                    0,
                                    200
                                ) +
                                '] ' +
                                'ARIA=[' +
                                aria.substring(
                                    0,
                                    200
                                ) +
                                '] ' +
                                'HREF=[' +
                                href.substring(
                                    0,
                                    500
                                ) +
                                ']'
                            );

                            clickableCount++;
                        }
                    }

                    // ==================================================
                    // LINK MAPS
                    // ==================================================

                    result.push(
                        ''
                    );

                    result.push(
                        '===== LINK GOOGLE MAPS ====='
                    );

                    var mapLinks =
                        document.querySelectorAll(
                            'a[href*="google.com/maps"], a[href*="/maps/"]'
                        );

                    result.push(
                        'TROVATI: ' +
                        mapLinks.length
                    );

                    for (
                        var j = 0;
                        j < mapLinks.length &&
                        j < 100;
                        j++
                    ) {

                        var link =
                            mapLinks[j];

                        result.push(
                            'MAP_LINK ' +
                            j +
                            ': ' +
                            (
                                link.innerText ||
                                ''
                            )
                            .trim()
                            .replace(
                                /\\s+/g,
                                ' '
                            ) +
                            ' -> ' +
                            (
                                link.href ||
                                ''
                            )
                        );
                    }

                    // ==================================================
                    // ELEMENTI CON ARIA
                    // ==================================================

                    result.push(
                        ''
                    );

                    result.push(
                        '===== ELEMENTI ARIA ====='
                    );

                    var ariaElements =
                        document.querySelectorAll(
                            '[aria-label]'
                        );

                    var ariaCount = 0;

                    for (
                        var k = 0;
                        k < ariaElements.length;
                        k++
                    ) {

                        if (
                            ariaCount >= 150
                        ) {
                            break;
                        }

                        var ariaElement =
                            ariaElements[k];

                        var label =
                            ariaElement.getAttribute(
                                'aria-label'
                            ) || '';

                        if (
                            label.trim()
                        ) {

                            result.push(
                                'ARIA ' +
                                ariaCount +
                                ': ' +
                                label.substring(
                                    0,
                                    300
                                )
                            );

                            ariaCount++;
                        }
                    }

                    // ==================================================
                    // CLASSI IMPORTANTI
                    // ==================================================

                    result.push(
                        ''
                    );

                    result.push(
                        '===== ELEMENTI DATA ====='
                    );

                    var dataElements =
                        document.querySelectorAll(
                            '[data-place-id], [data-result-index], [data-cid], [data-item-id]'
                        );

                    result.push(
                        'DATA ELEMENTS: ' +
                        dataElements.length
                    );

                    for (
                        var d = 0;
                        d < dataElements.length &&
                        d < 100;
                        d++
                    ) {

                        var de =
                            dataElements[d];

                        result.push(
                            'DATA ' +
                            d +
                            ': ' +
                            de.outerHTML.substring(
                                0,
                                1000
                            )
                        );
                    }

                    // ==================================================
                    // HTML PARZIALE
                    // ==================================================

                    result.push(
                        ''
                    );

                    result.push(
                        '===== HTML BODY PARZIALE ====='
                    );

                    var html =
                        document.body
                            ? document.body.innerHTML
                            : '';

                    html =
                        html.substring(
                            0,
                            15000
                        );

                    result.push(
                        html
                    );

                    return result.join(
                        '\\n'
                    );

                } catch(e) {

                    return (
                        '===== ERRORE =====\\n' +
                        e.name +
                        ': ' +
                        e.message
                    );
                }

            })();

        """.trimIndent()

        webView.evaluateJavascript(
            javascript
        ) { result ->

            val decoded =
                decodeJavascriptResult(result)

            val limited =
                decoded.take(30000)

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
    }

    // ============================================================
    // DECODIFICA JAVASCRIPT
    // ============================================================

    private fun decodeJavascriptResult(
        value: String?
    ): String {

        if (value.isNullOrBlank()) {
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

            Google voleva aprire:
            APP GOOGLE MAPS

            ==============================
            CERCO FALLBACK WEB...

            """.trimIndent()
        )

        try {

            val uri =
                Uri.parse(intentUrl)

            val fallback =
                uri.getQueryParameter(
                    "S.browser_fallback_url"
                )

            if (!fallback.isNullOrBlank()) {

                addLog(
                    """
                    [FALLBACK URL TROVATO]

                    Carico versione web...

                    ==============================
                    """.trimIndent()
                )

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
                    Uri.decode(
                        fallbackText
                    )

                addLog(
                    """
                    [FALLBACK MANUALE]

                    Carico versione web...

                    ==============================
                    """.trimIndent()
                )

                webView.loadUrl(fallbackText)

                return
            }

            addLog(
                """
                ❌ FALLBACK URL NON TROVATO

                ==============================
                """.trimIndent()
            )

        } catch (e: Exception) {

            addLog(
                """
                ❌ ERRORE PARSING INTENT

                ${e.message}

                ==============================
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
                                'BUTTON_FOUND:' +
                                text
                            );

                            try {

                                e.click();

                                result.push(
                                    'CLICK_OK'
                                );

                            } catch(err) {

                                result.push(
                                    'CLICK_ERROR:' +
                                    err.message
                                );
                            }

                            break;
                        }
                    }

                    return result.join('|');

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

                // ==================================================
                // FETCH
                // ==================================================

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

                        try {

                            var response =
                                await originalFetch.apply(
                                    this,
                                    arguments
                                );

                            return response;

                        } catch(error) {

                            throw error;
                        }
                    };

                // ==================================================
                // XHR
                // ==================================================

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

            METHOD:
            ${request.method}

            URL:
            $url

            MAIN FRAME:
            ${request.isForMainFrame}

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

        if (sharedText.isNullOrBlank()) {

            addLog(
                "Nessun testo ricevuto."
            )

            return
        }

        val match =
            Regex(
                """https?://\S+"""
            ).find(sharedText)

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
            text.take(5000)
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
