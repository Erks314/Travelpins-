package com.travelpins.test

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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

    private val requests =
        ConcurrentLinkedQueue<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createInterface()

        configureWebView()

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

        val buttons =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER
            }

        val copyButton =
            Button(this).apply {

                text = "COPIA TUTTO"

                setOnClickListener {

                    val text =
                        output.text.toString()

                    val clipboard =
                        getSystemService(
                            Context.CLIPBOARD_SERVICE
                        ) as ClipboardManager

                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            "TravelPins",
                            text
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

                    requests.clear()

                    output.text =
                        "Monitor pulito.\n\n"
                }
            }

        buttons.addView(
            copyButton,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        buttons.addView(
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

                setTextColor(Color.BLACK)

                setPadding(
                    10,
                    10,
                    10,
                    30
                )

                text =
                    """
                    TravelPins NETWORK TEST

                    In attesa del link...

                    """.trimIndent()
            }

        val scroll =
            ScrollView(this).apply {
                addView(output)
            }

        root.addView(
            buttons,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

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

    private fun configureWebView() {

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
                "Chrome/131.0.0.0 Mobile Safari/537.36"
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

                    addNavigation(
                        request.url.toString()
                    )

                    return false
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): android.webkit.WebResourceResponse? {

                    val url =
                        request.url.toString()

                    inspectRequest(
                        url,
                        request.method
                    )

                    return null
                }

                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {

                    addEvent(
                        """
                        ================================
                        PAGINA COMPLETATA

                        $url

                        ================================
                        """.trimIndent()
                    )
                }
            }

        setContentView(
            buildMainLayout()
        )
    }

    private fun buildMainLayout():
            LinearLayout {

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

        val buttons =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER
            }

        val copy =
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

        val clear =
            Button(this).apply {

                text =
                    "PULISCI"

                setOnClickListener {

                    requests.clear()

                    output.text =
                        "Monitor pulito.\n"
                }
            }

        buttons.addView(
            copy,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        buttons.addView(
            clear,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        root.addView(buttons)

        val scroll =
            ScrollView(this).apply {

                addView(output)
            }

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        return root
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

        val text =
            intent.getStringExtra(
                Intent.EXTRA_TEXT
            )

        if (text.isNullOrBlank()) {

            addEvent(
                "Nessun testo ricevuto."
            )

            return
        }

        val match =
            Regex("""https?://\S+""")
                .find(text)

        if (match == null) {

            addEvent(
                "Nessun URL trovato."
            )

            return
        }

        val url =
            match.value

        addEvent(
            """
            ================================
            LINK RICEVUTO

            $url

            ================================
            AVVIO WEBVIEW...
            """.trimIndent()
        )

        webView.loadUrl(url)
    }

    private fun inspectRequest(
        url: String,
        method: String
    ) {

        val lower =
            url.lowercase()

        val interesting =
            lower.contains("maps") ||
            lower.contains("list") ||
            lower.contains("place") ||
            lower.contains("entity") ||
            lower.contains("preview") ||
            lower.contains("rpc") ||
            lower.contains("batchexecute") ||
            lower.contains("pb=") ||
            lower.contains("data=") ||
            lower.contains("saved")

        if (!interesting) {
            return
        }

        val entry =
            """
            
            [REQUEST]

            METHOD:
            $method

            URL:
            $url

            """.trimIndent()

        requests.add(entry)

        updateOutput()
    }

    private fun addNavigation(
        url: String
    ) {

        val entry =
            """

            [NAVIGATION]

            $url

            """.trimIndent()

        requests.add(entry)

        updateOutput()
    }

    private fun addEvent(
        text: String
    ) {

        requests.add(
            "\n$text\n"
        )

        updateOutput()
    }

    private fun updateOutput() {

        runOnUiThread {

            val text =
                requests.joinToString(
                    separator = "\n"
                )

            output.text =
                "TRAVELPINS NETWORK MONITOR\n\n" +
                text

            output.post {
                val scroll =
                    output.parent
                        as? ScrollView

                scroll?.fullScroll(
                    ScrollView.FOCUS_DOWN
                )
            }
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
