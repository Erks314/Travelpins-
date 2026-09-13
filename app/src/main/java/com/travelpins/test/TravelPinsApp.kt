package com.travelpins.test

import android.app.Application
import com.travelpins.test.data.TravelPinsRepository
import com.travelpins.test.importer.EnrichmentManager
import com.travelpins.test.sync.DriveSyncManager

/**
 * Application class di TravelPins.
 *
 * Possiede le istanze uniche di:
 *  - TravelPinsRepository (database locale)
 *  - DriveSyncManager (sincronizzazione collaborativa Drive)
 *
 * Tutte le activity (MainActivity, PlaceDetailActivity) ottengono il
 * repository tramite (application as TravelPinsApp).repository,
 * garantendo che:
 *  - esista una sola istanza nel processo;
 *  - il DataLifecycleListener installato dal DriveSyncManager riceva
 *    gli eventi da ogni activity (import, delete, modifica note/categoria).
 */
class TravelPinsApp : Application() {

    lateinit var repository: TravelPinsRepository
        private set

    lateinit var driveSyncManager: DriveSyncManager
        private set

    override fun onCreate() {
        super.onCreate()

        // Repository locale.
        repository = TravelPinsRepository(this)

        // Avvia la coda di prefetch in background all'avvio del processo,
        // indipendentemente da quale schermata viene aperta.
        EnrichmentManager.attach(this)
        EnrichmentManager.start(this, repository)

        // DriveSyncManager: installa il lifecycle listener sul repository,
        // esegue il backfill degli uuid e avvia la prima sincronizzazione
        // se esiste già un file Drive collegato.
        driveSyncManager = DriveSyncManager(this, repository)
        driveSyncManager.start()
    }
}
