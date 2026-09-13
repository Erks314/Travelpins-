package com.travelpins.test.data

import android.content.Context
import android.util.Log
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class SyncResult(val added: Int, val removed: Int, val updated: Int)

/**
 * Eventi di ciclo vita dei dati collaborativi, creati ex novo per la
 * sincronizzazione Drive (non esistevano nel codice precedente).
 *
 * Regola di emissione: gli eventi vengono emessi normalmente, TRANNE
 * quando isSyncApplying == true (ovvero mentre il DriveSyncManager sta
 * applicando al Room modifiche provenienti dal file Drive). Questo evita
 * il loop Room -> listener -> Drive -> Room.
 *
 * Tutti i metodi hanno implementazione di default vuota: il listener
 * implementa solo gli eventi che gli servono.
 */
interface DataLifecycleListener {
    /** Un luogo è stato inserito o aggiornato da Google Maps (syncListPlaces). */
    fun onPlaceUpserted(place: Place) {}

    /** Un luogo è stato rimosso dal Room (deletePlaceCompletely). */
    fun onPlaceDeleted(place: Place) {}

    /** L'utente ha modificato i dati collaborativi di un luogo (nota o categoria). */
    fun onPlaceCollabChanged(place: Place) {}

    /** L'utente ha creato una categoria. */
    fun onCategoryCreated(category: Category) {}

    /** L'utente ha modificato una categoria. */
    fun onCategoryUpdated(category: Category) {}

    /** Una categoria è stata eliminata; l'uuid è catturato PRIMA della delete. */
    fun onCategoryDeleted(uuid: String, deletedAt: Long) {}
}

class TravelPinsRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val placeDao = db.placeDao()
    private val categoryDao = db.categoryDao()
    private val placePhotoDao = db.placePhotoDao()
    private val placeReviewDao = db.placeReviewDao()
    private val sourceListDao = db.sourceListDao()
    private val prefs = context.applicationContext.getSharedPreferences("travelpins_prefs", Context.MODE_PRIVATE)

    // ===== Sincronizzazione Drive =====
    private var lifecycleListener: DataLifecycleListener? = null
    private val isSyncApplying = AtomicBoolean(false)

    fun setLifecycleListener(listener: DataLifecycleListener?) {
        lifecycleListener = listener
    }

    private fun emitPlaceUpserted(place: Place) {
        if (!isSyncApplying.get()) lifecycleListener?.onPlaceUpserted(place)
    }

    private fun emitPlaceDeleted(place: Place) {
        if (!isSyncApplying.get()) lifecycleListener?.onPlaceDeleted(place)
    }

    private fun emitPlaceCollabChanged(place: Place) {
        if (!isSyncApplying.get()) lifecycleListener?.onPlaceCollabChanged(place)
    }

    private fun emitCategoryCreated(category: Category) {
        if (!isSyncApplying.get()) lifecycleListener?.onCategoryCreated(category)
    }

    private fun emitCategoryUpdated(category: Category) {
        if (!isSyncApplying.get()) lifecycleListener?.onCategoryUpdated(category)
    }

    private fun emitCategoryDeleted(uuid: String, deletedAt: Long) {
        if (!isSyncApplying.get()) lifecycleListener?.onCategoryDeleted(uuid, deletedAt)
    }

    /**
     * Backfill degli uuid per le categorie create prima dell'introduzione
     * della sincronizzazione. Genera UUID in Kotlin (SQLite non ha una
     * random_uuid() portabile). Chiamato da DriveSyncManager.start() prima
     * che possa partire qualsiasi sincronizzazione.
     */
    suspend fun initSyncDataBackfill() {
        val missing = categoryDao.getAllWithEmptyUuid()
        for (cat in missing) {
            categoryDao.updateUuid(cat.id, UUID.randomUUID().toString())
        }
    }

    /**
     * Applica al Room una categoria proveniente dal file Drive (insert o
     * update by uuid). Eseguita con isSyncApplying=true: non emette eventi,
     * quindi non innesca un nuovo sync.
     */
    suspend fun applyRemoteCategoryUpsert(category: Category) {
        isSyncApplying.set(true)
        try {
            val existing = categoryDao.getByUuid(category.uuid)
            if (existing == null) {
                categoryDao.insert(category)
            } else {
                categoryDao.update(category.copy(id = existing.id))
            }
        } finally {
            isSyncApplying.set(false)
        }
    }

    /** Applica al Room il tombstone di una categoria proveniente dal Drive. */
    suspend fun applyRemoteCategoryDelete(uuid: String) {
        isSyncApplying.set(true)
        try {
            categoryDao.deleteByUuid(uuid)
        } finally {
            isSyncApplying.set(false)
        }
    }

    /**
     * Applica al Room i dati collaborativi di un luogo provenienti dal Drive
     * (categoria assegnata e nota). Usa i DAO esistenti; nessun campo
     * anagrafico del luogo viene toccato.
     */
    suspend fun applyRemotePlaceCollab(placeId: Long, categoryId: Long?, note: String?) {
        isSyncApplying.set(true)
        try {
            placeDao.assignCategory(placeId, categoryId)
            placeDao.updateNote(placeId, note)
        } finally {
            isSyncApplying.set(false)
        }
    }

    // ===== Flussi osservabili (invariati) =====

    val places: Flow<List<Place>> = placeDao.observeAll()
    val categories: Flow<List<Category>> = categoryDao.observeAll()

    fun placesInCategory(categoryId: Long): Flow<List<Place>> =
        placeDao.observeByCategory(categoryId)

    val uncategorizedPlaces: Flow<List<Place>> =
        placeDao.observeUncategorized()

    fun observePlaceById(placeId: Long): Flow<Place?> =
        placeDao.observeById(placeId)

    suspend fun getPlaceById(placeId: Long): Place? =
        placeDao.getPlaceById(placeId)

    suspend fun getPlacesByListId(listId: String?): List<Place> =
        placeDao.getPlacesByListId(listId)

    fun observePhotosByPlace(placeId: Long): Flow<List<PlacePhoto>> =
        placePhotoDao.observeByPlace(placeId)

    fun observeReviewsByPlace(placeId: Long): Flow<List<PlaceReview>> =
        placeReviewDao.observeByPlace(placeId)

    // ===== Import / sync luoghi da Google Maps (logica invariata) =====

    suspend fun saveImportedPlaces(places: List<Place>): Int {
        if (places.isEmpty()) return 0
        val inserted = placeDao.insertAll(places)
        return inserted.count { it != -1L }
    }

    suspend fun getSourceList(id: String): SourceList? = sourceListDao.getById(id)

    suspend fun syncListPlaces(
        listId: String,
        listName: String?,
        sourceUrl: String?,
        incoming: List<Place>
    ): SyncResult {
        val existing = placeDao.getPlacesByListId(listId)

        // 🛡️ PROTEZIONE: Non cancellare tutto se incoming è vuoto ma existing ha luoghi.
        // Questo previene la cancellazione accidentale dell'elenco quando il parser JS fallisce
        // o la pagina non si carica correttamente (restituendo una lista vuota).
        if (incoming.isEmpty() && existing.isNotEmpty()) {
            Log.w("TravelPins", "⚠️ syncListPlaces: incoming è vuoto ma existing ha ${existing.size} luoghi. Sync annullato per sicurezza.")
            return SyncResult(added = 0, removed = 0, updated = 0)
        }

        fun key(p: Place) = Triple(p.name, p.latitude, p.longitude)

        val existingByKey = existing.associateBy(::key)
        val incomingKeys = incoming.map(::key).toSet()

        // 1) Luoghi rimossi da Google -> elimina completamente
        //    (deletePlaceCompletely emette onPlaceDeleted per il tombstone Drive)
        val removedPlaces = existing.filter { key(it) !in incomingKeys }
        for (p in removedPlaces) deletePlaceCompletely(p)

        // 2) Luoghi ancora presenti -> sovrascrivi dati base e ri-arricchisci
        //    (note e categoryId NON vengono toccati: updateBaseInfo non li scrive)
        var updated = 0
        for (inc in incoming) {
            val ex = existingByKey[key(inc)]
            if (ex != null) {
                placeDao.updateBaseInfo(ex.id, inc.address, inc.mapsUrl, inc.placeId, inc.mapsPlaceRef)
                placeDao.clearDetailsFetched(ex.id)
                updated++
                emitPlaceUpserted(ex)
            }
        }

        // 3) Luoghi nuovi -> inserisci SOLO quelli che non esistono già
        val newPlaces = incoming.filter { key(it) !in existingByKey }
        val inserted = placeDao.insertAll(newPlaces)
        val added = inserted.count { it != -1L }
        newPlaces.forEach { emitPlaceUpserted(it) }

        // 4) Aggiorna intestazione lista
        val now = System.currentTimeMillis()
        val prev = sourceListDao.getById(listId)
        sourceListDao.upsert(
            SourceList(
                id = listId,
                name = listName ?: prev?.name,
                sourceUrl = sourceUrl ?: prev?.sourceUrl,
                coverUrl = prev?.coverUrl,
                createdAt = prev?.createdAt ?: now,
                updatedAt = now,
                placeCount = 0
            )
        )
        val total = placeDao.getPlacesByListId(listId).size
        sourceListDao.updateStats(listId, total, now)

        return SyncResult(added, removedPlaces.size, updated)
    }

    suspend fun deletePlaceCompletely(place: Place) {
        placePhotoDao.deleteByPlace(place.id)
        val reviews = placeReviewDao.observeByPlace(place.id).first()
        if (reviews.isNotEmpty()) placeReviewDao.deleteAll(reviews)
        placeDao.delete(place)
        // Notifica per il pending tombstone Drive (dati collaborativi da conservare)
        emitPlaceDeleted(place)
    }

    // ===== Categorie =====

    suspend fun createCategory(name: String, colorArgb: Int, iconKey: String): Long {
        val category = Category(
            uuid = UUID.randomUUID().toString(),
            name = name,
            colorArgb = colorArgb,
            iconKey = iconKey
        )
        val id = categoryDao.insert(category)
        emitCategoryCreated(category.copy(id = id))
        return id
    }

    /** Modifica di una categoria esistente (wrapper di CategoryDao.update). */
    suspend fun updateCategory(category: Category) {
        categoryDao.update(category)
        emitCategoryUpdated(category)
    }

    suspend fun assignPlaceToCategory(placeId: Long, categoryId: Long?) {
        placeDao.assignCategory(placeId, categoryId)
        placeDao.getPlaceById(placeId)?.let { emitPlaceCollabChanged(it) }
    }

    suspend fun deletePlace(place: Place) = placeDao.delete(place)

    suspend fun deleteCategory(category: Category) {
        // Cattura l'uuid PRIMA della cancellazione: dopo non sarebbe più recuperabile.
        val uuid = category.uuid
        val deletedAt = System.currentTimeMillis()
        categoryDao.delete(category)
        if (uuid.isNotBlank()) {
            emitCategoryDeleted(uuid, deletedAt)
        }
    }

    // ===== Dettagli / note / manutenzione =====

    suspend fun resetAllDetailsFetched() = placeDao.resetAllDetailsFetched()

    suspend fun clearDetailsFetched(placeId: Long) = placeDao.clearDetailsFetched(placeId)

    suspend fun clearAllPlaces() = placeDao.deleteAll()

    suspend fun insertPhotos(photos: List<PlacePhoto>): Int {
        if (photos.isEmpty()) return 0
        return placePhotoDao.insertAll(photos).count { it != -1L }
    }

    suspend fun insertReviews(reviews: List<PlaceReview>): Int {
        if (reviews.isEmpty()) return 0
        return placeReviewDao.insertAll(reviews).count { it != -1L }
    }

    suspend fun updatePlaceDetails(
        placeId: Long,
        rating: Double?,
        reviewCount: Int?,
        description: String?,
        websiteUrl: String?,
        types: String?,
        detailsFetchedAt: Long
    ) {
        placeDao.updateDetails(placeId, rating, reviewCount, description, websiteUrl, types, detailsFetchedAt)
    }

    suspend fun updateNote(placeId: Long, note: String?) {
        placeDao.updateNote(placeId, note)
        placeDao.getPlaceById(placeId)?.let { emitPlaceCollabChanged(it) }
    }

    fun setListCover(listId: String?, url: String) {
        if (listId.isNullOrBlank()) return
        prefs.edit().putString("list_cover_$listId", url).apply()
    }

    fun getListCover(listId: String?): String? {
        if (listId.isNullOrBlank()) return null
        return prefs.getString("list_cover_$listId", null)
    }

    suspend fun setPlaceCoverPhoto(placeId: Long, photoKey: String) {
        val photos = placePhotoDao.getByPlace(placeId)
        val chosen = photos.firstOrNull { it.photoKey == photoKey } ?: return
        val reordered = listOf(chosen) + photos.filter { it.photoKey != photoKey }
        placePhotoDao.deleteByPlace(placeId)
        placePhotoDao.insertAll(reordered.mapIndexed { index, photo -> photo.copy(position = index) })
    }
}
