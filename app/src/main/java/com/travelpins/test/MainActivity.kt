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

        val url = match.value

        output.text = """
            TravelPins TEST

            Link ricevuto!

            Sto analizzando Google Maps...
        """.trimIndent()

        thread {

            try {

                val connection =
                    URL(url).openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
                )

                val responseCode = connection.responseCode
                val finalUrl = connection.url.toString()

                val html = connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                connection.disconnect()

                val index = html.indexOf("entitylist/getlist")

                runOnUiThread {

                    if (index >= 0) {

                        val start = maxOf(0, index - 1000)
                        val end = minOf(html.length, index + 3000)

                        output.text = """
                            🎉 TROVATO!

                            HTTP: $responseCode

                            URL FINALE:
                            $finalUrl

                            ============================

                            ENTITYLIST/GETLIST TROVATO

                            ============================

                            $html.substring($start, $end)
                        """.trimIndent()

                    } else {

                        output.text = """
                            TravelPins TEST

                            HTTP: $responseCode

                            entitylist/getlist
                            NON TROVATO.

                            HTML ricevuto:
                            ${html.take(5000)}
                        """.trimIndent()
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {
                    output.text = """
                        ERRORE

                        ${e.javaClass.name}

                        ${e.message}
                    """.trimIndent()
                }
            }
        }
    }
}               
