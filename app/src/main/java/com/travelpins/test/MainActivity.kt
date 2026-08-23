package com.travelpins.test

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

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

        if (intent == null) return

        if (intent.action != Intent.ACTION_SEND) return

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)

        if (sharedText.isNullOrBlank()) {
            output.text = """
                TravelPins TEST

                L'app è stata aperta,
                ma Google Maps non ha fornito
                alcun testo.
            """.trimIndent()
            return
        }

        output.text = """
            TravelPins TEST

            ✓ Link ricevuto!

            $sharedText

            --------------------

            Questo significa che
            Google Maps → TravelPins
            funziona correttamente.
        """.trimIndent()
    }
}
