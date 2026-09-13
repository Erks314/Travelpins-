package com.travelpins.test.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.ActivityResultLauncher
import com.travelpins.test.data.Category
import com.travelpins.test.data.DataLifecycleListener
import com.travelpins.test.data.Place
import com.travelpins.test.data.TravelPinsRepository
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Orchestratore della sincronizzazione Google Drive via SAF.
 *
 * - Istanza posseduta da TravelPinsApp (NON è un singleton object).
 * - Offline-first: Room resta la fonte operativa; Drive è solo dati collaborativi.
 * - Un file remoto invalido/corrotto non viene MAI modificato e non tocca Room.
 * - Reconnect guidato (Opzione 4): dopo reinstallazione l'utente riseleziona il
 *   file col picker; il contenuto viene validato e mostrato per conferma.
 * - Prima configurazione: l'utente può CREARE un nuovo file direttamente dal
 *   picker (ACTION_CREATE_DOCUMENT); un file vuoto/appena creato è valido e
 *   viene inizializzato al primo sync.
 */
class DriveSyncManager(
    context: Context,
    private val repository: TravelPinsRepository
) : DataLifecycleListener {

    companion object {
        /** Nome proposto dal picker in creazione. */
        const val DEFAULT_SYNC_FILE_NAME = "travelpins_sync.json"
    }

    private val appContext = context.applicationContext
    private val state = SyncState(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val status = state.status
    val candidate = state.candidate
    val lastError: String? get() = state.lastError

    private var syncJob: Job? = null
    private val debounceMs = 2000L

    // ===== Avvio =====

    /**
     * Da chiamare una sola volta da TravelPinsApp.onCreate().
     * Esegue il backfill uuid PRIMA che possa partire qualsiasi sync.
     */
    fun start() {
        scope.launch {
            repository.initSyncDataBackfill()
            repository.setLifecycleListener(this@DriveSyncManager)
            if (state.syncUri != null) {
                state.setStatus(SyncStatus.CONNECTED)
                scheduleSync()
            } else {
                state.setStatus(SyncStatus.DISCONNECTED)
            }
        }
    }

    // ===== Eventi dal repository (DataLifecycleListener) =====

    override fun onPlaceUpserted(place: Place) {
        val key = canonicalPlaceKey(place) ?: return
        state.removePendingPlaceTombstone(key)
    }

    override fun onPlaceDeleted(place: Place) {
        val key = canonicalPlaceKey(place) ?: return
        state.setPendingPlaceTombstone(key, System.currentTimeMillis())
        scheduleSync()
    }

    override fun onPlaceCollabChanged(place: Place) {
        val key = canonicalPlaceKey(place) ?: return
        state.setPlaceClock(key, System.currentTimeMillis())
        scheduleSync()
    }

    override fun onCategoryCreated(category: Category) {
        state.setCategoryClock(category.uuid, System.currentTimeMillis())
        scheduleSync()
    }

    override fun onCategoryUpdated(category: Category) {
        state.setCategoryClock(category.uuid, System.currentTimeMillis())
        scheduleSync()
    }

    override fun onCategoryDeleted(uuid: String, deletedAt: Long) {
        state.setPendingCategoryTombstone(uuid, deletedAt)
        scheduleSync()
    }

    // ===== SAF: picker (apri esistente / crea nuovo) =====

    fun pickerIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
    }

    /** Apre il picker per SELEZIONARE un file esistente (reconnect). */
    fun launchPicker(launcher: ActivityResultLauncher<Intent>) {
        launcher.launch(pickerIntent())
    }

    /**
     * Apre il picker in modalità CREAZIONE: il sistema crea un nuovo file
     * vuoto nella cartella scelta dall'utente, col nome proposto.
     * Il launcher è di tipo String perché il contract CreateDocument riceve
     * il nome iniziale del file come input.
     */
    fun launchCreatePicker(launcher: ActivityResultLauncher<String>) {
        launcher.launch(DEFAULT_SYNC_FILE_NAME)
    }

    /**
     * Gestisce il risultato del picker (sia apri che crea): legge e VALIDA il
     * candidato senza collegarlo né modificarlo.
     *
     * - raw == null            -> errore di lettura, nessun collegamento.
     * - raw vuoto/blank        -> file nuovo appena creato: VALIDO, trattato
     *                             come documento da inizializzare al primo sync.
     * - raw non vuoto e parse KO -> JSON corrotto: NESSUN collegamento, NESSUNA
     *                             modifica al file.
     * - raw non vuoto e parse OK -> candidato normale con metadati.
     */
    fun onPickerResult(uri: Uri?) {
        if (uri == null) {
            state.setStatus(if (state.syncUri != null) SyncStatus.CONNECTED else SyncStatus.DISCONNECTED)
            return
        }
        scope.launch {
            val raw = readRaw(uri)
            if (raw == null) {
                state.setStatus(SyncStatus.ERROR, "Impossibile leggere il file selezionato")
                return@launch
            }
            val trimmed = raw.trim()
            val parsed = if (trimmed.isEmpty()) {
                // File nuovo/vuoto: documento valido da inizializzare.
                SyncRoot(
                    schemaVersion = SUPPORTED_SCHEMA_VERSION,
                    revision = 0L,
                    deviceId = "",
                    syncFileId = null,
                    categories = emptyMap(),
                    lists = emptyMap()
                )
            } else {
                SyncJson.parse(trimmed)
            }
            if (parsed == null) {
                // JSON corrotto o schema non supportato: NON collegato, NON modificato.
                state.setStatus(SyncStatus.ERROR, "File non valido: seleziona travelpins_sync.json")
                return@launch
            }
            // Firma reale: getDocumentId(Uri) — NESSUN parametro Context.
            val documentId = try { DocumentsContract.getDocumentId(uri) } catch (_: Exception) { null }
            state.setCandidate(
                SyncCandidate(
                    uri = uri,
                    documentId = documentId,
                    fileName = queryDisplayName(uri),
                    syncFileId = parsed.syncFileId,
                    revision = parsed.revision,
                    lastDeviceId = parsed.deviceId,
                    listCount = parsed.lists.size,
                    categoryCount = parsed.categories.size
                )
            )
        }
    }

    /** Conferma UI del candidato: persiste il collegamento con permessi SAF. */
    fun confirmCandidate() {
        val cand = state.candidate.value ?: return
        scope.launch {
            try {
                appContext.contentResolver.takePersistableUriPermission(
                    cand.uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
                state.setStatus(SyncStatus.ERROR, "Permesso non concesso sul file selezionato")
                return@launch
            }
            state.syncUri = cand.uri
            state.documentId = cand.documentId
            state.fileName = cand.fileName
            state.mimeType = "application/json"
            state.setCandidate(null)
            state.setStatus(SyncStatus.CONNECTED)
            syncNow()
        }
    }

    /**
     * Annulla il candidato senza collegarlo (chiusura dialogo di conferma).
     * Non modifica il file Drive, non modifica Room, non tocca lo stato
     * collaborativo: pulisce esclusivamente il candidato in attesa.
     */
    fun dismissCandidate() {
        state.setCandidate(null)
    }

    fun disconnect() {
        state.syncUri = null
        state.documentId = null
        state.fileName = null
        state.mimeType = null
        state.setStatus(SyncStatus.DISCONNECTED)
    }

    // ===== Sync =====

    fun scheduleSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            delay(debounceMs)
            syncOnce()
        }
    }

    fun syncNow() {
        syncJob?.cancel()
        syncJob = scope.launch { syncOnce() }
    }

    private suspend fun syncOnce() {
        val uri = state.syncUri
        if (uri == null) {
            state.setStatus(SyncStatus.DISCONNECTED)
            return
        }
        state.setStatus(SyncStatus.SYNCING)

        var attempts = 0
        while (attempts < 3) {
            attempts++

            // 1. Leggi remoto e valida. File vuoto = documento nuovo da inizializzare.
            val raw = readRaw(uri)
            val trimmed = raw?.trim()
            val remote = if (trimmed.isNullOrEmpty()) {
                SyncRoot(
                    schemaVersion = SUPPORTED_SCHEMA_VERSION,
                    revision = 0L,
                    deviceId = "",
                    syncFileId = null,
                    categories = emptyMap(),
                    lists = emptyMap()
                )
            } else {
                raw?.let { SyncJson.parse(it) }
            }
            if (raw != null && remote == null) {
                state.setStatus(SyncStatus.ERROR, "File Drive non valido o schema non supportato")
                return
            }
            val effectiveRemote = remote ?: SyncRoot(
                schemaVersion = SUPPORTED_SCHEMA_VERSION,
                revision = 0L,
                deviceId = "",
                syncFileId = null,
                categories = emptyMap(),
                lists = emptyMap()
            )

            // 2. Backup locale dell'ultima versione remota VALIDA e NON VUOTA (prima del merge).
            if (!trimmed.isNullOrEmpty()) state.lastValidRemoteJson = raw

            // 3. Snapshot locale.
            val local = buildLocalSnapshot()

            // 4. Merge.
            val merged = SyncMerger.merge(local, effectiveRemote, state.deviceId, System.currentTimeMillis())

            // 4b. Riempie categoryUuid nei record generati dal locale (id -> uuid).
            val rootWithUuids = fillLocalCategoryUuids(merged.newRoot, local)

            // 4c. syncFileId: generato UNA sola volta al primo write, poi immutabile.
            val finalRoot = if (rootWithUuids.syncFileId == null) {
                rootWithUuids.copy(syncFileId = UUID.randomUUID().toString())
            } else {
                rootWithUuids
            }

            // 5. Re-read per rilevare scritture concorrenti (mitigazione, non CAS).
            val raw2 = readRaw(uri)
            val trimmed2 = raw2?.trim()
            val remote2 = if (trimmed2.isNullOrEmpty()) {
                effectiveRemote
            } else {
                raw2?.let { SyncJson.parse(it) }
            }
            if (remote2 != null && remote2.revision != effectiveRemote.revision) {
                continue
            }

            // 6. Scrivi.
            val written = writeRaw(uri, SyncJson.serialize(finalRoot))
            if (!written) {
                state.setStatus(SyncStatus.ERROR, "Scrittura su Drive fallita")
                return
            }

            // 7. Applica al Room le modifiche remote (con isSyncApplying).
            applyToRoom(merged)

            // 8. Pulisci i pending tombstone realmente propagati.
            cleanupPending(finalRoot)

            // 9. Metadati di sync.
            state.lastSyncTs = System.currentTimeMillis()
            state.lastSyncRevision = finalRoot.revision
            state.setStatus(SyncStatus.CONNECTED)
            return
        }

        state.setStatus(SyncStatus.ERROR, "Conflitti concorrenti: riprova la sincronizzazione")
    }

    // ===== Supporto =====

    private suspend fun buildLocalSnapshot(): LocalSnapshot {
        val categories = repository.categories.first()
        val places = repository.places.first()
        return LocalSnapshot(
            categories = categories,
            places = places,
            placeClocks = state.getPlaceClocks(),
            categoryClocks = state.getCategoryClocks(),
            pendingPlaceTombstones = state.getPendingPlaceTombstones(),
            pendingCategoryTombstones = state.getPendingCategoryTombstones(),
            lastSyncTs = state.lastSyncTs
        )
    }

    /** Riempie categoryUuid nei PlaceRecord prodotti dal locale usando id -> uuid. */
    private fun fillLocalCategoryUuids(root: SyncRoot, local: LocalSnapshot): SyncRoot {
        val uuidById = local.categories.associate { it.id to it.uuid }
        val placeById = local.places.associateBy { it.id }
        val newLists = root.lists.mapValues { (_, list) ->
            list.copy(places = list.places.mapValues { (key, rec) ->
                if (rec.categoryUuid == null && !rec.deleted) {
                    val localPlace = placeById.values.firstOrNull { p -> canonicalPlaceKey(p) == key }
                    val uuid = localPlace?.categoryId?.let { uuidById[it] }
                    rec.copy(categoryUuid = uuid)
                } else {
                    rec
                }
            })
        }
        return root.copy(lists = newLists)
    }

    private suspend fun applyToRoom(merged: MergeOutput) {
        for (cat in merged.categoryUpserts) repository.applyRemoteCategoryUpsert(cat)
        for (uuid in merged.categoryDeletes) repository.applyRemoteCategoryDelete(uuid)

        if (merged.placeCollab.isNotEmpty()) {
            val categories = repository.categories.first()
            val idByUuid = categories.associate { it.uuid to it.id }
            for (action in merged.placeCollab) {
                val categoryId = action.categoryUuid?.let { idByUuid[it] }
                repository.applyRemotePlaceCollab(action.placeId, categoryId, action.note)
            }
        }
    }

    /** Rimuove i pending tombstone solo se presenti nel documento scritto come deleted. */
    private fun cleanupPending(root: SyncRoot) {
        val pendingPlaces = state.getPendingPlaceTombstones()
        for ((key, ts) in pendingPlaces) {
            val propagated = root.lists.values.any { list ->
                list.places[key]?.let { it.deleted && it.updatedAt >= ts } == true
            }
            if (propagated) state.removePendingPlaceTombstone(key)
        }
        val pendingCats = state.getPendingCategoryTombstones()
        for ((uuid, ts) in pendingCats) {
            val rec = root.categories[uuid]
            if (rec != null && rec.deleted && rec.updatedAt >= ts) state.removePendingCategoryTombstone(uuid)
        }
    }

    private fun readRaw(uri: Uri): String? {
        return try {
            appContext.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeRaw(uri: Uri, content: String): Boolean {
        return try {
            appContext.contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(content.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
