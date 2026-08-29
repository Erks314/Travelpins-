package com.travelpins.test

import android.app.Application
import com.travelpins.test.data.TravelPinsRepository
import com.travelpins.test.importer.EnrichmentManager

/**
 * Avvia la coda di prefetch in background all'avvio del processo,
 * indipendentemente da quale schermata viene aperta.
 */
class TravelPinsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // RIABILITATO: prefetch automatico di tutti i luoghi in background
        EnrichmentManager.start(
            this,
            TravelPinsRepository(this)
        )
    }
}
