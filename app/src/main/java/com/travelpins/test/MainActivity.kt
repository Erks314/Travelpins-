package com.travelpins.test

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        output = TextView(this).apply {
            textSize = 15f
            setPadding(30, 50, 30, 30)
            text = "TravelPins TEST\n\nIn attesa del link..."
        }

        setContentView(output)

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

            Recupero il List ID...
            """.trimIndent()
        )

        thread {

            try {

                // -------------------------------------------------
                // 1. RISOLVIAMO IL LINK BREVE
                // -------------------------------------------------

                val redirectConnection =
                    URL(sharedUrl)
                        .openConnection() as HttpURLConnection

                redirectConnection.requestMethod = "GET"
                redirectConnection.instanceFollowRedirects = true
                redirectConnection.connectTimeout = 20000
                redirectConnection.readTimeout = 20000

                val finalUrl =
                    redirectConnection.url.toString()

                redirectConnection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                redirectConnection.disconnect()

                // -------------------------------------------------
                // 2. ESTRAIAMO IL LIST ID
                // -------------------------------------------------

                val listId =
                    extractListId(finalUrl)

                if (listId == null) {

                    show(
                        """
                        ❌ LIST ID NON TROVATO

                        URL:

                        $finalUrl
                        """.trimIndent()
                    )

                    return@thread
                }

                show(
                    """
                    TravelPins TEST

                    ✅ LIST ID TROVATO

                    $listId

                    Costruisco la richiesta
                    Google getlist...
                    """.trimIndent()
                )

                // -------------------------------------------------
                // 3. COSTRUIAMO IL PB
                // -------------------------------------------------

                val pb = buildPb(listId)

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

                // -------------------------------------------------
                // 4. PROVIAMO LA RICHIESTA
                // -------------------------------------------------

                show(
                    """
                    TravelPins TEST

                    List ID:
                    $listId

                    Invio richiesta Google...

                    Attendo risposta...
                    """.trimIndent()
                )

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

                // -------------------------------------------------
                // 5. ANALIZZIAMO LA RISPOSTA
                // -------------------------------------------------

                val cleanResponse =
                    response
                        .removePrefix(")]}'")
                        .trim()

                val placeCount =
                    countLikelyPlaces(cleanResponse)

                val preview =
                    cleanResponse.take(12000)

                show(
                    """
                    🎯 RISPOSTA GOOGLE

                    HTTP:
                    $responseCode

                    LIST ID:
                    $listId

                    DIMENSIONE RISPOSTA:
                    ${cleanResponse.length} caratteri

                    POSSIBILI LUOGHI:
                    $placeCount

                    ========================

                    RISPOSTA:

                    $preview
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

    private fun extractListId(
        url: String
    ): String? {

        val patterns =
            listOf(

                Regex(
                    """!2s([A-Za-z0-9_-]{15,})"""
                ),

                Regex(
                    """!1s([A-Za-z0-9_-]{15,})"""
                ),

                Regex(
                    """2s([A-Za-z0-9_-]{15,})"""
                )
            )

        for (pattern in patterns) {

            val match =
                pattern.find(url)

            if (match != null) {
                return match.groupValues[1]
            }
        }

        return null
    }

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

    private fun countLikelyPlaces(
        response: String
    ): Int {

        var count = 0

        val occurrences =
            listOf(
                "\"GPS\"",
                "\"google_place_id\"",
                "maps.google.com",
                "maps/preview"
            )

        for (term in occurrences) {

            count +=
                response
                    .windowed(
                        term.length,
                        1
                    )
                    .count {
                        it == term
                    }
        }

        return count
    }

    private fun show(
        message: String
    ) {

        runOnUiThread {
            output.text = message
        }
    }
}
