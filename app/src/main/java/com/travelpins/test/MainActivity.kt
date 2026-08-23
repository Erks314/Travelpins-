package com.travelpins.test

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.net.Uri
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
                ==============================
                [JAVASCRIPT]

                $message

                ==============================
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

            val safeData =
                data ?: ""

            addLog(
                """
                ==============================
                NETWORK $type

                METHOD:
                ${method ?: ""}

                URL:
                ${url ?: ""}

                DATA:
                $safeData

                ==============================
                """.trimIndent()
            )
        }

        @JavascriptInterface
        fun listDetected(
            id: String?,
            url: String?
        ) {

            addLog(
                """
                =========================================
                LISTA GOOGLE MAPS DETECTATA

                ID:
                ${id ?: ""}

                URL:
                ${url ?: ""}

                =========================================
                """.trimIndent()
            )
        }
    }

    // ============================================================
    // ON CREATE
    // ============================================================

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

        scanButton =
            Button(this).apply {

                text =
                    "SCANSIONA"

                setOnClickListener {

                    inspectGoogleListPage(
                        immediate = true
                    )
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

                // =================================================
                // NAVIGAZIONE
                // =================================================

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {

                    val url =
                        request.url.toString()

                    inspectNavigation(
                        url
                    )

                    if (
                        url.startsWith(
                            "intent://"
                        )
                    ) {

                        handleGoogleIntent(
                            url
                        )

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

                    inspectRequest(
                        request
                    )

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
                        =========================================
                        PAGINA CARICATA

                        $url

                        =========================================
                        """.trimIndent()
                    )

                    injectNetworkHook()

                    if (
                        url.contains(
                            "consent.google.com"
                        )
                    ) {

                        consentButton.visibility =
                            Button.VISIBLE

                        addLog(
                            """
                            =========================================
                            CONSENSO GOOGLE RILEVATO

                            Premi:
                            ACCETTA GOOGLE

                            =========================================
                            """.trimIndent()
                        )

                    } else {

                        consentButton.visibility =
                            Button.GONE
                    }

                    if (
                        url.contains(
                            "/local/userlists/list/"
                        )
                    ) {

                        detectListUrl(
                            url
                        )

                        installListObserver()

                        inspectGoogleListPage(
                            immediate = false
                        )
                    }
                }

                // =================================================
                // RENDERER CRASH
                // =================================================

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail
                ): Boolean {

                    addLog(
                        """
                        =========================================
                        WEBVIEW RENDERER TERMINATO

                        CRASH:
                        ${detail.didCrash()}

                        =========================================
                        """.trimIndent()
                    )

                    return true
                }
            }
    }

    // ============================================================
    // DETECT LIST URL
    // ============================================================

    private fun detectListUrl(
        url: String
    ) {

        try {

            val marker =
                "/local/userlists/list/"

            val index =
                url.indexOf(
                    marker
                )

            if (index < 0) return

            var id =
                url.substring(
                    index + marker.length
                )

            id =
                id.substringBefore(
                    "?"
                )

            id =
                id.substringBefore(
                    "/"
                )

            addLog(
                """
                =========================================
                GOOGLE LIST ID

                $id

                =========================================
                """.trimIndent()
            )

            webView.evaluateJavascript(
                """
                try {
                    TravelPins.listDetected(
                        ${jsQuote(id)},
                        ${jsQuote(url)}
                    );
                } catch(e) {}
                """.trimIndent(),
                null
            )

        } catch (e: Exception) {

            addLog(
                "Errore detectListUrl: ${e.message}"
            )
        }
    }

    // ============================================================
    // OSSERVATORE DOM
    // ============================================================

    private fun installListObserver() {

        val javascript =
            """
            (function() {

                try {

                    if (
                        window.__travelpins_list_observer
                    ) {
                        return 'LIST_OBSERVER_ALREADY';
                    }

                    window.__travelpins_list_observer =
                        true;

                    var timer = null;

                    var observer =
                        new MutationObserver(
                            function() {

                                if (timer) {
                                    clearTimeout(timer);
                                }

                                timer =
                                    setTimeout(
                                        function() {

                                            try {

                                                TravelPins.log(
                                                    'DOM MODIFICATO - NUOVA SCANSIONE'
                                                );

                                            } catch(e) {}

                                            try {

                                                if (
                                                    typeof window.__travelpins_scan_list ===
                                                    'function'
                                                ) {

                                                    window.__travelpins_scan_list();

                                                }

                                            } catch(e) {}
                                        },
                                        1200
                                    );
                            }
                        );

                    observer.observe(
                        document.documentElement ||
                        document.body,
                        {
                            childList: true,
                            subtree: true,
                            characterData: true
                        }
                    );

                    window.__travelpins_list_observer =
                        observer;

                    return 'LIST_OBSERVER_INSTALLED';

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
                "[LIST OBSERVER] $result"
            )
        }
    }

    // ============================================================
    // SCANSIONE GOOGLE LIST
    // ============================================================

    private fun inspectGoogleListPage(
        immediate: Boolean
    ) {

        addLog(
            """
            =========================================
            SCANSIONE LISTA GOOGLE MAPS

            Attendo contenuto dinamico...

            =========================================
            """.trimIndent()
        )

        val delay =
            if (immediate) {
                300
            } else {
                2500
            }

        handler.postDelayed(
            {

                scanListJavascript()

            },
            delay.toLong()
        )

        // Altre scansioni perché Google costruisce
        // la pagina progressivamente.

        if (!immediate) {

            handler.postDelayed(
                {
                    scanListJavascript()
                },
                6000
            )

            handler.postDelayed(
                {
                    scanListJavascript()
                },
                10000
            )
        }
    }

    // ============================================================
    // JAVASCRIPT SCANSIONE
    // ============================================================

    private fun scanListJavascript() {

        val javascript =
            """
            (function() {

                try {

                    var result = [];

                    result.push(
                        '===== LIST SCAN ====='
                    );

                    result.push(
                        'URL=' +
                        location.href
                    );

                    result.push(
                        'TITLE=' +
                        (document.title || '')
                    );

                    // =============================================
                    // BODY TEXT
                    // =============================================

                    var bodyText =
                        document.body
                            ? document.body.innerText
                            : '';

                    bodyText =
                        bodyText.trim();

                    if (
                        bodyText.length >
                        50000
                    ) {

                        bodyText =
                            bodyText.substring(
                                0,
                                50000
                            );
                    }

                    result.push(
                        '===== BODY TEXT ====='
                    );

                    result.push(
                        bodyText
                    );

                    // =============================================
                    // LINKS
                    // =============================================

                    result.push(
                        '===== GOOGLE MAP LINKS ====='
                    );

                    var links =
                        document.querySelectorAll(
                            'a'
                        );

                    var linkCount =
                        0;

                    for (
                        var i = 0;
                        i < links.length;
                        i++
                    ) {

                        var a =
                            links[i];

                        var href =
                            a.href || '';

                        var txt =
                            (
                                a.innerText ||
                                a.textContent ||
                                ''
                            )
                            .trim()
                            .replace(
                                /\\s+/g,
                                ' '
                            );

                        if (
                            href.indexOf(
                                'google.com/maps'
                            ) >= 0 ||
                            href.indexOf(
                                '/maps/'
                            ) >= 0
                        ) {

                            result.push(
                                '[' +
                                linkCount +
                                '] ' +
                                txt +
                                ' -> ' +
                                href
                            );

                            linkCount++;
                        }
                    }

                    // =============================================
                    // ELEMENTI CON DATA ATTRIBUTES
                    // =============================================

                    result.push(
                        '===== DATA ATTRIBUTES ====='
                    );

                    var all =
                        document.querySelectorAll(
                            '*'
                        );

                    var dataCount =
                        0;

                    for (
                        var j = 0;
                        j < all.length &&
                        dataCount < 300;
                        j++
                    ) {

                        var el =
                            all[j];

                        if (
                            !el.attributes
                        ) {
                            continue;
                        }

                        var attrs = [];

                        for (
                            var k = 0;
                            k < el.attributes.length;
                            k++
                        ) {

                            var attr =
                                el.attributes[k];

                            if (
                                attr.name.indexOf(
                                    'data-'
                                ) === 0
                            ) {

                                attrs.push(
                                    attr.name +
                                    '=' +
                                    attr.value
                                );
                            }
                        }

                        if (
                            attrs.length > 0
                        ) {

                            var elementText =
                                (
                                    el.innerText ||
                                    ''
                                )
                                .trim()
                                .replace(
                                    /\\s+/g,
                                    ' '
                                );

                            if (
                                elementText.length >
                                200
                            ) {

                                elementText =
                                    elementText.substring(
                                        0,
                                        200
                                    );
                            }

                            result.push(
                                'TEXT=' +
                                elementText +
                                ' | ' +
                                attrs.join(
                                    ' ; '
                                )
                            );

                            dataCount++;
                        }
                    }

                    // =============================================
                    // SCRIPT
                    // =============================================

                    result.push(
                        '===== SCRIPT DATA ====='
                    );

                    var scripts =
                        document.querySelectorAll(
                            'script'
                        );

                    var scriptCount =
                        0;

                    for (
                        var s = 0;
                        s < scripts.length &&
                        scriptCount < 100;
                        s++
                    ) {

                        var script =
                            scripts[s];

                        var scriptText =
                            script.textContent ||
                            '';

                        scriptText =
                            scriptText.trim();

                        if (
                            scriptText.length === 0
                        ) {
                            continue;
                        }

                        var lower =
                            scriptText.toLowerCase();

                        if (
                            lower.indexOf(
                                'maps'
                            ) >= 0 ||
                            lower.indexOf(
                                'place'
                            ) >= 0 ||
                            lower.indexOf(
                                'list'
                            ) >= 0 ||
                            lower.indexOf(
                                'entity'
                            ) >= 0
                        ) {

                            if (
                                scriptText.length >
                                10000
                            ) {

                                scriptText =
                                    scriptText.substring(
                                        0,
                                        10000
                                    );
                            }

                            result.push(
                                'SCRIPT[' +
                                scriptCount +
                                ']=' +
                                scriptText
                            );

                            scriptCount++;
                        }
                    }

                    // =============================================
                    // PERFORMANCE RESOURCE
                    // =============================================

                    result.push(
                        '===== PERFORMANCE RESOURCES ====='
                    );

                    try {

                        var resources =
                            performance.getEntriesByType(
                                'resource'
                            );

                        var resourceCount =
                            0;

                        for (
                            var r = 0;
                            r < resources.length &&
                            resourceCount < 300;
                            r++
                        ) {

                            var resource =
                                resources[r];

                            var resourceUrl =
                                resource.name ||
                                '';

                            var lowerUrl =
                                resourceUrl.toLowerCase();

                            if (
                                lowerUrl.indexOf(
                                    'local'
                                ) >= 0 ||
                                lowerUrl.indexOf(
                                    'list'
                                ) >= 0 ||
                                lowerUrl.indexOf(
                                    'place'
                                ) >= 0 ||
                                lowerUrl.indexOf(
                                    'batchexecute'
                                ) >= 0 ||
                                lowerUrl.indexOf(
                                    'rpc'
                                ) >= 0 ||
                                lowerUrl.indexOf(
                                    'boq'
                                ) >= 0
                            ) {

                                result.push(
                                    resourceUrl
                                );

                                resourceCount++;
                            }
                        }

                    } catch(e) {

                        result.push(
                            'PERFORMANCE ERROR:' +
                            e.message
                        );
                    }

                    result.push(
                        '===== END SCAN ====='
                    );

                    var finalResult =
                        result.join(
                            '\\n'
                        );

                    if (
                        finalResult.length >
                        100000
                    ) {

                        finalResult =
                            finalResult.substring(
                                0,
                                100000
                            );
                    }

                    try {

                        TravelPins.log(
                            finalResult
                        );

                    } catch(e) {}

                    return 'SCAN_OK';

                } catch(e) {

                    try {

                        TravelPins.log(
                            'SCAN ERROR:' +
                            e.message
                        );

                    } catch(x) {}

                    return 'SCAN_ERROR:' +
                           e.message;
                }

            })();
            """.trimIndent()

        webView.evaluateJavascript(
            javascript
        ) { result ->

            addLog(
                """
                =========================================
                SCAN JAVASCRIPT RESULT

                $result

                =========================================
                """.trimIndent()
            )
        }
    }

    // ============================================================
    // NETWORK HOOK
    // ============================================================

    private fun injectNetworkHook() {

        val javascript =
            """
            (function() {

                if (
                    window.__travelpins_hooked
                ) {

                    return 'ALREADY_INSTALLED';
                }

                window.__travelpins_hooked =
                    true;

                // =================================================
                // FETCH
                // =================================================

                var originalFetch =
                    window.fetch;

                window.fetch =
                    async function() {

                        var input =
                            arguments[0];

                        var options =
                            arguments[1] || {};

                        var url =
                            typeof input ===
                            'string'
                            ? input
                            : (
                                input &&
                                input.url
                                ? input.url
                                : ''
                            );

                        var method =
                            options.method ||
                            (
                                typeof input !==
                                'string' &&
                                input
                                ? input.method
                                : 'GET'
                            ) ||
                            'GET';

                        var body =
                            options.body ||
                            '';

                        try {

                            TravelPins.network(
                                'FETCH_REQUEST',
                                method,
                                url,
                                String(body)
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

                                var lowerUrl =
                                    (
                                        url || ''
                                    )
                                    .toLowerCase();

                                if (
                                    lowerUrl.indexOf(
                                        'boq'
                                    ) >= 0 ||
                                    lowerUrl.indexOf(
                                        'list'
                                    ) >= 0 ||
                                    lowerUrl.indexOf(
                                        'place'
                                    ) >= 0 ||
                                    lowerUrl.indexOf(
                                        'rpc'
                                    ) >= 0 ||
                                    lowerUrl.indexOf(
                                        'batchexecute'
                                    ) >= 0
                                ) {

                                    TravelPins.network(
                                        'FETCH_RESPONSE',
                                        method,
                                        url,
                                        text
                                    );
                                }

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

                // =================================================
                // XHR OPEN
                // =================================================

                var originalOpen =
                    XMLHttpRequest
                        .prototype
                        .open;

                var originalSend =
                    XMLHttpRequest
                        .prototype
                        .send;

                XMLHttpRequest
                    .prototype
                    .open =
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

                // =================================================
                // XHR SEND
                // =================================================

                XMLHttpRequest
                    .prototype
                    .send =
                    function(body) {

                        var xhr =
                            this;

                        var method =
                            xhr.__tp_method ||
                            'GET';

                        var url =
                            xhr.__tp_url ||
                            '';

                        var lowerUrl =
                            String(url)
                                .toLowerCase();

                        var interesting =
                            lowerUrl.indexOf(
                                'boq'
                            ) >= 0 ||
                            lowerUrl.indexOf(
                                'list'
                            ) >= 0 ||
                            lowerUrl.indexOf(
                                'place'
                            ) >= 0 ||
                            lowerUrl.indexOf(
                                'rpc'
                            ) >= 0 ||
                            lowerUrl.indexOf(
                                'batchexecute'
                            ) >= 0 ||
                            lowerUrl.indexOf(
                                'local'
                            ) >= 0;

                        if (
                            interesting
                        ) {

                            try {

                                TravelPins.network(
                                    'XHR_REQUEST',
                                    method,
                                    url,
                                    body
                                    ? String(body)
                                    : ''
                                );

                            } catch(e) {}
                        }

                        xhr.addEventListener(
                            'load',
                            function() {

                                if (!interesting) {
                                    return;
                                }

                                try {

                                    var response =
                                        xhr.responseText ||
                                        '';

                                    TravelPins.network(
                                        'XHR_RESPONSE',
                                        method,
                                        url,
                                        response
                                    );

                                } catch(e) {

                                    try {

                                        TravelPins.network(
                                            'XHR_RESPONSE_ERROR',
                                            method,
                                            url,
                                            e.message
                                        );

                                    } catch(x) {}
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
            lower.contains(
                "google.com/maps"
            ) ||
            lower.contains(
                "maps.google"
            ) ||
            lower.contains(
                "/maps/preview"
            ) ||
            lower.contains(
                "entitylist"
            ) ||
            lower.contains(
                "placelist"
            ) ||
            lower.contains(
                "userlists"
            ) ||
            lower.contains(
                "local-search"
            ) ||
            lower.contains(
                "boq-local-search"
            ) ||
            lower.contains(
                "place"
            ) ||
            lower.contains(
                "saved"
            ) ||
            lower.contains(
                "list"
            ) ||
            lower.contains(
                "batchexecute"
            ) ||
            lower.contains(
                "rpc"
            ) ||
            lower.contains(
                "pb="
            ) ||
            lower.contains(
                "data="
            )

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
    // CONSENSO GOOGLE
    // ============================================================

    private fun acceptGoogleConsent() {

        addLog(
            """
            =========================================
            AVVIO ACCETTAZIONE GOOGLE

            =========================================
            """.trimIndent()
        )

        val javascript =
            """
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
                            text ===
                            'accetta tutto' ||
                            text ===
                            'accetta' ||
                            text ===
                            'accept all' ||
                            text ===
                            'accept'
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

                    return result.join(
                        '|'
                    );

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

                =========================================
                """.trimIndent()
            )
        }
    }

    // ============================================================
    // GOOGLE INTENT
    // ============================================================

    private fun handleGoogleIntent(
        intentUrl: String
    ) {

        addLog(
            """
            =========================================
            GOOGLE INTENT INTERCETTATO

            Google voleva aprire:
            APP GOOGLE MAPS

            =========================================
            CERCO FALLBACK WEB...

            """.trimIndent()
        )

        try {

            val uri =
                Uri.parse(
                    intentUrl
                )

            var fallback =
                uri.getQueryParameter(
                    "S.browser_fallback_url"
                )

            if (
                fallback.isNullOrBlank()
            ) {

                val marker =
                    "S.browser_fallback_url="

                val index =
                    intentUrl.indexOf(
                        marker
                    )

                if (index >= 0) {

                    fallback =
                        intentUrl.substring(
                            index +
                            marker.length
                        )

                    val endIntent =
                        fallback.indexOf(
                            "#Intent"
                        )

                    if (endIntent >= 0) {

                        fallback =
                            fallback.substring(
                                0,
                                endIntent
                            )
                    }

                    fallback =
                        fallback.substringBefore(
                            ";end;"
                        )

                    fallback =
                        Uri.decode(
                            fallback
                        )
                }
            }

            if (
                !fallback.isNullOrBlank()
            ) {

                fallback =
                    fallback
                        .replace(
                            ";end;",
                            ""
                        )
                        .replace(
                            ";end",
                            ""
                        )

                addLog(
                    """
                    [FALLBACK URL]

                    $fallback

                    =========================================
                    CARICO FALLBACK NELLA WEBVIEW...
                    =========================================
                    """.trimIndent()
                )

                webView.loadUrl(
                    fallback
                )

                return
            }

            addLog(
                """
                ❌ FALLBACK URL NON TROVATO

                =========================================
                """.trimIndent()
            )

        } catch (e: Exception) {

            addLog(
                """
                ❌ ERRORE PARSING INTENT

                ${e.message}

                =========================================
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
            =========================================
            LINK RICEVUTO

            $url

            =========================================
            AVVIO GOOGLE MAPS...
            """.trimIndent()
        )

        webView.loadUrl(
            url
        )
    }

    // ============================================================
    // JS STRING ESCAPE
    // ============================================================

    private fun jsQuote(
        value: String
    ): String {

        return "'" +
            value
                .replace(
                    "\\",
                    "\\\\"
                )
                .replace(
                    "'",
                    "\\'"
                )
                .replace(
                    "\n",
                    "\\n"
                )
                .replace(
                    "\r",
                    "\\r"
                ) +
            "'"
    }

    // ============================================================
    // LOG
    // ============================================================

    private fun addLog(
        text: String
    ) {

        log.add(
            text
        )

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
