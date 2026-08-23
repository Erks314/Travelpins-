package com.travelpins.test

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        output = TextView(this).apply {
            textSize = 18f
            setPadding(40, 60, 40, 40)
            text = "TravelPins TEST\n\nCondividi una lista Google Maps con questa app."
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

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)

        output.text = if (sharedText.isNullOrBlank()) {
            """
            TravelPins TEST

            L'app è stata aperta,
            ma non è stato ricevuto alcun testo.
            """.trimIndent()
        } else {
            """
            TravelPins TEST

            ✓ LINK RICEVUTO!

            $sharedText

            --------------------

            Google Maps → TravelPins
            FUNZIONA!
            """.trimIndent()
        }
    }
}
