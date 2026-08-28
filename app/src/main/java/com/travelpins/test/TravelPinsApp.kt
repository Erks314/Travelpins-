package com.travelpins.test

import android.app.Application
import com.travelpins.test.data.TravelPinsRepository
import com.travelpins.test.importer.EnrichmentManager

/**
 * Avvia la coda di arricchimento all'avvio del processo,
 * indipendentemente da quale schermata viene aperta.
 */
class TravelPinsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // DISABILITATO TEMPORANEAMENTE per evitare interferenze
        // con il meccanismo di arricchimento in PlaceDetailActivity.
        // Verrà riabilitato nello Step 2 con il prefetch automatico.
        // EnrichmentManager.start(
        //     this,
        //     TravelPinsRepository(this)
        // )
    }
}
