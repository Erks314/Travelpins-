package com.travelpins.test.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class SyncResult(val added: Int, val removed: Int, val updated: Int)

class TravelPinsRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val placeDao = db.placeDao()
    private val categoryDao = db.categoryDao()
    private val placePhotoDao = db.placePhotoDao()
    private val placeReviewDao = db.placeReviewDao()
    private val sourceListDao = db.sourceListDao()
    private val prefs = context.applicationContext.getSharedPreferences("travelpins_prefs", Context.MODE_PRIVATE)

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

    suspend fun saveImportedPlaces(places: List<Place>): Int {
        if (places.isEmpty()) return 0
        val inserted = placeDao.insertAll(places)
        return inserted.count { it != -1L }
    }

    // ============================================================
    // SYNC / REFRESH ELENCO
    // ============================================================

    suspend fun getSourceList(id: String): SourceList? = sourceListDao.getById(id)

    suspend fun syncListPlaces(
        listId: String,
        listName: String?,
        sourceUrl: String?,
        incoming: List<Place>
    ): SyncResult {
        val existing = placeDao.getPlacesByListId(listId)
        fun key(p: Place) = Triple(p.name, p.latitude, p.longitude)

        val existingByKey = existing.associateBy(::key)
        val incomingKeys = incoming.map(::key).toSet()

        // 1) Luoghi rimossi da Google -> elimina completamente
        val removedPlaces = existing.filter { key(it) !in incomingKeys }
        for (p in removedPlaces) deletePlaceCompletely(p)

        // 2) Luoghi ancora presenti -> sovrascrivi dati base e ri-arricchisci
        var updated = 0
        for (inc in incoming) {
            val ex = existingByKey[key(inc)]
            if (ex != null) {
                placeDao.updateBaseInfo(ex.id, inc.address, inc.mapsUrl, inc.placeId, inc.mapsPlaceRef)
                placeDao.clearDetailsFetched(ex.id)
                updated++
            }
        }

        // 3) Luoghi nuovi -> inserisci
        val inserted = placeDao.insertAll(incoming)
        val added = inserted.count { it != -1L }

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
    }

    // ============================================================
    // CATEGORIE
    // ============================================================

    suspend fun createCategory(name: String, colorArgb: Int, iconKey: String): Long =
        categoryDao.insert(Category(name = name, colorArgb = colorArgb, iconKey = iconKey))

    suspend fun assignPlaceToCategory(placeId: Long, categoryId: Long?) =
        placeDao.assignCategory(placeId, categoryId)

    suspend fun deletePlace(place: Place) = placeDao.delete(place)

    suspend fun deleteCategory(category: Category) = categoryDao.delete(category)

    suspend fun resetAllDetailsFetched() = placeDao.resetAllDetailsFetched()

    suspend fun clearDetailsFetched(placeId: Long) = placeDao.clearDetailsFetched(placeId)

    suspend fun clearAllPlaces() = placeDao.deleteAll()

    // ============================================================
    // FOTO / RECENSIONI / DETTAGLI
    // ============================================================

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

    // ============================================================
    // NOTE
    // ============================================================

    suspend fun updateNote(placeId: Long, note: String?) =
        placeDao.updateNote(placeId, note)

    // ============================================================
    // COPERTINE
    // ============================================================

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
