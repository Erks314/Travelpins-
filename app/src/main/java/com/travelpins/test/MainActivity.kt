package com.travelpins.test

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
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

            Analizzo Google Maps...

            $sharedUrl
            """.trimIndent()
        )

        thread {

            try {

                val connection =
                    URL(sharedUrl)
                        .openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 20000
                connection.readTimeout = 20000

                connection.setRequestProperty(
                    "Accept-Language",
                    "it-IT,it;q=0.9,en;q=0.8"
                )

                val responseCode =
                    connection.responseCode

                val finalUrl =
                    connection.url.toString()

                val html =
                    connection.inputStream
                        .bufferedReader()
                        .use { it.readText() }

                connection.disconnect()

                // -----------------------------------------------------
                // CERCHIAMO LE STRUTTURE INTERESSANTI
                // -----------------------------------------------------

                val appStateIndex =
                    html.indexOf(
                        "APP_INITIALIZATION_STATE",
                        ignoreCase = true
                    )

                val entityListIndex =
                    html.indexOf(
                        "entitylist",
                        ignoreCase = true
                    )

                val getListIndex =
                    html.indexOf(
                        "getlist",
                        ignoreCase = true
                    )

                val listId =
                    extractListId(finalUrl)

                // -----------------------------------------------------
                // CERCHIAMO COORDINATE NELLA PAGINA
                // -----------------------------------------------------

                val coordinateMatches =
                    Regex(
                        """-?\d{1,3}\.\d{4,}[^0-9]{1,20}-?\d{1,3}\.\d{4,}"""
                    )
                        .findAll(html)
                        .take(20)
                        .map { it.value }
                        .toList()

                // -----------------------------------------------------
                // CREIAMO UNA DIAGNOSTICA MOLTO PIÙ UTILE
                // -----------------------------------------------------

                val result =
                    StringBuilder()

                result.append(
                    "TravelPins TEST\n\n"
                )

                result.append(
                    "HTTP: $responseCode\n\n"
                )

                result.append(
                    "URL FINALE:\n$finalUrl\n\n"
                )

                result.append(
                    "LIST ID:\n${listId ?: "NON TROVATO"}\n\n"
                )

                result.append(
                    "APP_INITIALIZATION_STATE:\n"
                )

                result.append(
                    if (appStateIndex >= 0)
                        "✅ TROVATO"
                    else
                        "❌ NON TROVATO"
                )

                result.append("\n\n")

                result.append(
                    "ENTITYLIST:\n"
                )

                result.append(
                    if (entityListIndex >= 0)
                        "✅ TROVATO"
                    else
                        "❌ NON TROVATO"
                )

                result.append("\n\n")

                result.append(
                    "GETLIST:\n"
                )

                result.append(
                    if (getListIndex >= 0)
                        "✅ TROVATO"
                    else
                        "❌ NON TROVATO"
                )

                result.append("\n\n")

                result.append(
                    "COORDINATE INDIVIDUATE:\n"
                )

                if (coordinateMatches.isEmpty()) {

                    result.append(
                        "Nessuna coppia trovata."
                    )

                } else {

                    coordinateMatches.forEach {
                        result.append(
                            "$it\n"
                        )
                    }
                }

                // -----------------------------------------------------
                // SE TROVIAMO APP_INITIALIZATION_STATE,
                // MOSTRIAMO UNA FINESTRA ATTORNO AL PUNTO
                // -----------------------------------------------------

                if (appStateIndex >= 0) {

                    val start =
                        maxOf(
                            0,
                            appStateIndex - 1000
                        )

                    val end =
                        minOf(
                            html.length,
                            appStateIndex + 8000
                        )

                    result.append(
                        "\n\n========================\n"
                    )

                    result.append(
                        "ESTRATTO APP_INITIALIZATION_STATE:\n"
                    )

                    result.append(
                        "========================\n\n"
                    )

                    result.append(
                        html.substring(
                            start,
                            end
                        )
                    )
                }

                // -----------------------------------------------------
                // SE NON TROVIAMO QUELLO,
                // MOSTRIAMO UN ESTRATTO DELL'HTML
                // -----------------------------------------------------

                if (
                    appStateIndex < 0 &&
                    entityListIndex < 0 &&
                    getListIndex < 0
                ) {

                    result.append(
                        "\n\n========================\n"
                    )

                    result.append(
                        "ESTRATTO HTML:\n"
                    )

                    result.append(
                        "========================\n\n"
                    )

                    result.append(
                        html.take(10000)
                    )
                }

                show(result.toString())

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
                    """!2s([A-Za-z0-9_-]{10,})"""
                ),

                Regex(
                    """!1s([A-Za-z0-9_-]{10,})"""
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

    private fun show(message: String) {

        runOnUiThread {
            output.text = message
        }
    }
}
