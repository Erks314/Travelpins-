package com.travelpins.test

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
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

    private val log =
        ConcurrentLinkedQueue<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createInterface()

        createWebView()

        handleIntent(intent)
    }

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

                    updateScreen(
                        "Monitor pulito."
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
                    "TravelPins NETWORK TEST\n\n" +
                    "In attesa del link..."
            }

        val logScroll =
            ScrollView(this).apply {
                addView(output)
            }

        root.addView(
            toolbar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            logScroll,
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

            loadsImagesAutomatically = true

            javaScriptCanOpenWindowsAutomatically =
                true

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

        webView.webChromeClient =
            WebChromeClient()

        webView.webViewClient =
            object : WebViewClient() {

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {

                    inspectRequest(
                        request
                    )

                    return null
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {

                    addLog(
                        """
                        [NAVIGAZIONE]

                        ${request.url}

                        """.trimIndent()
                    )

                    return false
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
                }

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail
                ): Boolean {

                    addLog(
                        """
                        ==============================
                        ⚠️ WEBVIEW RENDERER TERMINATO

                        DID CRASH:
                        ${detail.didCrash()}

                        ==============================
                        """.trimIndent()
                    )

                    return true
                }
            }
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

        if (sharedText.isNullOrBlank()) {

            addLog(
                "Nessun testo ricevuto."
            )

            return
        }

        val match =
            Regex("""https?://\S+""")
                .find(sharedText)

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

        val entry =
            """
            [GOOGLE REQUEST]

            METHOD:
            ${request.method}

            URL:
            $url

            MAIN FRAME:
            ${request.isForMainFrame}

            """.trimIndent()

        addLog(entry)
    }

    private fun addLog(
        text: String
    ) {

        log.add(text)

        updateScreen()
    }

    private fun updateScreen() {

        runOnUiThread {

            val text =
                log.joinToString(
                    separator = "\n"
                )

            output.text =
                "TRAVELPINS NETWORK MONITOR\n\n" +
                text
        }
    }

    private fun updateScreen(
        text: String
    ) {

        runOnUiThread {
            output.text =
                "TRAVELPINS NETWORK MONITOR\n\n" +
                text
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

        if (webView.canGoBack()) {
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
