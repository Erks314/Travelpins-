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
            output.text = "Testo ricevuto:\n\n$sharedText"
            return
        }

        val url = match.value

        output.text = """
            TravelPins TEST

            Link ricevuto!

            $url

            Sto contattando Google...
        """.trimIndent()

        thread {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection

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

                val stream =
                    if (responseCode >= 400) {
                        connection.errorStream
                    } else {
                        connection.inputStream
                    }

                val body = stream?.bufferedReader()?.use {
                    it.readText()
                } ?: ""

                val preview = body.take(3000)

                runOnUiThread {
                    output.text = """
                        TRAVELPINS DIAGNOSTICA

                        CODICE HTTP:
                        $responseCode

                        URL FINALE:
                        $finalUrl

                        ----------------------------

                        RISPOSTA:

                        $preview
                    """.trimIndent()
                }

                connection.disconnect()

            } catch (e: Exception) {

                runOnUiThread {
                    output.text = """
                        TRAVELPINS DIAGNOSTICA

                        ERRORE:

                        ${e.javaClass.name}

                        ${e.message}

                        ----------------------------

                        $url
                    """.trimIndent()
                }
            }
        }
    }
}
