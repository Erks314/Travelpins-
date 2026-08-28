package com.travelpins.test.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class TravelPinsRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val placeDao = db.placeDao()
    private val categoryDao = db.categoryDao()
    private val placePhotoDao = db.placePhotoDao()
    private val placeReviewDao = db.placeReviewDao()

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

    fun observePhotosByPlace(placeId: Long): Flow<List<PlacePhoto>> =
        placePhotoDao.observeByPlace(placeId)

    fun observeReviewsByPlace(placeId: Long): Flow<List<PlaceReview>> =
        placeReviewDao.observeByPlace(placeId)

    suspend fun saveImportedPlaces(places: List<Place>): Int {
        if (places.isEmpty()) return 0
        val inserted = placeDao.insertAll(places)
        return inserted.count { it != -1L }
    }

    suspend fun createCategory(name: String, colorArgb: Int, iconKey: String): Long =
        categoryDao.insert(Category(name = name, colorArgb = colorArgb, iconKey = iconKey))

    suspend fun assignPlaceToCategory(placeId: Long, categoryId: Long?) =
        placeDao.assignCategory(placeId, categoryId)

    suspend fun deletePlace(place: Place) = placeDao.delete(place)

    suspend fun deleteCategory(category: Category) = categoryDao.delete(category)

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
}
