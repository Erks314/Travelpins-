package com.travelpins.test

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

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

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {

        if (intent?.action != Intent.ACTION_SEND) return

        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return

        val url = Regex("""https?://\S+""")
            .find(text)
            ?.value
            ?: return

        output.text = """
            TravelPins TEST

            Link ricevuto:

            $url

            ✓ Google Maps ha passato correttamente
            il link alla nostra app.

            PROSSIMO PASSO:
            estrarre i luoghi della lista.
        """.trimIndent()
    }
}
