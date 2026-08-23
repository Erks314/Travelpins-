package com.travelpins.test

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URI
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val copyButton = Button(this).apply {
            text = "COPIA TUTTO"

            setOnClickListener {
                val clipboard =
                    getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager

                clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                        "TravelPins",
                        output.text.toString()
                    )
                )

                Toast.makeText(
                    this@MainActivity,
                    "Testo copiato!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val clearButton = Button(this).apply {
            text = "PULISCI"

            setOnClickListener {
                output.text = ""
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

        output = TextView(this).apply {
            textSize = 15f
            setTextIsSelectable(true)
            setTextColor(Color.BLACK)
            setPadding(20, 20, 20, 40)

            text =
                "TravelPins TEST\n\n" +
                "In attesa del link..."
        }

        val scrollView = ScrollView(this).apply {
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
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

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

        val sharedText =
            intent.getStringExtra(Intent.EXTRA_TEXT)

        if (sharedText.isNullOrBlank()) {
            show("Nessun testo ricevuto.")
            return
        }

        val match =
            Regex("""https?://\S+""")
                .find(sharedText)

        if (match == null) {
            show("Nessun link trovato.")
            return
        }

        val sharedUrl = match.value

        show(
            """
            TravelPins TEST

            Link ricevuto!

            Scarico la pagina Google Maps...
            """.trimIndent()
        )

        thread {

            try {

                // ==================================================
                // 1. SCARICA LA PAGINA
                // ==================================================

                val connection =
                    URL(sharedUrl)
                        .openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 20000
                connection.readTimeout = 20000

                val finalUrl =
                    connection.url.toString()

                val html =
                    connection.inputStream
                        .bufferedReader()
                        .use { it.readText() }

                connection.disconnect()

                // ==================================================
                // 2. CERCA DIRETTAMENTE ENTITYLIST/GETLIST
                // ==================================================

                val getListUrl =
                    extractGetListUrl(html)

                if (getListUrl == null) {

                    show(
                        """
                        ❌ GETLIST NON TROVATO

                        HTML SCARICATO:
                        ${html.length} caratteri

                        URL:

                        $finalUrl

                        ========================

                        Ho cercato:

                        entitylist/getlist

                        ma non è comparso nel formato
                        href previsto.

                        ========================

                        PRIME 3000 CARATTERI:

                        ${html.take(3000)}
                        """.trimIndent()
                    )

                    return@thread
                }

                // ==================================================
                // 3. MOSTRA L'URL TROVATO
                // ==================================================

                show(
                    """
                    ✅ GETLIST TROVATO!

                    URL:

                    $getListUrl

                    ========================

                    Invio la richiesta...
                    """.trimIndent()
                )

                // ==================================================
                // 4. CHIAMA L'URL ESATTO DI GOOGLE
                // ==================================================

                val apiConnection =
                    URL(getListUrl)
                        .openConnection() as HttpURLConnection

                apiConnection.requestMethod = "GET"

                apiConnection.connectTimeout = 20000
                apiConnection.readTimeout = 20000

                apiConnection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                    "AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) " +
                    "Chrome/131.0.0.0 Safari/537.36"
                )

                apiConnection.setRequestProperty(
                    "Accept",
                    "*/*"
                )

                val responseCode =
                    apiConnection.responseCode

                val response =
                    try {

                        apiConnection.inputStream
                            .bufferedReader()
                            .use { it.readText() }

                    } catch (e: Exception) {

                        apiConnection.errorStream
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            ?: ""
                    }

                apiConnection.disconnect()

                // ==================================================
                // 5. MOSTRA RISPOSTA
                // ==================================================

                val cleanResponse =
                    stripXssi(response)

                show(
                    """
                    🎯 RISPOSTA GOOGLE

                    HTTP:
                    $responseCode

                    DIMENSIONE:
                    ${cleanResponse.length}

                    ========================

                    GETLIST URL:

                    $getListUrl

                    ========================

                    RISPOSTA:

                    $cleanResponse
                    """.trimIndent()
                )

            } catch (e: Exception) {

                show(
                    """
                    ❌ ERRORE

                    ${e.javaClass.name}

                    ${e.message}
                    """.trimIndent()
                )
            }
        }
    }

    // ==========================================================
    // CERCA HREF CONTENENTE ENTITYLIST/GETLIST
    // ==========================================================

    private fun extractGetListUrl(
        html: String
    ): String? {

        val pattern =
            Regex(
                """href="([^"]*entitylist/getlist[^"]*)"""",
                RegexOption.IGNORE_CASE
            )

        val match =
            pattern.find(html)

        if (match == null) {
            return null
        }

        var url =
            match.groupValues[1]

        // HTML entities
        url =
            url
                .replace("&amp;", "&")
                .replace("&#39;", "'")
                .replace("&quot;", "\"")

        // Decodifica eventuale URL encoding
        url =
            try {
                URLDecoder.decode(
                    url,
                    "UTF-8"
                )
            } catch (e: Exception) {
                url
            }

        // Se Google ci dà un percorso relativo
        if (url.startsWith("/")) {
            url =
                "https://www.google.com$url"
        }

        return url
    }

    // ==========================================================
    // RIMUOVE IL PREFISSO XSSI DI GOOGLE
    // ==========================================================

    private fun stripXssi(
        response: String
    ): String {

        var result = response

        while (
            result.isNotEmpty() &&
            (
                result[0] == ')' ||
                result[0] == ']' ||
                result[0] == '}' ||
                result[0] == '\'' ||
                result[0] == '\n' ||
                result[0] == '\\'
            )
        ) {
            result =
                result.substring(1)
        }

        return result
    }

    private fun show(
        message: String
    ) {

        runOnUiThread {
            output.text = message
        }
    }
}
