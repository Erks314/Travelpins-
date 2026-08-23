package com.travelpins.test

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.widget.TextView
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        output = TextView(this).apply {
            textSize = 16f
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

        if (intent?.action != Intent.ACTION_SEND) return

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)

        if (sharedText.isNullOrBlank()) {
            output.text = "Nessun link ricevuto."
            return
        }

        val match = Regex("""https?://\S+""").find(sharedText)

        if (match == null) {
            output.text = "Nessun URL trovato."
            return
        }

        val sharedUrl = match.value

        output.text = """
            TravelPins TEST

            Link ricevuto!

            Sto cercando i dati della lista...
        """.trimIndent()

        thread {

            try {

                // =====================================================
                // FASE 1
                // IMPORTANTE: NESSUN USER-AGENT
                // =====================================================

                val pageConnection =
                    URL(sharedUrl).openConnection() as HttpURLConnection

                pageConnection.requestMethod = "GET"
                pageConnection.instanceFollowRedirects = true
                pageConnection.connectTimeout = 20000
                pageConnection.readTimeout = 20000

                // NON impostiamo User-Agent qui.
                // Google deve restituire la pagina utile al parser.

                val pageCode = pageConnection.responseCode
                val finalUrl = pageConnection.url.toString()

                val html = pageConnection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                pageConnection.disconnect()

                // =====================================================
                // FASE 2
                // CERCHIAMO IL PRELOAD entitylist/getlist
                // =====================================================

                val regex = Regex(
                    """href="([^"]*entitylist/getlist[^"]*)""""
                )

                val matchApi = regex.find(html)

                if (matchApi == null) {

                    runOnUiThread {

                        output.text = """
                            ❌ GETLIST NON TROVATO

                            HTTP: $pageCode

                            URL FINALE:
                            $finalUrl

                            HTML ricevuto:
                            ${html.take(6000)}
                        """.trimIndent()
                    }

                    return@thread
                }

                var apiUrl = matchApi.groupValues[1]

                // Decodifica &amp; ecc.
                apiUrl = Html.fromHtml(
                    apiUrl,
                    Html.FROM_HTML_MODE_LEGACY
                ).toString()

                if (apiUrl.startsWith("/")) {
                    apiUrl = "https://www.google.com$apiUrl"
                }

                // =====================================================
                // FASE 3
                // CHIAMIAMO GOOGLE GETLIST
                // =====================================================

                runOnUiThread {
                    output.text = """
                        🎯 GETLIST TROVATO!

                        Sto scaricando i luoghi...
                    """.trimIndent()
                }

                val apiConnection =
                    URL(apiUrl).openConnection() as HttpURLConnection

                apiConnection.requestMethod = "GET"
                apiConnection.connectTimeout = 20000
                apiConnection.readTimeout = 20000

                // Qui invece il progetto funzionante usa
                // un normale User-Agent da Chrome.
                apiConnection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/131.0.0.0 Safari/537.36"
                )

                apiConnection.setRequestProperty(
                    "Accept",
                    "application/json,text/plain,*/*"
                )

                val apiCode = apiConnection.responseCode

                val rawResponse =
                    apiConnection.inputStream
                        .bufferedReader()
                        .use { it.readText() }

                apiConnection.disconnect()

                // =====================================================
                // FASE 4
                // TOGLIAMO LA PROTEZIONE XSSI DI GOOGLE
                // =====================================================

                val jsonText = rawResponse
                    .removePrefix(")]}'")
                    .trim()

                // =====================================================
                // FASE 5
                // PARSIAMO IL JSON
                // =====================================================

                val rootArray = JSONArray(jsonText)

                if (rootArray.length() == 0) {
                    throw Exception(
                        "Google ha restituito una risposta vuota."
                    )
                }

                val root = rootArray.getJSONArray(0)

                val listName =
                    if (root.length() > 4 &&
                        !root.isNull(4)
                    ) {
                        root.getString(4)
                    } else {
                        "Lista senza nome"
                    }

                if (root.length() <= 8 ||
                    root.isNull(8)
                ) {
                    throw Exception(
                        "La risposta Google non contiene i luoghi."
                    )
                }

                val places = root.getJSONArray(8)

                val result = StringBuilder()

                result.append("🎉 LISTA TROVATA!\n\n")
                result.append("Nome lista:\n")
                result.append(listName)
                result.append("\n\n")

                result.append("Luoghi trovati: ")
                result.append(places.length())
                result.append("\n\n")

                // Mostriamo i primi 20 per il test.
                val limit = minOf(20, places.length())

                for (i in 0 until limit) {

                    try {

                        val place = places.getJSONArray(i)

                        if (place.length() <= 2) continue

                        val name = place.optString(2)

                        var address = ""
                        var latitude = ""
                        var longitude = ""
                        var placeId = ""

                        if (place.length() > 1 &&
                            !place.isNull(1)
                        ) {

                            val info = place.getJSONArray(1)

                            // Indirizzo
                            if (info.length() > 2 &&
                                !info.isNull(2)
                            ) {
                                address = info.optString(2)
                            }

                            if (address.isBlank() &&
                                info.length() > 4 &&
                                !info.isNull(4)
                            ) {
                                address = info.optString(4)
                            }

                            // Coordinate
                            if (info.length() > 5 &&
                                !info.isNull(5)
                            ) {

                                val coords =
                                    info.getJSONArray(5)

                                if (coords.length() > 3) {

                                    latitude =
                                        coords.optString(2)

                                    longitude =
                                        coords.optString(3)
                                }
                            }

                            // Place ID / feature ID
                            if (info.length() > 7 &&
                                !info.isNull(7)
                            ) {
                                placeId =
                                    info.optString(7)
                            }
                        }

                        result.append("📍 ")
                        result.append(name)
                        result.append("\n")

                        if (address.isNotBlank()) {
                            result.append(address)
                            result.append("\n")
                        }

                        if (latitude.isNotBlank()) {
                            result.append(
                                "GPS: $latitude, $longitude\n"
                            )
                        }

                        if (placeId.isNotBlank()) {
                            result.append(
                                "ID: $placeId\n"
                            )
                        }

                        result.append("\n")

                    } catch (e: Exception) {

                        result.append(
                            "⚠️ Errore luogo $i: ${e.message}\n\n"
                        )
                    }
                }

                if (places.length() > 20) {
                    result.append(
                        "... e altri " +
                                (places.length() - 20) +
                                " luoghi."
                    )
                }

                runOnUiThread {
                    output.text = result.toString()
                }

            } catch (e: Exception) {

                runOnUiThread {

                    output.text = """
                        ❌ ERRORE

                        ${e.javaClass.name}

                        ${e.message}

                        ----------------------------

                        URL RICEVUTO:
                        $sharedUrl
                    """.trimIndent()
                }
            }
        }
    }
}
