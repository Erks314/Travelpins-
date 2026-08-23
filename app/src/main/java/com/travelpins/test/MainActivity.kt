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

            addLog(
                """
                ==============================
                NETWORK $type

                METHOD:
                ${method ?: ""}

                URL:
                ${url ?: ""}

                DATA:
                ${data ?: ""}

                ==============================
                """.trimIndent()
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

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
            setPadding(12, 12, 12, 12)
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val copyButton = Button(this).apply {

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

        val clearButton = Button(this).apply {

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

        consentButton = Button(this).apply {

            text = "ACCETTA GOOGLE"
            visibility = Button.GONE

            setOnClickListener {
                acceptGoogleConsent()
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

        output = TextView(this).apply {

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

        val scroll = ScrollView(this).apply {
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

                // ------------------------------------------------
                // NAVIGAZIONE
                // ------------------------------------------------

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {

                    val url =
                        request.url.toString()

                    inspectNavigation(url)

                    if (url.startsWith("intent://")) {

                        handleGoogleIntent(url)

                        return true
                    }

                    return false
                }

                // ------------------------------------------------
                // RICHIESTE WEBVIEW
                // ------------------------------------------------

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {

                    inspectRequest(request)

                    return null
                }

                // ------------------------------------------------
                // PAGINA CARICATA
                // ------------------------------------------------

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

                    if (url.contains("consent.google.com")) {

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
                    // ------------------------------------------------

                    if (
                        url.contains(
                            "/local/userlists/list/"
                        )
                    ) {

                        inspectGoogleListPage()
                    }
                }

                // ------------------------------------------------
                // RENDERER CRASH
                // ------------------------------------------------

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
    // LETTURA LISTA GOOGLE MAPS
    // ============================================================

    private fun inspectGoogleListPage() {

        addLog(
            """
            ==============================
            LISTA GOOGLE MAPS RILEVATA

            Avvio monitoraggio contenuto...

            ==============================
            """.trimIndent()
        )

        // --------------------------------------------------------
        // Prima lettura dopo 2 secondi
        // --------------------------------------------------------

        handler.postDelayed({

            readGoogleListPage(
                "CONTROLLO 2 SECONDI"
            )

        }, 2000)

        // --------------------------------------------------------
        // Seconda lettura dopo 5 secondi
        // --------------------------------------------------------

        handler.postDelayed({

            readGoogleListPage(
                "CONTROLLO 5 SECONDI"
            )

        }, 5000)

        // --------------------------------------------------------
        // Terza lettura dopo 8 secondi
        // --------------------------------------------------------

        handler.postDelayed({

            readGoogleListPage(
                "CONTROLLO 8 SECONDI"
            )

        }, 8000)

        // --------------------------------------------------------
        // Quarta lettura dopo 12 secondi
        // --------------------------------------------------------

        handler.postDelayed({

            readGoogleListPage(
                "CONTROLLO 12 SECONDI"
            )

        }, 12000)

        // --------------------------------------------------------
        // Installiamo anche un MutationObserver.
        //
        // Google Maps costruisce la pagina dinamicamente.
        // Questo ci permette di sapere quando il contenuto cambia.
        // --------------------------------------------------------

        installListMutationObserver()
    }

    // ============================================================
    // LETTURA PAGINA
    // ============================================================

    private fun readGoogleListPage(
        label: String
    ) {

        val javascript = """

            (function() {

                try {

                    var bodyText =
                        document.body
                            ? document.body.innerText
                            : '';

                    var title =
                        document.title || '';

                    var links = [];

                    var elements =
                        document.querySelectorAll('a');

                    for (
                        var i = 0;
                        i < elements.length;
                        i++
                    ) {

                        var element =
                            elements[i];

                        var href =
                            element.href || '';

                        var text =
                            element.innerText || '';

                        text =
                            text.trim();

                        if (
                            href &&
                            (
                                href.indexOf(
                                    'google.com/maps'
                                ) >= 0
                                ||
                                href.indexOf(
                                    '/maps/'
                                ) >= 0
                            )
                        ) {

                            links.push(
                                text +
                                ' -> ' +
                                href
                            );
                        }
                    }

                    // ------------------------------------------------
                    // Cerchiamo anche elementi che sembrano
                    // contenere nomi di luoghi.
                    // ------------------------------------------------

                    var buttons = [];

                    var clickable =
                        document.querySelectorAll(
                            '[role="button"]'
                        );

                    for (
                        var j = 0;
                        j < clickable.length;
                        j++
                    ) {

                        var button =
                            clickable[j];

                        var buttonText =
                            button.innerText || '';

                        buttonText =
                            buttonText.trim();

                        if (
                            buttonText.length > 0
                        ) {

                            buttons.push(
                                buttonText
                            );
                        }
                    }

                    // ------------------------------------------------
                    // Limitiamo la quantità di testo.
                    // ------------------------------------------------

                    if (
                        bodyText.length > 40000
                    ) {

                        bodyText =
                            bodyText.substring(
                                0,
                                40000
                            );
                    }

                    if (
                        links.length > 500
                    ) {

                        links =
                            links.slice(
                                0,
                                500
                            );
                    }

                    if (
                        buttons.length > 500
                    ) {

                        buttons =
                            buttons.slice(
                                0,
                                500
                            );
                    }

                    var result =
                        'TITLE:\\n' +
                        title +
                        '\\n\\n' +

                        'BODY TEXT:\\n' +
                        bodyText +
                        '\\n\\n' +

                        'MAP LINKS:\\n' +
                        links.join('\\n') +
                        '\\n\\n' +

                        'BUTTONS / CLICKABLE:\\n' +
                        buttons.join('\\n');

                    return result;

                } catch(e) {

                    return 'ERROR:' +
                           e.message;
                }

            })();

        """.trimIndent()

        webView.evaluateJavascript(
            javascript
        ) { result ->

            val decoded =
                decodeJavascriptResult(result)

            addLog(
                """
                ==============================
                $label

                $decoded

                ==============================
                """.trimIndent()
            )
        }
    }

    // ============================================================
    // MUTATION OBSERVER
    // ============================================================

    private fun installListMutationObserver() {

        val javascript = """

            (function() {

                try {

                    if (
                        window.__travelpins_list_observer
                    ) {

                        return 'OBSERVER_ALREADY_INSTALLED';
                    }

                    window.__travelpins_list_observer =
                        true;

                    var lastText = '';

                    function checkPage() {

                        try {

                            var text =
                                document.body
                                    ? document.body.innerText
                                    : '';

                            if (
                                !text ||
                                text === lastText
                            ) {

                                return;
                            }

                            lastText = text;

                            TravelPins.log(
                                'LIST_PAGE_CHANGED: ' +
                                text.length +
                                ' caratteri'
                            );

                            // Quando Google ha finalmente
                            // inserito abbastanza contenuto,
                            // chiediamo a Kotlin di leggerlo.

                            if (
                                text.length > 100
                            ) {

                                setTimeout(
                                    function() {

                                        var links =
                                            document.querySelectorAll(
                                                'a'
                                            );

                                        var found = [];

                                        for (
                                            var i = 0;
                                            i < links.length;
                                            i++
                                        ) {

                                            var href =
                                                links[i].href ||
                                                '';

                                            var linkText =
                                                links[i].innerText ||
                                                '';

                                            linkText =
                                                linkText.trim();

                                            if (
                                                href &&
                                                linkText
                                            ) {

                                                if (
                                                    href.indexOf(
                                                        'google.com/maps'
                                                    ) >= 0
                                                    ||
                                                    href.indexOf(
                                                        '/maps/'
                                                    ) >= 0
                                                ) {

                                                    found.push(
                                                        linkText +
                                                        ' -> ' +
                                                        href
                                                    );
                                                }
                                            }
                                        }

                                        TravelPins.log(
                                            'MAP_LINKS_FOUND:\\n' +
                                            found.join(
                                                '\\n'
                                            )
                                        );

                                    },
                                    500
                                );
                            }

                        } catch(e) {

                            TravelPins.log(
                                'OBSERVER_ERROR:' +
                                e.message
                            );
                        }
                    }

                    var observer =
                        new MutationObserver(
                            function() {

                                checkPage();

                            }
                        );

                    observer.observe(
                        document.documentElement,
                        {
                            childList: true,
                            subtree: true,
                            characterData: true
                        }
                    );

                    setInterval(
                        checkPage,
                        2000
                    );

                    checkPage();

                    return 'OBSERVER_INSTALLED';

                } catch(e) {

                    return 'OBSERVER_ERROR:' +
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
                LIST MUTATION OBSERVER

                $result

                ==============================
                """.trimIndent()
            )
        }
    }

    // ============================================================
    // DECODIFICA RISULTATO JAVASCRIPT
    // ============================================================

    private fun decodeJavascriptResult(
        value: String?
    ): String {

        if (value.isNullOrBlank()) {
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

                    $fallback

                    ==============================
                    CARICO FALLBACK NELLA WEBVIEW...
                    ==============================
                    """.trimIndent()
                )

                webView.loadUrl(fallback)

                return
            }

            // ------------------------------------------------
            // FALLBACK MANUALE
            // ------------------------------------------------

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

                    $fallbackText

                    ==============================
                    CARICO FALLBACK...
                    ==============================
                    """.trimIndent()
                )

                webView.loadUrl(
                    fallbackText
                )

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

                            try {

                                var clone =
                                    response.clone();

                                var text =
                                    await clone.text();

                                TravelPins.network(
                                    'FETCH_RESPONSE',
                                    method,
                                    url,
                                    text
                                );

                            } catch(e) {

                                TravelPins.network(
                                    'FETCH_RESPONSE_ERROR',
                                    method,
                                    url,
                                    e.message
                                );
                            }

                            return response;

                        } catch(error) {

                            try {

                                TravelPins.network(
                                    'FETCH_ERROR',
                                    method,
                                    url,
                                    error.message
                                );

                            } catch(e) {}

                            throw error;
                        }
                    };

                // ==================================================
                // XMLHttpRequest
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

                        try {

                            TravelPins.network(
                                'XHR_OPEN',
                                method,
                                url,
                                ''
                            );

                        } catch(e) {}

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

                        xhr.addEventListener(
                            'load',
                            function() {

                                try {

                                    TravelPins.network(
                                        'XHR_RESPONSE',
                                        xhr.__tp_method ||
                                        'GET',
                                        xhr.__tp_url ||
                                        '',
                                        xhr.responseText ||
                                        ''
                                    );

                                } catch(e) {

                                    TravelPins.network(
                                        'XHR_RESPONSE_ERROR',
                                        xhr.__tp_method ||
                                        'GET',
                                        xhr.__tp_url ||
                                        '',
                                        e.message
                                    );
                                }
                            }
                        );

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
            lower.contains("google.com/maps") ||
            lower.contains("maps.google") ||
            lower.contains("/maps/preview") ||
            lower.contains("entitylist") ||
            lower.contains("placelist") ||
            lower.contains("place") ||
            lower.contains("saved") ||
            lower.contains("list") ||
            lower.contains("batchexecute") ||
            lower.contains("rpc") ||
            lower.contains("pb=") ||
            lower.contains("data=")

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

        log.add(text)

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
