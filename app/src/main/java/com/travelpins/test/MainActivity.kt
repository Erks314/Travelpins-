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
import java.net.URLEncoder
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
            textIsSelectable = true
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

            Cerco il List ID...
            """.trimIndent()
        )

        thread {

            try {

                // ============================================
                // 1. RISOLUZIONE DEL LINK GOOGLE
                // ============================================

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
                    try {
                        connection.inputStream
                            .bufferedReader()
                            .use { it.readText() }
                    } catch (e: Exception) {
                        ""
                    }

                connection.disconnect()

                // ============================================
                // 2. DECODIFICA URL
                // ============================================

                val decodedUrl =
                    try {
                        URLDecoder.decode(
                            finalUrl,
                            "UTF-8"
                        )
                    } catch (e: Exception) {
                        finalUrl
                    }

                // ============================================
                // 3. CERCA LIST ID
                // ============================================

                val listId =
                    findListId(finalUrl)
                        ?: findListId(decodedUrl)
                        ?: findListId(sharedUrl)
                        ?: findListId(
                            try {
                                URLDecoder.decode(
                                    sharedUrl,
                                    "UTF-8"
                                )
                            } catch (e: Exception) {
                                sharedUrl
                            }
                        )
                        ?: findListId(html)

                if (listId == null) {

                    show(
                        """
                        ❌ LIST ID NON TROVATO

                        URL FINALE:

                        $finalUrl

                        ========================

                        URL DECODIFICATO:

                        $decodedUrl

                        ========================

                        DIMENSIONE HTML:

                        ${html.length}
                        """.trimIndent()
                    )

                    return@thread
                }

                // ============================================
                // 4. LIST ID TROVATO
                // ============================================

                show(
                    """
                    ✅ LIST ID TROVATO

                    $listId

                    ========================

                    Invio richiesta
                    Google getlist...
                    """.trimIndent()
                )

                // ============================================
                // 5. COSTRUZIONE PB
                // ============================================

                val pb =
                    buildPb(listId)

                val encodedPb =
                    URLEncoder.encode(
                        pb,
                        "UTF-8"
                    )

                val apiUrl =
                    "https://www.google.com/maps/preview/" +
                    "entitylist/getlist" +
                    "?authuser=0" +
                    "&hl=it" +
                    "&gl=it" +
                    "&pb=$encodedPb"

                // ============================================
                // 6. RICHIESTA GOOGLE
                // ============================================

                val apiConnection =
                    URL(apiUrl)
                        .openConnection() as HttpURLConnection

                apiConnection.requestMethod = "GET"

                apiConnection.connectTimeout = 20000
                apiConnection.readTimeout = 20000

                apiConnection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10) " +
                    "AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) " +
                    "Chrome/131.0.0.0 " +
                    "Mobile Safari/537.36"
                )

                apiConnection.setRequestProperty(
                    "Accept",
                    "*/*"
                )

                apiConnection.setRequestProperty(
                    "Referer",
                    "https://www.google.com/maps/"
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

                // ============================================
                // 7. RISPOSTA
                // ============================================

                val cleanResponse =
                    response
                        .removePrefix(")]}'")
                        .trim()

                show(
                    """
                    🎯 RISPOSTA GOOGLE

                    HTTP:
                    $responseCode

                    LIST ID:
                    $listId

                    DIMENSIONE:
                    ${cleanResponse.length}

                    ========================

                    RISPOSTA COMPLETA:

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

    // ================================================
    // CERCA LIST ID IN DIVERSI FORMATI
    // ================================================

    private fun findListId(text: String): String? {

        if (text.isBlank()) {
            return null
        }

        val patterns =
            listOf(

                Regex(
                    """!11m2!2s([A-Za-z0-9_-]{15,})"""
                ),

                Regex(
                    """!2s([A-Za-z0-9_-]{15,})"""
                ),

                Regex(
                    """(?:%21|!)11m2(?:%21|!)2s([A-Za-z0-9_-]{15,})""",
                    RegexOption.IGNORE_CASE
                )
            )

        for (pattern in patterns) {

            val match =
                pattern.find(text)

            if (match != null) {
                return match.groupValues[1]
            }
        }

        return null
    }

    // ================================================
    // COSTRUZIONE RICHIESTA GOOGLE
    // ================================================

    private fun buildPb(
        listId: String
    ): String {

        return "!1m4" +
                "!1s$listId" +
                "!2e1" +
                "!3m1!1e1" +
                "!2e2" +
                "!3e3" +
                "!4i500" +
                "!8i3" +
                "!16b1"
    }

    // ================================================
    // AGGIORNA SCHERMATA
    // ================================================

    private fun show(
        message: String
    ) {

        runOnUiThread {
            output.text = message
        }
    }
}
