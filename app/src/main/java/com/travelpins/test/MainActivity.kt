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

    private val log =
        ConcurrentLinkedQueue<String>()

    private val handler =
        Handler(Looper.getMainLooper())

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        createInterface()
        createWebView()
        handleIntent(intent)
    }

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
                        "TRAVELPINS NETWORK MONITOR\n\n" +
                        "Monitor pulito."
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
                    "TRAVELPINS NETWORK MONITOR\n\n" +
                    "In attesa del link..."
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

    private fun createWebView() {

        webView =
            WebView(this)

        webView.settings.apply {

            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true

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

                    /*
                     * QUESTO È IL PASSAGGIO NUOVO.
                     *
                     * Google prova ad aprire
                     * l'app Maps tramite intent://.
                     *
                     * Noi estraiamo invece
                     * S.browser_fallback_url
                     * e la carichiamo nella WebView.
                     */

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
                            "consent.google.com"
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

            if (
                fallback != null &&
                fallback.isNotBlank()
            ) {

                addLog(
                    """
                    [FALLBACK URL TROVATO]

                    $fallback

                    ==============================
                    CARICO FALLBACK NELLA WEBVIEW...
                    ==============================
                    """.trimIndent()
                )

                webView.loadUrl(
                    fallback
                )

                return
            }

            /*
             * Alcune versioni possono codificare
             * il parametro in modo differente.
             *
             * Proviamo quindi anche una
             * ricerca manuale.
             */

            val marker =
                "S.browser_fallback_url="

            val index =
                intentUrl.indexOf(
                    marker
                )

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
                    function() {

                        try {

                            var input =
                                arguments[0];

                            var url =
                                typeof input ===
                                'string'
                                ? input
                                : input.url;

                            console.log(
                                'TP_FETCH:' +
                                url
                            );

                        } catch(e) {}

                        return originalFetch.apply(
                            this,
                            arguments
                        );
                    };

                var originalOpen =
                    XMLHttpRequest.prototype.open;

                XMLHttpRequest.prototype.open =
                    function(
                        method,
                        url
                    ) {

                        try {

                            console.log(
                                'TP_XHR:' +
                                method +
                                ':' +
                                url
                            );

                        } catch(e) {}

                        return originalOpen.apply(
                            this,
                            arguments
                        );
                    };

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
            AVVIO GOOGLE MAPS...
            """.trimIndent()
        )

        webView.loadUrl(
            url
        )
    }

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

    override fun onNewIntent(
        intent: Intent
    ) {

        super.onNewIntent(intent)

        setIntent(intent)

        handleIntent(intent)
    }

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

    override fun onDestroy() {

        webView.stopLoading()
        webView.destroy()

        super.onDestroy()
    }
}
