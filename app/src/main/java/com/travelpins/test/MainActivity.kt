package com.travelpins.test

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import java.net.URLDecoder

class MainActivity : Activity() {

    private lateinit var output: TextView
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        output = TextView(this).apply {
            textSize = 15f
            setPadding(25, 40, 25, 25)
            text = "TravelPins TEST\n\nIn attesa del link..."
        }

        webView = WebView(this)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true

        webView.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {

                val url = request?.url?.toString() ?: return false

                if (url.startsWith("intent://")) {

                    val marker = "S.browser_fallback_url="

                    val markerIndex = url.indexOf(marker)

                    if (markerIndex >= 0) {

                        val fallbackStart =
                            markerIndex + marker.length

                        val fallbackEnd =
                            url.indexOf(";end", fallbackStart)

                        if (fallbackEnd > fallbackStart) {

                            val encodedFallback =
                                url.substring(
                                    fallbackStart,
                                    fallbackEnd
                                )

                            try {

                                val fallbackUrl =
                                    URLDecoder.decode(
                                        encodedFallback,
                                        "UTF-8"
                                    )

                                view?.loadUrl(fallbackUrl)

                            } catch (e: Exception) {

                                update(
                                    """
                                    ERRORE DECODIFICA

                                    ${e.message}
                                    """.trimIndent()
                                )
                            }
                        }

                    }

                    return true
                }

                return false
            }

            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)

                update(
                    """
                    TravelPins TEST

                    Google Maps sta caricando...

                    URL:

                    $url
                    """.trimIndent()
                )
            }

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                super.onPageFinished(view, url)

                update(
                    """
                    TravelPins TEST

                    Google Maps caricata.

                    Sto cercando le richieste
                    della lista...

                    URL:

                    $url
                    """.trimIndent()
                )
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {

                val url = request?.url?.toString() ?: ""

                if (
                    url.contains("entitylist/getlist") ||
                    url.contains("entitylist")
                ) {

                    update(
                        """
                        🎯 RICHIESTA GOOGLE TROVATA!

                        URL:

                        $url

                        ========================

                        Abbiamo trovato
                        la richiesta della lista.
                        """.trimIndent()
                    )
                }

                return super.shouldInterceptRequest(
                    view,
                    request
                )
            }
        }

        setContentView(webView)

        processIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)

        processIntent(intent)
    }

    private fun processIntent(intent: Intent?) {

        if (intent?.action != Intent.ACTION_SEND) {
            return
        }

        val text =
            intent.getStringExtra(Intent.EXTRA_TEXT)

        if (text.isNullOrBlank()) {

            update(
                "Nessun testo ricevuto."
            )

            return
        }

        val match =
            Regex("""https?://\S+""")
                .find(text)

        if (match == null) {

            update(
                "Nessun link trovato."
            )

            return
        }

        val url = match.value

        update(
            """
            TravelPins TEST

            Link ricevuto!

            Apro Google Maps...

            $url
            """.trimIndent()
        )

        webView.loadUrl(url)
    }

    private fun update(message: String) {

        runOnUiThread {
            output.text = message
        }
    }

    override fun onDestroy() {

        webView.stopLoading()
        webView.destroy()

        super.onDestroy()
    }
}
