package com.travelpins.test

import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)

        setContentView(webView)

        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadsImagesAutomatically = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(false)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(
            webView,
            true
        )

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {

                return false
            }

            override fun onPageFinished(
                view: WebView,
                url: String
            ) {

                Toast.makeText(
                    this@MainActivity,
                    "Pagina caricata",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(
        intent: android.content.Intent?
    ) {

        if (intent?.action != android.content.Intent.ACTION_SEND) {
            return
        }

        val text =
            intent.getStringExtra(
                android.content.Intent.EXTRA_TEXT
            )

        if (text.isNullOrBlank()) {
            return
        }

        val match =
            Regex("""https?://\S+""")
                .find(text)

        if (match == null) {
            return
        }

        val url = match.value

        webView.loadUrl(url)
    }

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
