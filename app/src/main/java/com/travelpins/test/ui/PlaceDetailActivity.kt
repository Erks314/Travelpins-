package com.travelpins.test.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.travelpins.test.data.Place
import com.travelpins.test.data.TravelPinsRepository
import com.travelpins.test.importer.EnrichmentManager
import kotlinx.coroutines.launch

class PlaceDetailActivity : ComponentActivity() {

    enum class EnrichmentState { Idle, Loading, Done, Failed }

    companion object {
        const val EXTRA_PLACE_ID = "extra_place_id"

        fun newIntent(context: Context, placeId: Long): Intent =
            Intent(context, PlaceDetailActivity::class.java)
                .putExtra(EXTRA_PLACE_ID, placeId)
    }

    private lateinit var repository: TravelPinsRepository

    private val webViewState = mutableStateOf<android.webkit.WebView?>(null)
    private val enrichmentState = mutableStateOf(EnrichmentState.Idle)
    private val debugMessages = mutableStateListOf<String>()

    private var currentPlaceId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = TravelPinsRepository(applicationContext)

        val placeId = intent.getLongExtra(EXTRA_PLACE_ID, -1L)
        if (placeId == -1L) {
            finish()
            return
        }
        currentPlaceId = placeId

        EnrichmentManager.start(applicationContext, repository)

        // Se il luogo non ha foto o non e' mai stato arricchito,
        // forza l'aggiornamento automatico all'apertura.
        lifecycleScope.launch {
            val photoCount = repository.countPhotosByPlace(placeId)
            val place = repository.getPlaceById(placeId)
            val needsForce =
                photoCount == 0 || place?.detailsFetchedAt == null
            EnrichmentManager.prioritize(placeId, force = needsForce)
        }

        setContent {
            TravelPinsDarkTheme {
                PlaceDetailRoot(
                    repository = repository,
                    placeId = placeId,
                    webViewState = webViewState,
                    enrichmentState = enrichmentState.value,
                    debugMessages = debugMessages,
                    onBack = { finish() },
                    onStartEnrichmentIfNeeded = { place ->
                        EnrichmentManager.prioritize(place.id)
                    },
                    onForceRefresh = { place ->
                        EnrichmentManager.prioritize(place.id, force = true)
                        Toast.makeText(
                            this,
                            "Aggiornamento dati Google avviato",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onShare = { place -> sharePlace(place) },
                    onOpenGoogleMaps = { place -> openInGoogleMaps(place) },
                    onDelete = { place -> deletePlace(place) },
                    onAssignCategory = { placeId2, categoryId ->
                        lifecycleScope.launch {
                            repository.assignPlaceToCategory(placeId2, categoryId)
                        }
                    },
                    onCreateCategory = { name, color, icon ->
                        lifecycleScope.launch {
                            repository.createCategory(name, color, icon)
                        }
                    }
                )
            }
        }
    }

    private fun sharePlace(place: Place) {
        val link = place.mapsUrl
            ?: "https://www.google.com/maps/search/?api=1&query=" +
                "${place.latitude},${place.longitude}"

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${place.name}\n$link")
        }
        startActivity(
            Intent.createChooser(sendIntent, "Condividi luogo")
        )
    }

    private fun openInGoogleMaps(place: Place) {
        val uri = if (!place.mapsUrl.isNullOrBlank()) {
            Uri.parse(place.mapsUrl)
        } else {
            Uri.parse(
                "https://www.google.com/maps/search/?api=1&query=" +
                    "${place.latitude},${place.longitude}"
            )
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Impossibile aprire Google Maps.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun deletePlace(place: Place) {
        lifecycleScope.launch {
            repository.deletePlace(place)
            runOnUiThread { finish() }
        }
    }
}
