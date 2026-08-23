package com.travelpins.test

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
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

            Cerco il List ID...
            """.trimIndent()
        )

        thread {

            try {

                // --------------------------------------------------
                // 1. RISOLVIAMO IL LINK
                // --------------------------------------------------

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

                // --------------------------------------------------
                // 2. PROVIAMO A DECODIFICARE L'URL
                // --------------------------------------------------

                val decodedUrl =
                    try {
                        URLDecoder.decode(
                            finalUrl,
                            "UTF-8"
                        )
                    } catch (e: Exception) {
                        finalUrl
                    }

                // --------------------------------------------------
                // 3. CERCHIAMO IL LIST ID IN TUTTE LE VARIANTI
                // --------------------------------------------------

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

                        Il link è arrivato correttamente,
                        ma Google ha cambiato il formato
                        dell'URL.

                        DIMENSIONE HTML:
                        ${html.length}
                        """.trimIndent()
                    )

                    return@thread
                }

                // --------------------------------------------------
                // 4. LIST ID TROVATO
                // --------------------------------------------------

                show(
                    """
                    ✅ LIST ID TROVATO!

                    $listId

                    ========================

                    Ora provo direttamente
                    la richiesta Google getlist...
                    """.trimIndent()
                )

                // --------------------------------------------------
                // 5. COSTRUIAMO LA RICHIESTA
                // --------------------------------------------------

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

                // --------------------------------------------------
                // 6. INVIO RICHIESTA
                // --------------------------------------------------

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

                // --------------------------------------------------
                // 7. MOSTRIAMO IL RISULTATO
                // --------------------------------------------------

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

    private fun findListId(
        text: String
    ): String? {

        if (text.isBlank()) {
            return null
        }

        // --------------------------------------------------
        // FORMATO CHE ABBIAMO VISTO NEL TUO LINK:
        //
        // !11m2!2sEoi6FS...
        // --------------------------------------------------

        val pattern1 =
            Regex(
                """!11m2!2s([A-Za-z0-9_-]{15,})"""
            )

        val match1 =
            pattern1.find(text)

        if (match1 != null) {
            return match1.groupValues[1]
        }

        // --------------------------------------------------
        // FORMATO GENERICO !2sID
        // --------------------------------------------------

        val pattern2 =
            Regex(
                """!2s([A-Za-z0-9_-]{15,})"""
            )

        val match2 =
            pattern2.find(text)

        if (match2 != null) {
            return match2.groupValues[1]
        }

        // --------------------------------------------------
        // FORMATO URL ENCODED
        // %21 = !
        // %32 = 2
        // %73 = s
        // --------------------------------------------------

        val pattern3 =
            Regex(
                """(?:%21|!)11m2(?:%21|!)2s([A-Za-z0-9_-]{15,})""",
                RegexOption.IGNORE_CASE
            )

        val match3 =
            pattern3.find(text)

        if (match3 != null) {
            return match3.groupValues[1]
        }

        // --------------------------------------------------
        // ULTIMO TENTATIVO:
        // cerchiamo una stringa che assomigli
        // al List ID che abbiamo già visto
        // --------------------------------------------------

        val pattern4 =
            Regex(
                """[A-Za-z0-9_-]{20,60}"""
            )

        val candidates =
            pattern4
                .findAll(text)
                .map { it.value }
                .toList()

        for (candidate in candidates) {

            if (
                candidate.length >= 20 &&
                candidate.any { it.isLetter() } &&
                candidate.any { it.isDigit() }
            ) {
                return candidate
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

    private fun show(
        message: String
    ) {

        runOnUiThread {
            output.text = message
        }
    }
}
